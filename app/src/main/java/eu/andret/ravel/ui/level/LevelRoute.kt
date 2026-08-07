package eu.andret.ravel.ui.level

/**
 * The main mode's two destinations and the argument that connects them.
 *
 * Written once and read by three places - the graph that declares the routes, the code that
 * navigates to them, and [LevelViewModel], which pulls the argument back out of a `SavedStateHandle`
 * by name. That name is a string no compiler checks: spelled differently in two of the three, the
 * board would open on no level at all, and it would do so only at runtime.
 */
internal object LevelRoute {
    const val ARG_LEVEL = "level"

    /** The ladder: which level is next, and how far the player has got. The tab's start. */
    const val MENU = "main"

    /**
     * One level's board.
     *
     * The level is in the path rather than the query because a board with no level is not a board.
     * There is no "review" argument beside it, as there is on the daily challenge: a level is either
     * the one being played or one the player cannot open at all.
     */
    const val BOARD = "main/board/{$ARG_LEVEL}"

    fun board(level: Int): String = "main/board/$level"
}
