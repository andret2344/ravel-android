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
}
