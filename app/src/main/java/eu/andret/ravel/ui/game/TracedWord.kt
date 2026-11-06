package eu.andret.ravel.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import eu.andret.ravel.game.Puzzle
import eu.andret.ravel.ui.board.BoardPalette
import eu.andret.ravel.ui.board.BoardStyle
import eu.andret.ravel.ui.board.TraceState
import eu.andret.ravel.ui.board.rememberBoardPalette
import eu.andret.ravel.ui.board.shownPath

/**
 * The letters being traced, laid out in a line between the board and the clues, growing and
 * shrinking with the finger.
 *
 * A path that winds is hard to read off the grid: the letters are in the right order but not in a
 * line, and the eye has to follow the turns to read them. Here they are a word, which is the thing
 * the player is actually asking about while they drag - "is this one?".
 *
 * **Drawn as the board draws a traced word**, not as text: the same [BoardPalette.marked] tiles,
 * corner ratio and joining ribbon, the first tile full-sized and bold and the rest at
 * [BoardStyle.TAIL_SCALE]. The strip and the grid are then visibly one thing - the word under the
 * finger, straightened out - rather than a caption about it. Only the trace's colour is borrowed,
 * never a found word's: a found word clears the path, so the strip empties on success by itself.
 *
 * Reads the trace while the finger is down and the marked path once it lifts, through
 * [TraceState.shownPath], which is exactly what the grid highlights.
 *
 * **Always the same height, even empty.** In the tall layout the board is sized from what is left
 * over, so a strip that appeared on the first cell would shrink the grid under the finger in the
 * middle of a drag - and the drag hit-tests against the size the grid had when it started.
 *
 * **Tile size is fixed per board, not per word.** It is set by the longest word the board hides,
 * so a twelve-letter word fits a phone held upright, and it does not shrink letter by letter as a
 * word grows - which would make every tile move under the player's eyes on every cell. A trace
 * longer than any word (a wrong one) is the one case allowed to shrink the tiles, rather than run
 * off the edge.
 *
 * No semantics beyond the letters themselves. The board is not a screen-reader surface (see
 * `docs/DECISIONS.md`), so announcing every letter would be speaking for a gesture a screen-reader
 * user cannot make.
 */
@Composable
internal fun TracedWord(puzzle: Puzzle, trace: TraceState) {
    val palette = rememberBoardPalette()
    val letters = puzzle.board.letters
    val cells = trace.shownPath(puzzle)
    val longestWord = puzzle.clues.maxOfOrNull { it.length } ?: 0

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().height(MAX_TILE),
        contentAlignment = Alignment.Center,
    ) {
        val slots = maxOf(longestWord, cells.size, 1)
        val tile = min(MAX_TILE, (maxWidth - GAP * (slots - 1)) / slots)
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            cells.forEachIndexed { index, cell ->
                if (index > 0) Ribbon(tile, palette)
                LetterTile(
                    letter = letters[cell].uppercaseChar(),
                    side = if (index == 0) tile else tile * BoardStyle.TAIL_SCALE,
                    leading = index == 0,
                    palette = palette,
                )
            }
        }
    }
}

/** One letter, drawn to the same proportions as a marked cell on the board. */
@Composable
private fun LetterTile(letter: Char, side: Dp, leading: Boolean, palette: BoardPalette) {
    Box(
        modifier = Modifier
            .size(side)
            .background(palette.marked, RoundedCornerShape(side * BoardStyle.CORNER_RATIO)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter.toString(),
            color = palette.markedLetter,
            style = TextStyle(
                fontSize = with(LocalDensity.current) { (side * BoardStyle.FONT_RATIO).toSp() },
                fontWeight = if (leading) FontWeight.Bold else FontWeight.Medium,
            ),
        )
    }
}

/**
 * The bar between two tiles, as thick against the tile as the board's own ribbon is. On the board
 * it runs from centre to centre underneath the tiles; here the tiles sit in a row, so only the part
 * showing in the gap is drawn.
 */
@Composable
private fun Ribbon(tile: Dp, palette: BoardPalette) {
    Box(
        modifier = Modifier
            .width(GAP)
            .height(tile * BoardStyle.TAIL_SCALE * BoardStyle.CONNECTOR_RATIO)
            .background(palette.marked),
    )
}

/** A tile no bigger than a comfortable board cell on a phone, which is also the strip's height. */
private val MAX_TILE = 36.dp

/** The space between tiles, which the ribbon fills. */
private val GAP = 4.dp
