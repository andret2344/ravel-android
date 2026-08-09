package eu.andret.ravel.ui.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.rememberTextMeasurer
import eu.andret.ravel.game.Puzzle

/**
 * The board as the player sees it, and the surface they trace on.
 *
 * Drawn on a [Canvas] rather than assembled from one composable per cell. A 12x12 board is 144
 * cells, and tracing turns a moving finger into a cell index dozens of times a second. That is one
 * division against [BoardMetrics] here, against 144 layout nodes and a hit-test tree the other way.
 *
 * The grid takes whatever space it is given and fits itself inside, centred, with square cells. It
 * never asks for a size of its own, so the screen around it can hand it the room left over after
 * the header and the clues and be certain nothing overflows.
 *
 * The marked path lives in [puzzle] rather than here, because it outlives the gesture that drew it:
 * lifting a finger leaves the cells marked, and the next gesture or tap continues from them. Only
 * the movement currently under the finger belongs to this composable, in [TraceState].
 *
 * @param onPathTraced the cells the finger covered, in order, when it lifts.
 * @param onTap the cell tapped, for the corrections a tap makes - see [Puzzle.tapped].
 * @param hapticsEnabled whether the player has left board haptics on. Passed down from the screen's
 *   view model rather than read here, because a composable that fetched its own copy of a setting
 *   would be the second answer to a question the process already has one owner for.
 */
@Composable
fun BoardGrid(
    puzzle: Puzzle,
    onPathTraced: (List<Int>) -> Unit,
    onTap: (Int) -> Unit,
    hapticsEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val board = puzzle.board
    val textMeasurer = rememberTextMeasurer()
    val palette = rememberBoardPalette()
    val haptics = rememberBoardHaptics(hapticsEnabled)
    val trace = remember(board) { TraceState(haptics) }
    // The gesture handlers are set up once per board and would otherwise capture the first state
    // and callbacks they ever saw, which go stale as soon as the screen recomposes.
    val current by rememberUpdatedState(puzzle)
    val currentOnPathTraced by rememberUpdatedState(onPathTraced)
    val currentOnTap by rememberUpdatedState(onTap)

    /*
     * The two heavier events, watched on the puzzle rather than raised where they happen.
     *
     * There are four ways to complete a word - trace it, tap its last cell, buy a letter that turns
     * out to be the last one, buy the whole word - and the same reasoning that put the level's
     * reward on the transition rather than on each of those four routes applies here: a buzz fired
     * at every route is a buzz fired twice by the fifth. What the player felt is that the board
     * changed, and the board is the thing to watch.
     *
     * Seeded from the puzzle as it stands when the effect starts, which is what keeps a reopened
     * board silent: a level with three words already found, or one that was finished last week and
     * is being looked at again, must not thump on the frame it appears.
     *
     * Keyed on the board rather than on the puzzle, so the seeding happens once per board and not
     * once per move - keyed on the puzzle, every move would restart the effect with the move already
     * baked into the baseline and nothing would ever fire.
     */
    LaunchedEffect(board, haptics) {
        var found = puzzle.found.size
        var complete = puzzle.isComplete
        snapshotFlow { current }.collect { next ->
            when {
                // The board wins when one move is both. The last word of a board is not a word
                // found and then a board finished; it is a board finished, and two effects a
                // millisecond apart read as one badly-made effect rather than as two events.
                next.isComplete && !complete -> haptics.board()
                next.found.size > found -> haptics.word()
            }
            found = next.found.size
            complete = next.isComplete
        }
    }

    val view = BoardView(
        path = if (trace.active) trace.cells else puzzle.path,
        solvedPaths = puzzle.solvedPaths,
        solvedCells = puzzle.solvedCells,
        hintedCells = puzzle.hintedCells,
    )

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .boardGestures(
                board = board,
                trace = trace,
                path = { current.path },
                solvedCells = { current.solvedCells },
                onPathTraced = { cells -> currentOnPathTraced(cells) },
                // The tap's tick is fired here and not from the puzzle that comes back, because a
                // tap changes the path in four different ways and three of them are not a cell
                // being added - clearing the path, cutting it back, starting a new one. All four
                // are the same thing to a finger, and the one case that is not is the tap the board
                // ignores, which is what Puzzle.accepts is for.
                onTap = { cell ->
                    if (current.accepts(cell)) haptics.cell()
                    currentOnTap(cell)
                },
            ),
    ) {
        BoardPainter(
            scope = this,
            board = board,
            metrics = BoardMetrics(board, size.width, size.height, this),
            palette = palette,
            textMeasurer = textMeasurer,
        ).draw(view)
    }
}
