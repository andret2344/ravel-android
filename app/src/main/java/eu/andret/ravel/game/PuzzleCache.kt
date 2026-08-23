package eu.andret.ravel.game

import eu.andret.ravel.generator.Language
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Boards built before anybody asks for them, so the wait happens where the player is not looking.
 *
 * ## Why this exists
 *
 * Generation is far more expensive on a phone than the code around it assumed. Measured on a Redmi
 * Note 10 5G against a desktop JVM, the same search runs about 19 times slower: the median 10x10
 * takes 7.7 seconds, 58% of 10x10 boards take over five, and **7 of the 24 10x10 levels below 300
 * cannot be built inside [Puzzles.DEFAULT_TIMEOUT_MILLIS] at all**. Those seven are not unlucky.
 * The seed fixes the search, so the same level fails the same way on the same device every time,
 * and the "try again" the board screen offers is an invitation to watch it fail again. The full
 * numbers are in `docs/DECISIONS.md`.
 *
 * The cure that does not change what a board *is* is to start earlier. A player spends minutes on
 * one board, and the next one needs seconds - so the next one is built while they are busy, with
 * [Puzzles.PREFETCH_TIMEOUT_MILLIS] instead of a player's patience, and by the time they arrive it
 * is already sitting here. Board identity is untouched, which matters more than it sounds: changing
 * the size ramp or the shape dials to make these levels cheaper would reissue puzzles, and this
 * fixes the same seven levels without reissuing anything.
 *
 * This hides the cost rather than removing it. What still pays full price is the first board of a
 * fresh install and any level reached with nothing prefetched. Removing the cost means the layout
 * search itself - 98.6% of generation time, with 61% of its attempts on a 10x10 thrown away - and
 * that is a separate piece of work.
 *
 * ## Why [take] does not generate
 *
 * Generation must stop when a player leaves a screen, and it does because the view model runs it in
 * its own scope. That rule is kept exactly as it was: a miss here answers null and the caller
 * generates the way it always has, cancellably, on its own terms.
 *
 * What lives in this scope is only ever a prefetch - work whose result is *kept*, so leaving the
 * screen does not waste it. A [take] that joins one is awaiting a job it does not own: cancelling
 * the awaiting coroutine raises in the awaiter and leaves the search running, which is the point.
 * The board finishes, stays here, and is waiting on the next visit.
 *
 * ## Why two slots
 *
 * One is not enough by exactly one board. Finishing a level prefetches the next, and a player who
 * then backs out to the menu and re-enters the level they are *on* would find it evicted by its own
 * successor - sending the level that was rescued straight back into the twenty-second wait it was
 * rescued from. Two holds the board being played and the one coming, which is the whole working set;
 * a third would be a board no navigation reaches.
 */
class PuzzleCache(private val scope: CoroutineScope) {
    private class Entry(
        val id: PuzzleId,
        val language: Language,
        val board: Deferred<Puzzle?>,
    )

    private val guard = Mutex()

    /** Oldest first, so eviction is from the front. Never longer than [CAPACITY]. */
    private val entries = ArrayList<Entry>(CAPACITY)

    /**
     * The board for [id] in [language] if one was prefetched, waiting for it if it is still being
     * built, or null when nothing here is about that puzzle.
     *
     * Null means "generate it yourself" and not "there is no board" - the caller's own path is the
     * one that reports failure to the player, and it is the only one that can be cancelled.
     *
     * A prefetch that ran out of its own deadline is cached as its null answer rather than being
     * retried here. Retrying would be the same deterministic search a second time, and the caller
     * asking again is the caller deciding to spend a player's wait on it.
     */
    suspend fun take(id: PuzzleId, language: Language): Puzzle? {
        val pending = guard.withLock {
            entries.firstOrNull { it.id == id && it.language == language }?.board
        } ?: return null
        return try {
            pending.await()
        } catch (cancellation: CancellationException) {
            // Two very different events arrive as the same exception here, and only one of them is
            // about this caller. The entry is awaited outside the lock, so between finding it and
            // waiting for it another prefetch may have evicted and cancelled it - that is this
            // cache dropping a board, not the screen going away, and the honest answer is the same
            // null a miss gives: generate it yourself.
            //
            // ensureActive is what separates them. If the caller's own job is the cancelled one it
            // rethrows, because a view model that has been told to stop must stop - swallowing that
            // would leave the screen on its spinner with nothing left to finish it.
            currentCoroutineContext().ensureActive()
            null
        }
    }

    /**
     * Starts building [id] in [language] if it is not here already.
     *
     * Cheap to call again for a puzzle already held or already being built: the second call finds
     * the entry and does nothing, so a screen may say this on every board without counting.
     *
     * A language other than the entries' clears them all, in-flight ones included. Each language has
     * its own dictionary and its own boards, so nothing already here can be wanted again, and a
     * search still running for the language the player just left is a core spent on a board that has
     * no screen to appear on.
     */
    fun prefetch(id: PuzzleId, language: Language) {
        // Launched rather than suspending, because the caller is a view model reacting to a board
        // appearing on screen and should not have to be inside a coroutine to say "the next one,
        // please". The lock is what makes that safe, and the work below never suspends on anything
        // except the lock itself.
        scope.launch {
            guard.withLock {
                if (entries.any { it.language != language }) cancelAll()
                if (entries.any { it.id == id && it.language == language }) return@withLock
                if (entries.size >= CAPACITY) entries.removeAt(0).board.cancel()
                entries.add(
                    Entry(
                        id = id,
                        language = language,
                        // Started on the cache's own scope and not on this launch's, so the search
                        // outlives the bookkeeping that started it. A child of the launch would be
                        // held open by a coroutine whose only job was to add a list entry.
                        board = scope.async {
                            Puzzles.generate(id, language, Puzzles.PREFETCH_TIMEOUT_MILLIS)
                        },
                    )
                )
            }
        }
    }

    /** Drops everything and stops whatever is being built. */
    fun forget() {
        scope.launch { guard.withLock { cancelAll() } }
    }

    private fun cancelAll() {
        entries.forEach { it.board.cancel() }
        entries.clear()
    }

    private companion object {
        /** The board in front of the player and the one after it. See the class comment. */
        const val CAPACITY = 2
    }
}
