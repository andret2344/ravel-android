package eu.andret.ravel.ui.board

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Density
import eu.andret.ravel.game.Puzzle
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
    onTapOffGrid: () -> Unit,
): Modifier = this
    .pointerInput(board) {
        val density: Density = this
        detectTapGestures { position ->
            // The canvas is square and a board need not be, so there is margin inside it that is
            // not a cell. To the player that margin is the screen around the board, and a tap there
            // means what a tap on the screen means. This detector consumes the tap either way, so
            // the screen's own detector never hears it - it has to be passed on from here.
            val cell = BoardMetrics.of(board, size.width, size.height, density).cellAt(position)
            if (cell != null) onTap(cell) else onTapOffGrid()
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
 * The cells a screen should show as the word being spelled: the trace under the finger while there
 * is one, otherwise the path [puzzle] keeps marked after the finger lifts - the same choice
 * [BoardGrid] draws from, so the strip under the board and the grid can never disagree.
 */
internal fun TraceState.shownPath(puzzle: Puzzle): List<Int> = if (active) cells else puzzle.path

/**
 * A [TraceState] for [board], made by the screen rather than by [BoardGrid] so that something
 * outside the grid - the word strip under it - can read the trace while the finger is still moving.
 *
 * Keyed on the board, so a board regenerated under the same screen starts with nothing traced.
 */
@Composable
internal fun rememberTraceState(board: Board?, hapticsEnabled: Boolean): TraceState {
    val haptics = rememberBoardHaptics(hapticsEnabled)
    return remember(board, haptics) { TraceState(haptics) }
}

/**
 * The movement under the finger right now: the only board state that is not in the puzzle.
 *
 * Created by the screen through [rememberTraceState] and handed to [BoardGrid]. It used to be made
 * inside the grid, which was enough while only the grid drew it; hoisting it is what lets the word
 * strip spell the trace letter by letter instead of only after the finger lifts.
 *
 * A snapshot list, so each cell the finger picks up redraws the canvas without waiting for the
 * gesture to end. [active] is separate from the list being empty: a gesture that began on a wall
 * owns nothing and must not be mistaken for one that has just cleared its path.
 *
 * The cell tick is fired from here, at the mutation, rather than from a snapshot of the list's
 * length. Two cells crossed inside one frame are two ticks and a cell taken back is a third, and a
 * watcher counting the list would report one of those as nothing happening at all - backtracking
 * removes a cell and moving on adds one, which leaves the length exactly where it started. Since
 * the tick is what tells the player the board took the cell, missing it is missing the answer to
 * the only question they are asking while the finger is down.
 *
 * @param haptics fired once per cell joined or dropped. Not a callback out to the screen, because
 *   there is nothing for a screen to decide here - the decision is which cell the trace accepts,
 *   and this is the class that makes it.
 */
@Stable
internal class TraceState(private val haptics: BoardHaptics) {
    val cells: SnapshotStateList<Int> = mutableStateListOf()

    var active: Boolean by mutableStateOf(false)
        private set

    /**
     * Starts a gesture at [cell].
     *
     * Starting on the marked path picks it up where it was left, so a long word can be traced in as
     * many goes as the player likes. Starting on a wall or a finished word takes nothing.
     *
     * One tick for a gesture that took hold of something, however many cells it picked up. Picking
     * up a standing path is a single act to the player - they put a finger on a word they had
     * already started - and a tick per cell of it would spend the whole board's haptic budget on a
     * touch that changed nothing on screen.
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
        if (active) haptics.cell()
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
            if (isTraceable(board, solved, cell)) {
                cells.add(cell)
                haptics.cell()
            }
            return
        }
        if (cell == cells.last()) return
        if (cells.size >= 2 && cell == cells[cells.size - 2]) {
            cells.removeAt(cells.size - 1)
            // Undoing a cell is the same tick as taking one. It is the same size of event to the
            // player - one letter changed hands - and giving it an effect of its own would be
            // teaching a second sensation to say something the screen already shows.
            haptics.cell()
            return
        }
        if (cell in cells) return
        if (!isTraceable(board, solved, cell)) return
        if (!areAdjacent(board.width, cells.last(), cell)) return
        cells.add(cell)
        haptics.cell()
    }

    fun end() {
        active = false
        cells.clear()
    }

    private fun isTraceable(board: Board, solved: Set<Int>, cell: Int): Boolean =
        !board.isWall(cell) && cell !in solved
}
