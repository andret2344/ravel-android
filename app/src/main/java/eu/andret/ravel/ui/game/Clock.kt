package eu.andret.ravel.ui.game

import java.util.Locale

/**
 * A duration as minutes and seconds, the way a stopwatch reads.
 *
 * Fixed formatting rather than a localised one: this is a clock, not a sentence, and every language
 * writes 4:07 the same way. [Locale.ROOT] and not the player's, so a locale with its own digits does
 * not turn the running counter into a different set of shapes every time the phone's language
 * changes.
 *
 * Minutes are not clamped at sixty. A board that took ninety minutes reads 90:12 rather than
 * 1:30:12, because the number being compared against other boards is minutes, and an hours field
 * that is almost always zero is a column of noise.
 */
internal fun asClock(seconds: Int): String =
    String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)

/**
 * The same reading for a total rather than for a board: hours once there are any.
 *
 * [asClock] deliberately lets minutes run past sixty, because a board is compared against other
 * boards and 90:12 is easier to compare than 1:30:12. A lifetime total is compared against nothing,
 * and 4,317 minutes is not a number anybody reads - it is a number they have to divide.
 */
internal fun asDuration(seconds: Int): String = when {
    seconds < SECONDS_IN_HOUR -> asClock(seconds)
    else -> String.format(
        Locale.ROOT,
        "%d:%02d:%02d",
        seconds / SECONDS_IN_HOUR,
        (seconds % SECONDS_IN_HOUR) / 60,
        seconds % 60,
    )
}

private const val SECONDS_IN_HOUR = 3600
