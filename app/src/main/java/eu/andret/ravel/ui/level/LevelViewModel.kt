package eu.andret.ravel.ui.level

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import eu.andret.ravel.RavelApplication
import eu.andret.ravel.game.Hint
import eu.andret.ravel.game.Puzzle
import eu.andret.ravel.game.PuzzleId
import eu.andret.ravel.game.PuzzleMode
import eu.andret.ravel.game.Puzzles
import eu.andret.ravel.game.Rewards
import eu.andret.ravel.settings.LevelProgressState
import eu.andret.ravel.settings.SavedBoard
import eu.andret.ravel.ui.game.BoardState
import eu.andret.ravel.ui.game.SolveClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Holds one level's board across configuration changes, and passes the player's moves to [Puzzle].
 *
 * The daily challenge's twin, and deliberately not a subclass of it. What the two share is the part
 * that is really about boards rather than about modes - generation, the clock, the hint purchase -
 * and that lives in [Puzzles], [SolveClock] and [Rewards]. What is left here is the ladder: one
 * level at a time, in order, and a finish that moves the player on rather than filling in a calendar.
 *
 * Which level this is arrives as a navigation argument rather than being read from the store, for
 * the same reason the daily screen is told its date: the menu in front of this screen already
 * decided, and a second reading is a second chance to disagree.
 */
class LevelViewModel(
    application: Application,
    handle: SavedStateHandle,
) : AndroidViewModel(application) {
    private val language = (application as RavelApplication).language
    private val wallet = (application as RavelApplication).wallet
    private val progress = (application as RavelApplication).levels

    /** The level this board is, counting from one. */
    val level: Int = checkNotNull(handle.get<Int>(LevelRoute.ARG_LEVEL)) {
        "No level to open: ${LevelRoute.ARG_LEVEL} missing from the route"
    }

    private val _state = MutableStateFlow<BoardState>(BoardState.Generating)
    val state: StateFlow<BoardState> = _state.asStateFlow()

    /** What the player can spend, for the screen to show beside the prices. */
    val balance: StateFlow<Int> = wallet.balance

    /** Whether the board may buzz. Straight through from the store, since nothing here decides it. */
    val hapticsEnabled: StateFlow<Boolean> =
        (application as RavelApplication).haptics.enabled

    /**
     * The tally as it stood when the level was beaten, or null while it is still being played.
     *
     * Finishing a level moves the store on to the next one, and the next one has bought nothing -
     * so without this the line under a board the player is still looking at would drop to zero the
     * moment they solved it, reporting the level they are about to start rather than the one in
     * front of them.
     */
    private val _sealed = MutableStateFlow<LevelCost?>(null)

    /**
     * What this level has cost in hints, across every visit to it and not just this one.
     *
     * Read from the store rather than from the board, so backing out and coming back does not hand
     * the player a clean slate they did not earn. Zero for any level that is not the one being
     * played: the store keeps a cost for the current level only, and showing it against a level the
     * player is somehow looking at instead would be reporting someone else's spending.
     */
    val bought: StateFlow<LevelCost> = combine(progress.state, _sealed) { progress, sealed ->
        sealed ?: progress.cost()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = progress.state.value.cost(),
    )

    /**
     * True when this level is ahead of the ladder, which means the player cannot be on it.
     *
     * The only thing that can put them here is a language switch. Each language keeps its own ladder
     * - see [LevelProgressState] - so leaving the twenty-first Polish level open and switching to an
     * English ladder that has reached level one leaves this screen showing an English board under
     * the number 21, playable, and paying nothing: [eu.andret.ravel.settings.LevelProgress.complete]
     * refuses a level that is not the current one, which is correct and invisible.
     *
     * Ahead of the ladder rather than "not the current level", because finishing a level makes it
     * not the current one - and a celebration that threw the player back to the menu the moment they
     * earned it would be this check firing on the one screen it is not about.
     */
    val unreachable: StateFlow<Boolean> = progress.state
        .map { level > it.current }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _completion = MutableStateFlow<LevelCompletion?>(null)

    /**
     * What the last finish paid, until the player picks one of the two ways on.
     *
     * Held here rather than raised as a one-off event so a rotation mid-celebration does not lose
     * the celebration - and so the screen can be rebuilt from state alone, like everything else.
     */
    val completion: StateFlow<LevelCompletion?> = _completion.asStateFlow()

    /** The search currently running, kept only so a new one can call the old one off. */
    private var running: Job? = null

    private val clock = SolveClock(
        scope = viewModelScope,
        banked = progress.state.value.secondsFor(level),
        onBank = { seconds -> progress.recordTime(level, seconds) },
    )

    /** Time on this board, ticking, for the bar over it. */
    val elapsed: StateFlow<Int> = clock.elapsed

    private val id = PuzzleId(PuzzleMode.ENDLESS, level.toLong())

    init {
        // Collecting rather than calling once, because the first emission *is* the first generation
        // and every later one is a language the board has to be rebuilt from. A view model outlives
        // the activity's recreation, so without this the switch would leave the old language's grid
        // on screen under an interface that had already changed.
        viewModelScope.launch {
            language.current.collect { generate() }
        }
    }

    fun generate() {
        // A retry while a search is still running would otherwise leave the old one burning a core
        // for a board that is already unwanted. The id does not change with it: a retry has to
        // produce the same level's board, not a fresh one.
        running?.cancel()
        _state.value = BoardState.Generating
        // Read once, here, rather than inside the coroutine: the board that comes back has to be the
        // one the saved marks below were checked against, and a switch landing mid-search would
        // otherwise restore one language's progress onto the other's grid.
        val playing = language.current.value
        running = viewModelScope.launch {
            val next = try {
                Puzzles.generate(id, playing)
                    ?.let { puzzle ->
                        BoardState.Playing(restored(puzzle, progress.state.value.boardFor(level)))
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
     * nothing left to reveal cannot charge for it.
     *
     * @return false when the balance does not cover it, which the screen answers with a trip to the
     *   shop rather than a dead button.
     */
    fun hint(hint: Hint): Boolean {
        val playing = _state.value as? BoardState.Playing ?: return true
        if (playing.puzzle.isComplete) return true
        if (!wallet.spend(hint.cost)) return false

        val next = playing.puzzle.hinted(hint)
        // Written to the level's record before the level can be finished by the move that follows:
        // buying the last word both buys a hint and beats the level, and a purchase recorded
        // afterwards would land on the level after it.
        if (next.boughtOf(hint) > playing.puzzle.boughtOf(hint)) progress.recordHint(level, hint)
        if (next.isComplete) reward(next.board.solution.size)
        _state.value = BoardState.Playing(next)
        if (!next.isComplete) progress.saveBoard(saved(next))
        return true
    }

    /** The screen is on and showing this board. */
    fun resumeClock() = clock.onVisible(true)

    /** The screen has gone away. Banks whatever the clock counted on the way out. */
    fun pauseClock() = clock.onVisible(false)

    /** Puts the celebration away, leaving the player on the finished board. */
    fun dismissCompletion() {
        _completion.value = null
    }

    override fun onCleared() {
        // viewModelScope cancels this anyway; spelled out because the cancellation is the point.
        // No super call: ViewModel.onCleared is defined to be empty.
        running?.cancel()
    }

    /**
     * Tells the clock whether there is anything here to be timed.
     *
     * Called after generation as well as from the lifecycle, because the two can land in either
     * order: a search that finishes while the app is in the background has to leave the clock alone,
     * and one that finishes while it is in front has to start it without waiting for another
     * lifecycle event that is never coming.
     */
    private fun syncClock() {
        clock.onPlayable((_state.value as? BoardState.Playing)?.puzzle?.isComplete == false)
    }

    /** What the level being played has cost, out of a state that keeps one level's spending. */
    private fun LevelProgressState.cost() =
        LevelCost(boughtLettersFor(level), boughtWordsFor(level))

    /** Where the player has got to, in the least that has to be written down. */
    private fun saved(puzzle: Puzzle) = SavedBoard(
        ordinal = level.toLong(),
        language = language.current.value,
        found = puzzle.found,
        path = puzzle.path,
        hints = puzzle.hints,
        wordCount = puzzle.board.solution.size,
    )

    /**
     * Applies a move and pays for the level when that move was the one that finished it.
     *
     * Paid here rather than wherever a word happens to be completed, because there are four ways to
     * complete one - trace it, tap the last cell, buy a letter, buy the word - and a reward handed
     * out at each of them is a reward handed out twice by the fifth. The transition is what pays:
     * not complete before, complete after, once.
     */
    private inline fun play(move: (Puzzle) -> Puzzle) {
        val playing = _state.value as? BoardState.Playing ?: return
        val next = move(playing.puzzle)
        if (next.isComplete && !playing.puzzle.isComplete) reward(next.board.solution.size)
        _state.value = BoardState.Playing(next)
        // Written on every move rather than when the screen closes, because the screen is not
        // always told: a process killed in the background gets no chance to save on the way out.
        if (!next.isComplete) progress.saveBoard(saved(next))
    }

    /**
     * Pays for the level, moves the ladder on, and says what it all came to.
     *
     * Unlike the daily challenge, nothing is paid for a board finished twice. A day can legitimately
     * be opened again - the calendar is full of them - so the daily reward is for the board and the
     * streak is for the day. A level cannot: once it is behind the player there is no route back to
     * it, so a second finish is a board that outlived its level, not a level beaten.
     */
    private fun reward(wordCount: Int) {
        // Banked before the level is closed, or the last few seconds of the solve are lost with the
        // running clock - and the store stops accepting time for this level the moment it moves on.
        clock.stop()
        val seconds = progress.state.value.secondsFor(level)
        _sealed.value = progress.state.value.cost()
        val counted = progress.complete(level, wordCount)
        val coins = if (counted) Rewards.FOR_COMPLETING else 0
        if (coins > 0) wallet.earn(coins)

        _completion.value = LevelCompletion(level = level, coins = coins, seconds = seconds)
    }

    private companion object {
        /**
         * The freshly generated board with the player's own marks put back on it.
         *
         * Everything saved is an index into a board that was regenerated, not reloaded, and
         * [PuzzleId] is explicit that board identity is (id, generator version): ship a generator
         * change and the same seed lands on a different board, at which point a saved path points
         * at cells that have moved and a found word index may not exist at all.
         *
         * So the saved marks are checked against the board they are being put on, and anything that
         * does not fit is dropped rather than trusted. A player who loses a half-finished level to a
         * release has lost an evening; one whose app crashes on every open has lost the app.
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
