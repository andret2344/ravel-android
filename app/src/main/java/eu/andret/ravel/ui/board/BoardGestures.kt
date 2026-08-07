package eu.andret.ravel.ui.board

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Density
import eu.andret.ravel.game.areAdjacent
import eu.andret.ravel.generator.Board

/**
 * Taps and drags over the board, reported as cells.
 *
 * The two live in separate `pointerInput` blocks: a drag has to pass the touch slop before it is a
 * drag, and a tap that never moves that far is left for the tap detector.
 *
 * [path] and [solvedCells] are read through functions rather than captured. These blocks are set up
 * once per board and are not restarted on recomposition, so a captured value would be whatever the
 * board looked like the first time the player touched it.
 */
internal fun Modifier.boardGestures(
    board: Board,
    trace: TraceState,
    path: () -> List<Int>,
    solvedCells: () -> Set<Int>,
    onPathTraced: (List<Int>) -> Unit,
    onTap: (Int) -> Unit,
): Modifier = this
    .pointerInput(board) {
        val density: Density = this
        detectTapGestures { position ->
            BoardMetrics.of(board, size.width, size.height, density).cellAt(position)?.let(onTap)
        }
    }
    .pointerInput(board) {
        val density: Density = this
        // Rebuilt when a gesture starts rather than once: this block is not restarted when the
        // layout changes size, so metrics taken at setup would hit-test a rotated screen against
        // the shape the board had before the rotation.
        var metrics = BoardMetrics.of(board, size.width, size.height, density)
        detectDragGestures(
            onDragStart = { position ->
                metrics = BoardMetrics.of(board, size.width, size.height, density)
                trace.begin(board, path(), solvedCells(), metrics.cellAt(position))
            },
            onDrag = { change, _ ->
                if (!trace.active) return@detectDragGestures
                metrics.cellAt(change.position)?.let { cell ->
                    trace.extend(board, solvedCells(), cell)
                }
            },
            onDragEnd = {
                if (trace.active) onPathTraced(trace.cells.toList())
                trace.end()
            },
            onDragCancel = { trace.end() },
        )
    }

/**
 * The movement under the finger right now, which is the only board state this package owns.
 *
 * A snapshot list, so each cell the finger picks up redraws the canvas without waiting for the
 * gesture to end. [active] is separate from the list being empty: a gesture that began on a wall
 * owns nothing and must not be mistaken for one that has just cleared its path.
 */
@Stable
internal class TraceState {
    val cells: SnapshotStateList<Int> = mutableStateListOf()

    var active: Boolean by mutableStateOf(false)
        private set

    /**
     * Starts a gesture at [cell].
     *
     * Starting on the marked path picks it up where it was left, so a long word can be traced in as
     * many goes as the player likes. Starting on a wall or a finished word takes nothing.
     */
    fun begin(board: Board, path: List<Int>, solved: Set<Int>, cell: Int?) {
        cells.clear()
        active = when {
            cell == null -> false
            cell in path -> {
                cells.addAll(path.take(path.indexOf(cell) + 1))
                true
            }
            isTraceable(board, solved, cell) -> {
                cells.add(cell)
                true
            }
            else -> false
        }
    }

    /**
     * Adds [cell] to the trace, or removes the last one when the finger goes back the way it came.
     *
     * Backtracking is what makes tracing a long word bearable: a path taken one cell too far is
     * undone by moving back over it rather than by lifting and starting again.
     *
     * A finger that crosses a cell diagonally, or skips one by moving fast, is ignored until it
     * lands somewhere the path can legally continue. Guessing at the cells in between would be
     * guessing at the player's intent, and words wind, so the guess would often be wrong.
     */
    fun extend(board: Board, solved: Set<Int>, cell: Int) {
        if (cells.isEmpty()) {
            if (isTraceable(board, solved, cell)) cells.add(cell)
            return
        }
        if (cell == cells.last()) return
        if (cells.size >= 2 && cell == cells[cells.size - 2]) {
            cells.removeAt(cells.size - 1)
            return
        }
        if (cell in cells) return
        if (!isTraceable(board, solved, cell)) return
        if (!areAdjacent(board.width, cells.last(), cell)) return
        cells.add(cell)
    }

    fun end() {
        active = false
        cells.clear()
    }

    private fun isTraceable(board: Board, solved: Set<Int>, cell: Int): Boolean =
        !board.isWall(cell) && cell !in solved
}
