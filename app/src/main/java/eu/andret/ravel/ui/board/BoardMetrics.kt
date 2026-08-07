package eu.andret.ravel.ui.board

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import eu.andret.ravel.generator.Board

/**
 * Where every cell of a board sits inside a given rectangle.
 *
 * The drawing pass and both gesture handlers ask this same class, because all three have to agree
 * exactly: the moment the arithmetic is written twice, the cell under the finger stops being the
 * cell that lights up.
 *
 * The grid is fitted into whatever space it is handed and centred there, with square cells, so a
 * caller only has to decide how much room the board gets - never what shape it is.
 */
internal class BoardMetrics(
    private val board: Board,
    width: Float,
    height: Float,
    density: Density,
) {
    private val cellSize: Float = minOf(width / board.width, height / board.height)
    private val originX: Float = (width - cellSize * board.width) / 2f
    private val originY: Float = (height - cellSize * board.height) / 2f
    private val gap: Float = with(density) {
        (cellSize * BoardStyle.GAP_RATIO).coerceAtMost(BoardStyle.MAX_GAP.toPx())
    }

    /** The drawn square inside a cell, which is the cell less the gap around it. */
    val tile: Float = cellSize - gap

    fun centreOf(cell: Int): Offset = Offset(
        originX + (cell % board.width + 0.5f) * cellSize,
        originY + (cell / board.width + 0.5f) * cellSize,
    )

    /** The top left of a tile of [side] centred on [cell], whatever size that cell is drawn at. */
    fun topLeftOf(cell: Int, side: Float): Offset = centreOf(cell) - Offset(side / 2, side / 2)

    /**
     * The cell under [position], or null when the finger is outside the grid.
     *
     * Cells are hit across their whole square including the gap, not just the drawn tile: a finger
     * dragged along a row would otherwise stutter every time it crossed a gap.
     */
    fun cellAt(position: Offset): Int? {
        if (position.x < originX || position.y < originY) return null
        val column = ((position.x - originX) / cellSize).toInt()
        val row = ((position.y - originY) / cellSize).toInt()
        if (column !in 0 until board.width || row !in 0 until board.height) return null
        return row * board.width + column
    }

    companion object {
        /** For callers holding a pixel size as integers, which is what a pointer scope reports. */
        fun of(board: Board, width: Int, height: Int, density: Density) =
            BoardMetrics(board, width.toFloat(), height.toFloat(), density)
    }
}
