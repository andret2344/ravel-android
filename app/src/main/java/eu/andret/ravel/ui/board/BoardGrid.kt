package eu.andret.ravel.ui.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
 */
@Composable
fun BoardGrid(
    puzzle: Puzzle,
    onPathTraced: (List<Int>) -> Unit,
    onTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val board = puzzle.board
    val textMeasurer = rememberTextMeasurer()
    val palette = rememberBoardPalette()
    val trace = remember(board) { TraceState() }
    // The gesture handlers are set up once per board and would otherwise capture the first state
    // and callbacks they ever saw, which go stale as soon as the screen recomposes.
    val current by rememberUpdatedState(puzzle)
    val currentOnPathTraced by rememberUpdatedState(onPathTraced)
    val currentOnTap by rememberUpdatedState(onTap)

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
                onTap = { cell -> currentOnTap(cell) },
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
