package eu.andret.ravel.ui.daily

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import eu.andret.ravel.RavelApplication
import eu.andret.ravel.game.DailyDate
import eu.andret.ravel.game.Hint
import eu.andret.ravel.game.Puzzle
import eu.andret.ravel.game.PuzzleId
import eu.andret.ravel.game.PuzzleMode
import eu.andret.ravel.game.Puzzles
import eu.andret.ravel.game.Rewards
import eu.andret.ravel.game.Streak
import eu.andret.ravel.game.wordResults
import eu.andret.ravel.settings.DayResult
import eu.andret.ravel.settings.SavedBoard
import eu.andret.ravel.settings.ShareCard
import eu.andret.ravel.ui.game.BoardState
import eu.andret.ravel.ui.game.SolveClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Holds one day's board across configuration changes, and passes the player's moves to [Puzzle].
 *
 * The rules live in [Puzzle] and the board's identity in [PuzzleId]; this class is about the board
 * arriving and surviving. Generation is a search with random restarts and a long tail - seconds,
 * not milliseconds, on larger grids - and it loads a dictionary of up to 25 000 words the first
 * time it runs. Both belong off the main thread, and the result belongs somewhere a screen rotation
 * cannot throw it away, which is exactly what a view model is.
 *
 * Which day it is arrives as a navigation argument rather than being read from the clock here. The
 * menu in front of this screen already decided - it might be today, or it might be a day out of the
 * calendar - and a second reading of the clock is a second chance to disagree about what day it is.
 */
class DailyViewModel(
    application: Application,
    handle: SavedStateHandle,
) : AndroidViewModel(application) {
    private val language = (application as RavelApplication).language
    private val wallet = (application as RavelApplication).wallet
    private val progress = (application as RavelApplication).progress
    private val puzzles = (application as RavelApplication).puzzles

    /** The day this board belongs to, as a [DailyDate] ordinal. */
    val ordinal: Long = checkNotNull(handle.get<Long>(DailyRoute.ARG_ORDINAL)) {
        "No day to open: ${DailyRoute.ARG_ORDINAL} missing from the route"
    }

    /**
     * True when the board is being looked at rather than played: it opens finished, nothing can be
     * traced onto it, and nothing about it can be bought or earned again.
     *
     * The board itself is rebuilt from its date rather than loaded, because [PuzzleId] makes that
     * the same board every time - so reviewing a day costs a generator run and no storage at all.
     * What it cannot show is the route the player took through it, only the answer they arrived at.
     */
    val review: Boolean = handle.get<Boolean>(DailyRoute.ARG_REVIEW) == true

    private val _state = MutableStateFlow<BoardState>(BoardState.Generating)
    val state: StateFlow<BoardState> = _state.asStateFlow()

    /** What the player can spend, for the screen to show beside the prices. */
    val balance: StateFlow<Int> = wallet.balance

    /** Whether the board may buzz. Straight through from the store, since nothing here decides it. */
    val hapticsEnabled: StateFlow<Boolean> =
        (application as RavelApplication).haptics.enabled

    /**
     * What this day has cost in hints, across every visit to it and not just this one.
     *
     * Read from the day's record rather than from the board, so backing out and coming back does
     * not hand the player a clean slate they did not earn.
     */
    val bought: StateFlow<DayResult> = progress.state
        .map { it.costOf(ordinal) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, progress.state.value.costOf(ordinal))

    private val _completion = MutableStateFlow<Completion?>(null)

    /**
     * What the last finish paid, until the player dismisses it.
     *
     * Held here rather than raised as a one-off event so a rotation mid-celebration does not lose
     * the celebration - and so the screen can be rebuilt from state alone, like everything else on
     * it.
     */
    val completion: StateFlow<Completion?> = _completion.asStateFlow()

    /** The search currently running, kept only so a new one can call the old one off. */
    private var running: Job? = null

    /**
     * Time on this board, ticking, for the bar over it.
     *
     * Starts from what the day has already cost across every earlier sitting, and banks what it
     * counts into the same record, so the number over the board is the day's total and not this
     * visit's.
     */
    private val clock = SolveClock(
        scope = viewModelScope,
        banked = progress.state.value.costOf(ordinal).seconds ?: 0,
        onBank = { seconds -> progress.recordTime(ordinal, seconds) },
    )

    val elapsed: StateFlow<Int> = clock.elapsed

    /** Local midnight on [ordinal], for the screen to format in the phone's own date style. */
    val date: Long = DailyDate.startOfDay(ordinal)

    private val id = PuzzleId(PuzzleMode.DAILY, ordinal)

    init {
        // Collecting rather than calling once: the first emission is the first generation, and any
        // later one is a language the grid has to be rebuilt from. See [LevelViewModel] for why a
        // view model has to handle this itself rather than leave it to the activity being recreated.
        viewModelScope.launch {
            language.current.collect { generate() }
        }
    }

    fun generate() {
        // A retry while a search is still running would otherwise leave the old one burning a core
        // for a board that is already unwanted. The id does not change with it: a retry has to
        // produce the same day's board, not a fresh one.
        running?.cancel()
        _state.value = BoardState.Generating
        // Read once, here, rather than inside the coroutine: the grid that comes back has to be the
        // one the saved marks below were checked against.
        val playing = language.current.value
        running = viewModelScope.launch {
            val next = try {
                // Today's board was ordered by the menu in front of this screen; any other day
                // misses and is built here as it always was. See [eu.andret.ravel.game.PuzzleCache].
                // Written down only when it is going to be played: a review's board would take the
                // daily's one slot from today's.
                (puzzles.take(id, playing)
                    ?: Puzzles.generate(id, playing)?.also {
                        if (!review) puzzles.keep(id, playing, it)
                    })
                    ?.let { puzzle ->
                        BoardState.Playing(
                            when {
                                review -> solved(puzzle)
                                else -> restored(puzzle, progress.state.value.boardFor(ordinal))
                            },
                        )
                    }
                    ?: BoardState.Failed(null)
            } catch (cancellation: CancellationException) {
                // Someone else is deciding what happens next - leave the state alone.
                throw cancellation
            } catch (failure: Exception) {
                BoardState.Failed(failure.message)
            }
            _state.value = next
            // There is a board now, or there is not. Either way the clock's answer just changed.
            syncClock()
        }
    }

    /** See [Puzzle.withPath]. */
    fun trace(cells: List<Int>) = play { it.withPath(cells) }

    /** See [Puzzle.tapped]. */
    fun tap(cell: Int) = play { it.tapped(cell) }

    /**
     * Buys [hint] and applies it, or reports that the player cannot afford it.
     *
     * The coins are taken first and only then is the move made, so a hint that turns out to have
     * nothing left to reveal - every word already found - cannot charge for it: [play] does
     * nothing in that state, and neither does this, because the board is complete and the button
     * that calls this is disabled.
     *
     * @return false when the balance does not cover it, which the screen answers with a trip to
     *   the shop rather than a dead button: a player who wants a hint and has no coins is the one
     *   person in the game with a reason to be there.
     */
    fun hint(hint: Hint): Boolean {
        if (review) return true
        val playing = _state.value as? BoardState.Playing ?: return true
        // Charging for a hint that has nothing left to reveal is the one way this could take coins
        // for nothing, so it is refused here rather than relied on being refused by a disabled
        // button.
        if (playing.puzzle.isComplete) return true
        if (!wallet.spend(hint.cost)) return false

        val next = playing.puzzle.hinted(hint)
        // Written to the day's record before the day can be sealed by the move that follows: buying
        // the last word both buys a hint and finishes the board, and a purchase recorded afterwards
        // would land on a day that had already stopped listening.
        if (next.boughtOf(hint) > playing.puzzle.boughtOf(hint)) progress.recordHint(ordinal, hint)
        if (next.isComplete) reward(next)
        _state.value = BoardState.Playing(next)
        if (!next.isComplete) progress.saveBoard(saved(next))
        return true
    }

    /** The screen is on and showing this board. Half of what the clock needs; see [SolveClock]. */
    fun resumeClock() = clock.onVisible(true)

    /** The screen has gone away. Banks whatever the clock counted on the way out. */
    fun pauseClock() = clock.onVisible(false)

    /**
     * Tells the clock whether there is anything here to be timed.
     *
     * Called after generation rather than only from the lifecycle, because the two can land in
     * either order: a search that finishes while the app is in the background has to leave the clock
     * alone, and one that finishes while it is in front has to start it without waiting for another
     * lifecycle event that is never coming.
     *
     * A board opened for review is never playable - it is history, not a solve - and neither is a
     * failure message with a retry button under it.
     */
    private fun syncClock() {
        clock.onPlayable(
            !review && (_state.value as? BoardState.Playing)?.puzzle?.isComplete == false,
        )
    }

    /** Puts the celebration away, leaving the player on the finished board. */
    fun dismissCompletion() {
        _completion.value = null
    }

    override fun onCleared() {
        // viewModelScope cancels this anyway; spelled out because the cancellation is the point.
        // No super call: ViewModel.onCleared is defined to be empty.
        running?.cancel()
    }

    /** Where the player has got to, in the least that has to be written down. */
    private fun saved(puzzle: Puzzle) = SavedBoard(
        ordinal = ordinal,
        language = language.current.value,
        found = puzzle.found,
        path = puzzle.path,
        hints = puzzle.hints,
        wordCount = puzzle.board.solution.size,
    )

    /**
     * Applies a move and pays for the board when that move was the one that finished it.
     *
     * Paid here rather than wherever a word happens to be completed, because there are four ways to
     * complete one - trace it, tap the last cell, buy a letter, buy the word - and a reward handed
     * out at each of them is a reward handed out twice by the fifth. The transition is what pays:
     * not complete before, complete after, once.
     *
     * A board opened for review is complete from its first frame, so it never makes that transition
     * and never pays - but the moves are refused outright anyway, because a finished board that
     * still accepted a finger would invite the player to think they were doing something.
     */
    private inline fun play(move: (Puzzle) -> Puzzle) {
        if (review) return
        val playing = _state.value as? BoardState.Playing ?: return
        val next = move(playing.puzzle)
        if (next.isComplete && !playing.puzzle.isComplete) reward(next)
        _state.value = BoardState.Playing(next)
        // Written on every move rather than when the screen closes, because the screen is not
        // always told: a process killed in the background gets no chance to save on the way out.
        if (!next.isComplete) progress.saveBoard(saved(next))
    }

    /**
     * Pays for the board, seals the day's record, pays for the day, and says what it all came to.
     *
     * The streak only ever pays for today. A board out of the calendar is history being reread, and
     * paying for it would turn a day the player missed into a day they can buy back - which is the
     * one thing that would make the number beside the calendar worth nothing.
     *
     * The amounts are published as they are paid. A screen asking afterwards would be asking a
     * balance that has already changed and a streak that has already grown, and would have to work
     * backwards from both to say what just happened.
     */
    private fun reward(puzzle: Puzzle) {
        // Banked before the day is sealed, or the last few seconds of the solve are lost with the
        // running clock. Stopped directly rather than through syncClock, because the board is over
        // and the screen has not gone anywhere - the state that would tell syncClock so is not
        // written until this returns.
        clock.stop()
        wallet.earn(Rewards.FOR_COMPLETING)

        // Taken off the finished board rather than assembled from what this class happens to know:
        // the per-word results are only derivable while the puzzle is in hand - see
        // eu.andret.ravel.game.wordResults - and the word count is one of them per word, so reading
        // it separately would be reading the same fact twice.
        val card = card(puzzle)
        // No check that this is still today's date. The board carries its own day, and the only
        // board that can be played is the one that was today when it was opened - every other route
        // in opens for review, which never reaches this method. A player who crosses midnight
        // mid-solve is paid for the day they were solving.
        val counted = progress.complete(card)
        puzzles.solved(id, card.language)
        val streak = progress.state.value.streak
        val forStreak = if (counted) Streak.rewardFor(Streak.dayInCycle(streak)) else 0
        if (forStreak > 0) wallet.earn(forStreak)

        _completion.value = Completion(
            forBoard = Rewards.FOR_COMPLETING,
            forStreak = forStreak,
            cycleDay = Streak.dayInCycle(streak),
            streak = streak,
            seconds = progress.state.value.costOf(ordinal).seconds ?: 0,
            // The card the dialog offers to share is the one that was just written down, not the
            // one read back afterwards. They are the same object, and asking the store for it would
            // make the dialog depend on a write having landed to show a button about it.
            card = card,
        )
    }

    /**
     * What the finished board looked like, for the share button.
     *
     * The size comes off the grid rather than out of [eu.andret.ravel.game.PuzzleSpec], though both
     * answer with the same number: this is a record of the board the player actually had in front
     * of them, and a board's width is a fact about the grid and not about the arithmetic that asked
     * for one. See [eu.andret.ravel.settings.ShareCard] for why it is written down at all.
     */
    private fun card(puzzle: Puzzle) = ShareCard(
        ordinal = ordinal,
        language = language.current.value,
        size = puzzle.board.width,
        words = puzzle.wordResults(),
    )

    private companion object {
        /** The same board with every word found, which is what a day already solved looks like. */
        fun solved(puzzle: Puzzle): Puzzle =
            puzzle.copy(found = puzzle.board.solution.indices.toSet())

        /**
         * The freshly generated board with the player's own marks put back on it.
         *
         * Everything saved is an index into a board that was regenerated, not reloaded, and
         * [PuzzleId] is explicit that board identity is (id, generator version): ship a generator
         * change and the same seed lands on a different board, at which point a saved path points
         * at cells that have moved and a found word index may not exist at all.
         *
         * So the saved marks are checked against the board they are being put on, and anything that
         * does not fit is dropped rather than trusted. A player who loses a half-finished board to
         * a release has lost an evening; one whose app crashes on every open has lost the app.
         */
        fun restored(puzzle: Puzzle, saved: SavedBoard?): Puzzle {
            if (saved == null) return puzzle
            val words = puzzle.board.solution.indices
            val cells = 0 until puzzle.board.width * puzzle.board.height
            return puzzle.copy(
                found = saved.found.filterTo(HashSet()) { it in words },
                path = saved.path.takeIf { path -> path.all { it in cells } }.orEmpty(),
                hints = saved.hints.filterKeys { it in words },
            )
        }
    }
}
