package eu.andret.ravel.ui.daily

/**
 * The daily tab's two destinations and the arguments that connect them.
 *
 * Written once and read by three places - the graph that declares the routes, the code that
 * navigates to them, and [DailyViewModel], which pulls the arguments back out of a
 * `SavedStateHandle` by name. Those names are strings that no compiler checks: spelled differently
 * in two of the three, the board would open on no day at all, and it would do so only at runtime.
 */
internal object DailyRoute {
    const val ARG_ORDINAL = "ordinal"
    const val ARG_REVIEW = "review"

    /** The menu: streak, ladder, calendar. The tab's start destination. */
    const val MENU = "daily"

    /**
     * One day's board.
     *
     * Both arguments are in the path rather than the query, because neither is optional: a board
     * with no day is not a board, and whether it is being played or reread changes everything the
     * screen does.
     */
    const val BOARD = "daily/board/{$ARG_ORDINAL}/{$ARG_REVIEW}"

    fun board(ordinal: Long, review: Boolean): String = "daily/board/$ordinal/$review"

    /**
     * Whether a board opened with these arguments is a review, given what day it is now.
     *
     * The route's own [ARG_REVIEW] is a request, not the answer. Only today's board can be played,
     * and the menu deciding "today" is not enough to guarantee that the board opens on it: a menu
     * that had gone stale overnight once sent yesterday's board as a live one, and a route restored
     * after the process died can arrive on any later day. Either would play, and pay a streak for,
     * a day that is over. So any day but [today] opens as a review whatever the route asked.
     *
     * Checked once, when the board opens, and never again: a player who starts before midnight and
     * finishes after it is finishing the board they were given, and is paid for that day.
     */
    fun opensForReview(reviewRequested: Boolean, ordinal: Long, today: Long): Boolean =
        reviewRequested || ordinal != today
}
