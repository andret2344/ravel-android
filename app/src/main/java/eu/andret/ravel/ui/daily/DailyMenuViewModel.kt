package eu.andret.ravel.ui.daily

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import eu.andret.ravel.RavelApplication
import eu.andret.ravel.game.DailyDate
import eu.andret.ravel.game.PuzzleId
import eu.andret.ravel.game.PuzzleMode
import eu.andret.ravel.settings.DailyProgressState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * What the menu in front of the daily challenge knows: the run so far, the month being looked at,
 * and which day the play button opens.
 *
 * Holds no puzzle, but orders today's from [eu.andret.ravel.game.PuzzleCache] while the player is
 * still looking at the calendar. This used to start nothing, on the argument that the board was "a
 * second or two of work" and not worth spending on a player who only came to check their streak.
 * On a phone it is several seconds, and the cache keeps what it builds, so the only waste left is
 * a board for a player who never presses play - against a spinner for every one who does.
 *
 * Not ordered for a day already solved: its board is only ever opened for review, and a review is
 * not worth a core on the chance somebody wants one.
 *
 * ### Today follows the clock
 *
 * [today] used to be read once, when the menu was built, on the argument that a calendar moving its
 * "today" ring under a player at midnight would be stranger than one a minute stale. It was not a
 * minute. This view model lives as long as the daily tab's back stack, which is as long as the
 * process, and a process outlives a night on the home screen as a matter of course: the morning
 * after solving a daily, the menu still called yesterday today, showed it solved, and would not
 * offer the new board until the app was killed. Had yesterday been left unsolved, Play would have
 * opened yesterday's board as a live one, and solving it would have paid a streak for a missed day.
 *
 * So the screen runs [followToday] for as long as it is started: the date is read again every time
 * the menu comes back into view, and again at each midnight while it stays in view. The ring now
 * moves under a player who watches midnight pass. That is accepted: the ring is then right, where
 * the old one was wrong for hours. The board screen is unaffected - it carries its own day, see
 * [DailyViewModel].
 */
class DailyMenuViewModel(application: Application) : AndroidViewModel(application) {
    private val progress = (application as RavelApplication).progress

    private val _today = MutableStateFlow(DailyDate.today())

    /** Today's ordinal, kept current by [followToday] while the menu is on screen. */
    val today: StateFlow<Long> = _today.asStateFlow()

    init {
        val puzzles = (application as RavelApplication).puzzles
        // Collected for the reason every board's view model collects it: the view model outlives
        // the activity a language switch recreates, and the switch empties the cache. And with the
        // date as well, because a new day is a new board to order.
        viewModelScope.launch {
            combine(application.language.current, _today, ::Pair).collect { (playing, day) ->
                if (!progress.state.value.isSolved(day)) {
                    puzzles.prefetch(PuzzleId(PuzzleMode.DAILY, day), playing)
                }
            }
        }
    }

    val state: StateFlow<DailyProgressState> = progress.state

    val balance: StateFlow<Int> = (application as RavelApplication).wallet.balance

    private val _month = MutableStateFlow(MonthGrid.containing(_today.value))

    /** The month the calendar is showing, which the player can walk backwards through. */
    val month: StateFlow<MonthGrid> = _month.asStateFlow()

    private val _selected = MutableStateFlow<Long?>(_today.value)

    /**
     * The day the card under the calendar is describing, or null when there is none to describe.
     *
     * Starts on today, so the card opens with something in it and the player learns what tapping a
     * day is for without having to tap one first.
     */
    val selected: StateFlow<Long?> = _selected.asStateFlow()

    fun select(ordinal: Long) {
        _selected.value = ordinal
    }

    fun showPreviousMonth() = show(_month.value.shifted(-1))

    /**
     * Steps forward, but never past the month today is in.
     *
     * There is nothing in a future month - no history to show and no board to open - and an arrow
     * that walks into empty grids forever invites the player to conclude the calendar is broken.
     */
    fun showNextMonth() {
        val next = _month.value.shifted(1)
        if (!next.isAfter(_today.value)) show(next)
    }

    /**
     * Keeps [today] current until the caller is cancelled: now, and then at every local midnight.
     *
     * Meant to run while the screen is started and no longer - the screen cancels it on stop and
     * calls it again on start, and that restart is the read that catches a night spent in the
     * background, a new time zone, or a clock set by hand. Nothing is scheduled while the app is
     * out of sight, so nothing has to survive Android freezing a cached process.
     *
     * The wait runs a second past midnight: a timer that fires a few milliseconds early would read
     * the old date, find nothing to do, and wait out another whole day.
     *
     * The streak is recounted on every pass, not only when [today] moves here: this view model can
     * be new while the store behind it has been holding a count since two midnights ago.
     */
    suspend fun followToday() {
        while (true) {
            progress.refresh()
            rollTo(DailyDate.today())
            delay(DailyDate.millisUntilNextDay(System.currentTimeMillis()) + MIDNIGHT_MARGIN_MILLIS)
        }
    }

    private fun rollTo(next: Long) {
        val previous = _today.value
        if (next == previous) return
        val focus = CalendarFocus(_month.value, _selected.value).afterDayChange(from = previous, to = next)
        _month.value = focus.month
        _selected.value = focus.selected
        _today.value = next
    }

    /**
     * Moves the calendar, and drops a selection the new month cannot show.
     *
     * A card describing the sixth of August under a grid showing July is not wrong so much as
     * unanswerable: the player can see the date on it and no way to get back to the cell it came
     * from. Clearing it says "pick one" instead.
     */
    private fun show(next: MonthGrid) {
        _month.value = next
        _selected.value = _selected.value?.takeIf(next::contains)
    }

    private companion object {
        const val MIDNIGHT_MARGIN_MILLIS = 1_000L
    }
}
