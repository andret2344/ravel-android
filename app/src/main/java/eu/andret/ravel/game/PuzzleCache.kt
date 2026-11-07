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
 * ## Why boards are also written down
 *
 * Memory alone loses a board the moment Android kills the process in the background, which it does
 * routinely. The player comes back to the level they were on, nothing is held, and the screen
 * rebuilds it on [Puzzles.DEFAULT_TIMEOUT_MILLIS] - which, for the levels this class exists for,
 * fails the same way on every retry. So every board that has been built once, by a prefetch or by
 * a screen ([keep]), goes onto the [BoardShelf] and stays there until it is solved ([solved]).
 *
 * This also pins the board. After an update that changes the generator, a board on the shelf is the
 * one the player started, not the one the new generator would build - which is what a half-finished
 * board's saved marks were made against. Boards not on the shelf reissue as they always did.
 *
 * ## Why the level screen builds through here too
 *
 * A level reached with nothing built - a prefetch killed before it finished, a restore from a
 * backup older than the board - used to be built by the screen on a 20-second deadline, and for the
 * slow levels that failed the same way on every retry. [obtain] builds it here instead, on
 * [Puzzles.PREFETCH_TIMEOUT_MILLIS], so a player who leaves the screen does not throw the search
 * away: it carries on while the app is in front, and the board is waiting on the next visit. The
 * screen tells the player so once the wait gets long - see `LevelViewModel.slow`.
 *
 * Whoever awaits a board here awaits a job it does not own: cancelling the awaiting coroutine raises
 * in the awaiter and leaves the search running, which is the point.
 *
 * The daily screen still builds a miss itself ([take], then `Puzzles.generate`): its only misses are
 * days opened for review, which must not take today's slot, and 8x8 boards that fit the deadline.
 *
 * ## Why the slots are counted per mode
 *
 * Endless gets three: the board being played and the [LOOKAHEAD] boards after it, which is the
 * whole working set. One fewer and the level being played is evicted by its own successors, so a
 * player who backs out to the menu and re-enters it is sent straight back into the wait it was
 * rescued from.
 *
 * The lookahead is two levels rather than one because building in the background only really
 * happens while the app is on screen: Android freezes a cached process within seconds of it leaving
 * the foreground, and kills it some time later. One level of a fast player's foreground time is not
 * always the minute a slow 10x10 needs; two levels is. A process killed before a build finished
 * loses it, but the player comes back to the level they were on - it is on the shelf - and both
 * builds start again behind it.
 *
 * The daily gets one, today's, ordered by the daily menu. Counted apart from endless because a
 * shared pool of three would be first-in-first-out across both: two level prefetches in a row would
 * push today's board out, and a daily prefetch would push out the level being played. Each mode
 * evicts only its own.
 *
 * ## Why there is no `forget`
 *
 * A language switch already clears everything (see [prefetch]), and that is the only moment the
 * entries become unwanted. Dropping them from `onTrimMemory` beside the dictionary was considered
 * and rejected: `TRIM_MEMORY_UI_HIDDEN` arrives every time the player leaves the app, often with a
 * build still running that the freezer will merely pause, and a finished board is a few kilobytes.
 *
 * @param build how a prefetch builds a board. A parameter only so the tests can hand it boards
 *   they control; the game never passes one.
 */
class PuzzleCache(
    private val scope: CoroutineScope,
    private val shelf: BoardShelf,
    private val build: suspend (PuzzleId, Language) -> Puzzle? = { id, language ->
        Puzzles.generate(id, language, Puzzles.PREFETCH_TIMEOUT_MILLIS)
    },
) {
    private class Entry(
        val id: PuzzleId,
        val language: Language,
        val board: Deferred<Puzzle?>,
    )

    private val guard = Mutex()

    /**
     * One build at a time per mode, in the order they were ordered - [Mutex] is fair, so *n + 1*
     * finishes before *n + 2* starts.
     *
     * One at a time because two at once finish both later than one after the other: a phone that is
     * already warm from the player's own board slows every core it has, and *n + 1* is the board
     * that is needed first. Per mode, because a daily queued behind two slow levels would keep the
     * player waiting on the daily screen for boards they did not open.
     */
    private val building = PuzzleMode.entries.associateWith { Mutex() }

    /** Oldest first, so eviction is from the front. Never more per mode than [capacityOf]. */
    private val entries = ArrayList<Entry>()

    /**
     * The board for [id] in [language] if one was built or is being built, waiting for it in the
     * second case, or null when nothing here is about that puzzle - "generate it yourself", not
     * "there is no board".
     */
    suspend fun take(id: PuzzleId, language: Language): Puzzle? {
        shelf.get(id, language)?.let { return it }
        val pending = guard.withLock {
            entries.firstOrNull { it.id == id && it.language == language }?.board
        } ?: return null
        return awaitOrNull(pending)
    }

    /**
     * The board for [id] in [language], built here if nobody has started it, or null when the build
     * ran out of [Puzzles.PREFETCH_TIMEOUT_MILLIS]. Asking again after a null starts a new build.
     */
    suspend fun obtain(id: PuzzleId, language: Language): Puzzle? {
        shelf.get(id, language)?.let { return it }
        val pending = guard.withLock { order(id, language) } ?: return shelf.get(id, language)
        return awaitOrNull(pending)
    }

    private suspend fun awaitOrNull(pending: Deferred<Puzzle?>): Puzzle? =
        try {
            pending.await()
        } catch (cancellation: CancellationException) {
            // Two very different events arrive as the same exception here, and only one of them is
            // about this caller. The entry is awaited outside the lock, so between finding it and
            // waiting for it an eviction or a language switch may have cancelled it - that is this
            // cache dropping a board, not the screen going away, and the honest answer is null:
            // for [take] "generate it yourself", for [obtain] a failure the player can retry.
            //
            // ensureActive is what separates them. If the caller's own job is the cancelled one it
            // rethrows, because a view model that has been told to stop must stop - swallowing that
            // would leave the screen on its spinner with nothing left to finish it.
            currentCoroutineContext().ensureActive()
            null
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
        // please". The lock is what makes that safe.
        scope.launch { guard.withLock { order(id, language) } }
    }

    /**
     * The build for [id] in [language], started if there is none, or null when the board is already
     * on the shelf. Call under [guard].
     *
     * A build that finished without a board is dropped and started again rather than answered with
     * its null. The search is deterministic, but its deadline is wall-clock time, and a process the
     * system froze in the background spends its deadline asleep - a second attempt in the foreground
     * is not the same attempt.
     */
    private fun order(id: PuzzleId, language: Language): Deferred<Puzzle?>? {
        if (entries.any { it.language != language }) cancelAll()
        if (id.ordinal in shelf.ordinals(id.mode, language)) return null
        val existing = entries.firstOrNull { it.id == id && it.language == language }
        if (existing != null && !existing.board.isCompleted) return existing.board
        existing?.let(entries::remove)
        val sameMode = entries.filter { it.id.mode == id.mode }
        if (sameMode.size >= capacityOf(id.mode)) {
            entries.remove(sameMode.first())
            sameMode.first().board.cancel()
        }
        // Started on the cache's own scope and not the caller's, so the search outlives whoever
        // asked for it.
        val board = scope.async {
            building.getValue(id.mode).withLock {
                build(id, language)?.also { keep(id, language, it) }
            }
        }
        entries.add(Entry(id = id, language = language, board = board))
        return board
    }

    /**
     * Writes down a board a screen built itself, so it survives the process the way a prefetched one
     * does. Evicts on the same per-mode count as memory, oldest ordinal first: for endless that is
     * the level already behind the player, for the daily a day that can no longer be played.
     */
    fun keep(id: PuzzleId, language: Language, puzzle: Puzzle) {
        shelf.put(id, language, puzzle)
        shelf.ordinals(id.mode, language)
            .filter { it != id.ordinal }
            .sorted()
            .dropLast(capacityOf(id.mode) - 1)
            .forEach { shelf.remove(PuzzleId(id.mode, it), language) }
    }

    /** The player finished [id]: nothing will open it to play again, so it leaves the shelf. */
    fun solved(id: PuzzleId, language: Language) = shelf.remove(id, language)

    private fun cancelAll() {
        entries.forEach { it.board.cancel() }
        entries.clear()
    }

    companion object {
        /**
         * How many levels past the one being played are built ahead. See the class comment for
         * why two.
         */
        const val LOOKAHEAD = 2

        /**
         * See the class comment. Exhaustive on purpose, like [PuzzleSpec]'s: a third mode has to
         * decide how many of its boards are worth holding.
         */
        private fun capacityOf(mode: PuzzleMode): Int = when (mode) {
            PuzzleMode.DAILY -> 1
            PuzzleMode.ENDLESS -> 1 + LOOKAHEAD
        }
    }
}

/**
 * Where built boards outlive the process. An interface so [PuzzleCache]'s tests can keep one in a
 * map; the game's is [eu.andret.ravel.settings.StoredBoards].
 */
interface BoardShelf {
    fun get(id: PuzzleId, language: Language): Puzzle?
    fun put(id: PuzzleId, language: Language, puzzle: Puzzle)
    fun remove(id: PuzzleId, language: Language)

    /** Every ordinal held for [mode] in [language], in no particular order. */
    fun ordinals(mode: PuzzleMode, language: Language): List<Long>
}
