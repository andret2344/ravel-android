    package eu.andret.ravel.game

import eu.andret.ravel.generator.Board
import eu.andret.ravel.generator.Language
import eu.andret.ravel.generator.Placement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cache's bookkeeping, with the generator replaced by boards the test hands over by hand.
 *
 * The scope is [Dispatchers.Unconfined] so a [PuzzleCache.prefetch] has finished its bookkeeping by
 * the time it returns - in the game the scope is `Main.immediate`, which does the same for a caller
 * on the main thread. Each build waits on a [CompletableDeferred] the test completes, which is what
 * lets a test hold a board "still being built" for as long as it needs to.
 */
class PuzzleCacheTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    /** One pending build per (id, language), in the order the cache started them. */
    private val builds = LinkedHashMap<Pair<PuzzleId, Language>, CompletableDeferred<Puzzle?>>()

    /** Builds the cache called off, which is what eviction is supposed to do to them. */
    private val stopped = HashSet<Pair<PuzzleId, Language>>()

    /** The shelf as a map, so a test can see what was written down and put boards there itself. */
    private val held = HashMap<Pair<PuzzleId, Language>, Puzzle>()

    private val shelf = object : BoardShelf {
        override fun get(id: PuzzleId, language: Language) = held[id to language]
        override fun put(id: PuzzleId, language: Language, puzzle: Puzzle) {
            held[id to language] = puzzle
        }

        override fun remove(id: PuzzleId, language: Language) {
            held.remove(id to language)
        }

        override fun ordinals(mode: PuzzleMode, language: Language) =
            held.keys.filter { it.first.mode == mode && it.second == language }.map { it.first.ordinal }
    }

    private val cache = PuzzleCache(scope, shelf) { id, language ->
        try {
            CompletableDeferred<Puzzle?>().also { builds[id to language] = it }.await()
        } catch (cancellation: CancellationException) {
            stopped += id to language
            throw cancellation
        }
    }

    private val board = Board(
        width = 2,
        height = 1,
        letters = charArrayOf('O', 'K'),
        solution = listOf(Placement("ok", intArrayOf(0, 1))),
    )

    private fun level(n: Long) = PuzzleId(PuzzleMode.ENDLESS, n)

    private val today = PuzzleId(PuzzleMode.DAILY, 20_000)

    @Test
    fun `a puzzle nobody ordered is a miss and builds nothing`() = runBlocking {
        assertNull(cache.take(level(1), Language.POLISH))
        assertTrue(builds.isEmpty())
    }

    @Test
    fun `an ordered puzzle is handed over and ordering it again builds nothing`() = runBlocking {
        val puzzle = Puzzle(board)
        cache.prefetch(level(1), Language.POLISH)
        cache.prefetch(level(1), Language.POLISH)
        assertEquals(1, builds.size)

        builds.getValue(level(1) to Language.POLISH).complete(puzzle)
        assertSame(puzzle, cache.take(level(1), Language.POLISH))
    }

    @Test
    fun `levels are built one at a time, nearest first`() = runBlocking {
        cache.prefetch(level(2), Language.POLISH)
        cache.prefetch(level(3), Language.POLISH)
        assertEquals(listOf(level(2) to Language.POLISH), builds.keys.toList())

        builds.getValue(level(2) to Language.POLISH).complete(Puzzle(board))
        assertEquals(
            listOf(level(2) to Language.POLISH, level(3) to Language.POLISH),
            builds.keys.toList(),
        )
    }

    @Test
    fun `the daily does not queue behind levels`() = runBlocking {
        cache.prefetch(level(2), Language.POLISH)
        cache.prefetch(today, Language.POLISH)
        assertTrue((today to Language.POLISH) in builds)
    }

    @Test
    fun `the same puzzle in the other language is a miss`() = runBlocking {
        cache.prefetch(level(1), Language.POLISH)
        builds.getValue(level(1) to Language.POLISH).complete(Puzzle(board))
        assertNull(cache.take(level(1), Language.ENGLISH))
    }

    @Test
    fun `a fourth level evicts the oldest level and stops building it`() = runBlocking {
        cache.prefetch(level(1), Language.POLISH)
        cache.prefetch(level(2), Language.POLISH)
        cache.prefetch(level(3), Language.POLISH)
        cache.prefetch(level(4), Language.POLISH)

        assertTrue(level(1) to Language.POLISH in stopped)
        assertNull(cache.take(level(1), Language.POLISH))
        assertFalse(level(2) to Language.POLISH in stopped)
    }

    @Test
    fun `levels and the daily never evict each other`() = runBlocking {
        val daily = Puzzle(board)
        cache.prefetch(today, Language.POLISH)
        cache.prefetch(level(1), Language.POLISH)
        cache.prefetch(level(2), Language.POLISH)
        cache.prefetch(level(3), Language.POLISH)
        cache.prefetch(level(4), Language.POLISH)

        builds.getValue(today to Language.POLISH).complete(daily)
        assertSame(daily, cache.take(today, Language.POLISH))
    }

    @Test
    fun `ordering in another language drops everything held`() = runBlocking {
        cache.prefetch(level(1), Language.POLISH)
        cache.prefetch(today, Language.POLISH)
        cache.prefetch(level(2), Language.ENGLISH)

        assertTrue(level(1) to Language.POLISH in stopped)
        assertTrue(today to Language.POLISH in stopped)
        assertNull(cache.take(today, Language.POLISH))
    }

    @Test
    fun `a board evicted while being waited for is a miss, not a cancellation`() = runBlocking {
        cache.prefetch(level(1), Language.POLISH)
        val waiting = async(start = CoroutineStart.UNDISPATCHED) {
            cache.take(level(1), Language.POLISH)
        }
        cache.prefetch(level(2), Language.POLISH)
        cache.prefetch(level(3), Language.POLISH)
        cache.prefetch(level(4), Language.POLISH)

        assertNull(waiting.await())
    }

    @Test
    fun `a caller that leaves stops waiting and the build carries on`() = runBlocking {
        cache.prefetch(level(1), Language.POLISH)
        val waiting = async(start = CoroutineStart.UNDISPATCHED) {
            cache.take(level(1), Language.POLISH)
        }
        waiting.cancelAndJoin()

        assertTrue(waiting.isCancelled)
        val puzzle = Puzzle(board)
        builds.getValue(level(1) to Language.POLISH).complete(puzzle)
        assertSame(puzzle, cache.take(level(1), Language.POLISH))
    }

    @Test
    fun `a finished prefetch is written down and outlives the memory`() = runBlocking {
        val puzzle = Puzzle(board)
        cache.prefetch(level(1), Language.POLISH)
        builds.getValue(level(1) to Language.POLISH).complete(puzzle)

        val restarted = PuzzleCache(scope, shelf) { _, _ -> error("nothing should be built") }
        assertSame(puzzle, restarted.take(level(1), Language.POLISH))
    }

    @Test
    fun `a puzzle already on the shelf is not built again`() = runBlocking {
        cache.keep(level(1), Language.POLISH, Puzzle(board))
        cache.prefetch(level(1), Language.POLISH)
        assertTrue(builds.isEmpty())
    }

    @Test
    fun `a failed prefetch writes nothing down`() = runBlocking {
        cache.prefetch(level(1), Language.POLISH)
        builds.getValue(level(1) to Language.POLISH).complete(null)
        assertTrue(held.isEmpty())
    }

    @Test
    fun `a solved puzzle leaves the shelf`() = runBlocking {
        cache.keep(level(1), Language.POLISH, Puzzle(board))
        cache.solved(level(1), Language.POLISH)
        assertTrue(held.isEmpty())
    }

    @Test
    fun `the shelf keeps the three newest levels and one day per language`() {
        val yesterday = PuzzleId(PuzzleMode.DAILY, today.ordinal - 1)
        listOf(level(1), level(2), level(3), level(4), yesterday, today).forEach {
            cache.keep(it, Language.POLISH, Puzzle(board))
        }
        cache.keep(level(1), Language.ENGLISH, Puzzle(board))

        assertEquals(
            setOf(
                level(2) to Language.POLISH,
                level(3) to Language.POLISH,
                level(4) to Language.POLISH,
                today to Language.POLISH,
                level(1) to Language.ENGLISH,
            ),
            held.keys,
        )
    }

    @Test
    fun `obtain builds a miss and writes it down`() = runBlocking {
        val puzzle = Puzzle(board)
        val waiting = async(start = CoroutineStart.UNDISPATCHED) {
            cache.obtain(level(1), Language.POLISH)
        }
        builds.getValue(level(1) to Language.POLISH).complete(puzzle)

        assertSame(puzzle, waiting.await())
        assertSame(puzzle, held[level(1) to Language.POLISH])
    }

    @Test
    fun `obtain joins a build already running instead of starting another`() = runBlocking {
        cache.prefetch(level(1), Language.POLISH)
        val waiting = async(start = CoroutineStart.UNDISPATCHED) {
            cache.obtain(level(1), Language.POLISH)
        }
        assertEquals(1, builds.size)

        val puzzle = Puzzle(board)
        builds.getValue(level(1) to Language.POLISH).complete(puzzle)
        assertSame(puzzle, waiting.await())
    }

    @Test
    fun `a screen that stops waiting on obtain leaves the build running`() = runBlocking {
        val waiting = async(start = CoroutineStart.UNDISPATCHED) {
            cache.obtain(level(1), Language.POLISH)
        }
        waiting.cancelAndJoin()

        assertFalse((level(1) to Language.POLISH) in stopped)
    }

    @Test
    fun `asking again after a failed build starts a new one`() = runBlocking {
        val first = async(start = CoroutineStart.UNDISPATCHED) {
            cache.obtain(level(1), Language.POLISH)
        }
        builds.getValue(level(1) to Language.POLISH).complete(null)
        assertNull(first.await())

        val second = async(start = CoroutineStart.UNDISPATCHED) {
            cache.obtain(level(1), Language.POLISH)
        }
        val puzzle = Puzzle(board)
        builds.getValue(level(1) to Language.POLISH).complete(puzzle)
        assertSame(puzzle, second.await())
    }
}
