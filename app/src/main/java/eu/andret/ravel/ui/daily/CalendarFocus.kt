package eu.andret.ravel.ui.daily

/**
 * Where the daily menu's calendar is looking: the month on screen and the day its card describes.
 *
 * A class of its own, and not two fields inside [DailyMenuViewModel], only so that what happens to
 * them when the date changes can be tested without a view model around it.
 */
internal data class CalendarFocus(val month: MonthGrid, val selected: Long?) {

    /**
     * Where the calendar should look once "today" has moved from [from] to [to].
     *
     * The calendar follows the player's today, and leaves alone whatever the player chose to look at
     * instead:
     *
     * - The month moves to [to]'s only when it was showing [from]'s. A player who walked back to
     *   March to read their history is still reading it after midnight. It also moves when it would
     *   otherwise be later than [to]'s - a clock wound back across a month end - because the calendar
     *   never shows a month with no history in it, see [DailyMenuViewModel.showNextMonth].
     * - The selection moves with today only when it *was* today. Any other day stays selected while
     *   its month is on screen, and is dropped when it is not, for the reason
     *   [DailyMenuViewModel] drops one on a month change. A selection later than [to] is dropped
     *   too: the card would offer a day that has not happened.
     */
    fun afterDayChange(from: Long, to: Long): CalendarFocus {
        if (from == to) return this
        val nextMonth = if (month.contains(from) || month.isAfter(to)) MonthGrid.containing(to) else month
        val nextSelected = when {
            selected == from -> to
            selected == null || selected > to -> null
            else -> selected.takeIf(nextMonth::contains)
        }
        return CalendarFocus(nextMonth, nextSelected)
    }
}
