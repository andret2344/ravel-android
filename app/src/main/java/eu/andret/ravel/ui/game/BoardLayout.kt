package eu.andret.ravel.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.andret.ravel.ui.AdaptiveScreen

/**
 * The arrangement both board screens are built out of: a grid, and everything that is not the grid.
 *
 * ## Why one file for two screens
 *
 * The daily challenge and a level are the same screen with a different thing to say at the top, which
 * is already why [BoardTopBar], [CluesRow] and [HintRow] live together in this package. The layout
 * itself was the part still copied into both, and it went out of step exactly as copies do: the level
 * screen learned to hang its heading off the board and the daily screen never did. Two orientations
 * would have doubled that copy rather than the one it was, so the arrangement moved here first.
 *
 * ## The two arrangements
 *
 * **Tall** - the three bands it has always been: the bar, the board with its heading, and the clues
 * and prices under it. Nothing scrolls, because a grid that moves under a finger being dragged across
 * it is a grid that loses the drag, and the board takes whatever is left after the fixed bands so
 * that holds on a short phone as well as a tall one.
 *
 * **Wide** - the grid on the left, everything else in a column on the right. A board is square and a
 * wide window is not, so a stacked layout on its side spends the scarce dimension - height - on
 * things that are not the board and leaves the board a few pixels tall. Side by side, the grid gets
 * the full height of the window and the leftover width goes to the text, which is the one thing here
 * that reads fine in a narrow column.
 *
 * The bar goes in the right-hand column rather than across the top, so the left half is board and
 * nothing else. The cost is real and worth naming: the close button and the balance are no longer in
 * the screen corners they occupy in portrait, so a player who learned where they are learns it twice.
 * The alternative spends about 44dp of the board's own height - on a phone on its side that is a tenth
 * of the grid - to keep two buttons where the muscle remembers them.
 *
 * The right column scrolls and the left one never does. A tall clue row plus two priced buttons plus
 * the tally can outgrow a 360dp-high window, and the thing to do about that is scroll the text, not
 * shrink the board. Scrolling is safe on the right for the same reason it is banned on the left: no
 * gesture over there is tracing anything.
 *
 * ## Which one
 *
 * [AdaptiveScreen] decides, and the reasoning for measuring the window rather than asking the device
 * which way up it is lives there - every screen in this game with two arrangements asks through it.
 *
 * @param topBar the mode's controls - see [BoardTopBar]. Emitted into the right column when wide.
 * @param title what board this is. Sits against the grid rather than in a band of its own, because
 *   the heading names the board: given the column's own rhythm it ends up as far from the grid as
 *   the grid is from the clues, and three evenly spaced bands read as three unrelated things.
 * @param board the grid, or whatever stands in for it while there is not one - it is given a square
 *   and centred in it either way.
 * @param footer the clues, the prices and the tally. Emitted straight into the surrounding column, so
 *   its own children are spaced by that column and not by a wrapper of their own.
 */
@Composable
internal fun BoardLayout(
    topBar: @Composable () -> Unit,
    title: @Composable () -> Unit,
    board: @Composable () -> Unit,
    footer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    AdaptiveScreen(modifier = modifier.padding(horizontal = 20.dp, vertical = 12.dp)) { wide ->
        if (wide) {
            Wide(topBar = topBar, title = title, board = board, footer = footer)
        } else {
            Tall(topBar = topBar, title = title, board = board, footer = footer)
        }
    }
}

@Composable
private fun Tall(
    topBar: @Composable () -> Unit,
    title: @Composable () -> Unit,
    board: @Composable () -> Unit,
    footer: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        topBar()
        // The heading and the grid are one thing and are spaced as one, then the pair is centred in
        // what is left over. The board is measured at weight(fill = false) so the box is the size of
        // the grid rather than of the space it was offered - given the whole remaining height it
        // would centre itself in that instead, and "under the heading" would stop meaning anything.
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        ) {
            title()
            Square(modifier = Modifier.weight(1f, fill = false), content = board)
        }
        footer()
    }
}

@Composable
private fun Wide(
    topBar: @Composable () -> Unit,
    title: @Composable () -> Unit,
    board: @Composable () -> Unit,
    footer: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Five ninths and four, not half each, and the weights are a ceiling on the board's width
        // rather than a share of it. The square takes whichever is smaller of the height and the
        // space it is offered, and on a phone on its side the height is much the smaller - so at half
        // each the board came out a fifth narrower than the window could carry, with the spare width
        // sitting empty beside it. Past the point where the height binds, every further slice of
        // width goes to the column instead, which is where it is short: the two priced buttons are
        // about 220dp side by side and a narrower column puts them on separate lines.
        //
        // A ceiling rather than "give the board the height and the column what is left", because that
        // has no ceiling: on a window barely wider than it is tall, a board sized purely by the height
        // would take nearly all the width and leave the clues a strip down the edge.
        Box(
            modifier = Modifier.weight(5f).fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            Square(content = board)
        }
        Column(
            modifier = Modifier
                .weight(4f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            topBar()
            title()
            footer()
        }
    }
}

/**
 * The biggest square that fits in whatever this is given.
 *
 * Deliberately no `fillMaxSize` in front of the ratio: that fixes both constraints, leaving
 * [aspectRatio] nothing to choose between and producing a square as wide as the space and as tall as
 * that width. Left unfixed, the ratio takes whichever of the two bounds it can satisfy - which is the
 * height in a wide window and the width in a tall one - and the parent's alignment centres the result.
 */
@Composable
private fun Square(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier.aspectRatio(1f),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}
