package eu.andret.ravel.ui.daily

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import eu.andret.ravel.RavelApplication
import eu.andret.ravel.game.DailyDate
import eu.andret.ravel.settings.DailyProgressState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the menu in front of the daily challenge knows: the run so far, the month being looked at,
 * and which day the play button opens.
 *
 * Holds no puzzle and starts no generation. The board is a screen away and a second or two of work,
 * and starting it here would spend that on every player who opened the tab to look at their streak.
 *
 * [today] is fixed when the menu opens, for the same reason [DailyViewModel] fixes its date: a
 * calendar that moved its "today" ring under a player at midnight would be stranger than one that
 * is a minute stale.
 */
class DailyMenuViewModel(application: Application) : AndroidViewModel(application) {
    private val progress = (application as RavelApplication).progress

    val today: Long = DailyDate.today()

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
