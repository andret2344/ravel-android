package eu.andret.ravel.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The one place the game asks what shape it has been given.
 *
 * Every screen that lays out differently on its side asks the same question, and it is deliberately
 * **not** "which way up is the phone". `Configuration.orientation` is a fact about how a device is
 * being held; what a layout needs to know is whether the window is wider than it is tall, which is a
 * different question on a tablet, in split screen, on a foldable, and on a desktop window. Asking the
 * window means none of those has to be named anywhere, and none of them can be forgotten.
 *
 * A single breakpoint and not Material's three window size classes. The classes answer "how much room
 * is there", which this game does not vary on - the board is a square and the menus are a handful of
 * cards, so nothing here wants a third arrangement for a large tablet that a wide phone would not
 * also want. What every screen here does vary on is which of the two dimensions is scarce.
 *
 * @param content given true when the window is wider than it is tall. Both branches are written out
 *   by each caller rather than one being derived from the other: they differ in what goes beside what,
 *   which is not something a modifier can express.
 */
@Composable
internal fun AdaptiveScreen(
    modifier: Modifier = Modifier,
    content: @Composable (wide: Boolean) -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        content(maxWidth > maxHeight)
    }
}
