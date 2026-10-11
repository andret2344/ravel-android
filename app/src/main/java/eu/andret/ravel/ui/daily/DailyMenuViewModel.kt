package eu.andret.ravel.ui.daily

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import eu.andret.ravel.RavelApplication
import eu.andret.ravel.game.DailyDate
import eu.andret.ravel.game.PuzzleId
import eu.andret.ravel.game.PuzzleMode
import eu.andret.ravel.settings.DailyProgressState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * [today] is fixed when the menu opens, for the same reason [DailyViewModel] fixes its date: a
 * calendar that moved its "today" ring under a player at midnight would be stranger than one that
 * is a minute stale.
 */
class DailyMenuViewModel(application: Application) : AndroidViewModel(application) {
    private val progress = (application as RavelApplication).progress

    val today: Long = DailyDate.today()

    init {
        val puzzles = (application as RavelApplication).puzzles
        // Collected for the reason every board's view model collects it: the view model outlives
        // the activity a language switch recreates, and the switch empties the cache.
        viewModelScope.launch {
            (application as RavelApplication).language.current.collect { playing ->
                if (!progress.state.value.isSolved(today)) {
                    puzzles.prefetch(PuzzleId(PuzzleMode.DAILY, today), playing)
                }
            }
        }
    }

    val state: StateFlow<DailyProgressState> = progress.state

    val balance: StateFlow<Int> = (application as RavelApplication).wallet.balance

    private val _month = MutableStateFlow(MonthGrid.containing(today))

    /** The month the calendar is showing, which the player can walk backwards through. */
    val month: StateFlow<MonthGrid> = _month.asStateFlow()

    private val _selected = MutableStateFlow<Long?>(today)

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
        if (!next.isAfter(today)) show(next)
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
}
