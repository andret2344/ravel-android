package eu.andret.ravel.ui.board

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import eu.andret.ravel.generator.Board
import kotlin.math.abs

/**
 * Draws a board onto a [DrawScope], one pass, no state of its own beyond a cache.
 *
 * Built fresh for each frame, which is why the cache lives here: it is warm for the hundred-odd
 * cells of one board and gone before it can go stale. Delegating [DrawScope] lets the drawing calls
 * read the way they would inside a `Canvas` block.
 */
internal class BoardPainter(
    scope: DrawScope,
    private val board: Board,
    private val metrics: BoardMetrics,
    private val palette: BoardPalette,
    private val textMeasurer: TextMeasurer,
) : DrawScope by scope {

    // Measured once per letter and size, not once per cell: a board repeats the same few
    // characters, and text measurement is the expensive part of drawing one.
    private val glyphs = HashMap<GlyphKey, TextLayoutResult>()

    fun draw(view: BoardView) {
        // Words are drawn as connected ribbons rather than as separate tiles: the ribbon is what
        // says "these cells are one word", both for what has been found and for what is being
        // traced, and for the latter it also shows the order the finger took.
        view.solvedPaths.forEach { solved -> drawRibbon(solved, palette.solved) }
        drawRibbon(view.path, palette.marked)
        for (cell in 0 until board.width * board.height) {
            if (board.isWall(cell)) fillTile(cell, 1f, palette.wall) else drawCell(cell, view)
        }
    }

    private fun drawCell(cell: Int, view: BoardView) {
        val style = view.styleOf(cell, palette)
        val side = fillTile(cell, style.scale, style.fill)
        // A hint is drawn as an outline rather than as another fill, so a cell can say both "you
        // were told this one" and whichever state it is in at the same time.
        style.hint?.let { colour -> outlineTile(cell, side, colour) }
        drawLetter(cell, side, style)
    }

    /** Fills a cell's tile and returns the side it was drawn at, which the letter is sized from. */
    private fun fillTile(cell: Int, scale: Float, colour: Color): Float {
        val side = metrics.tile * scale
        drawRoundRect(
            color = colour,
            topLeft = metrics.topLeftOf(cell, side),
            size = Size(side, side),
            cornerRadius = CornerRadius(side * BoardStyle.CORNER_RATIO),
        )
        return side
    }

    private fun outlineTile(cell: Int, side: Float, colour: Color) {
        drawRoundRect(
            color = colour,
            topLeft = metrics.topLeftOf(cell, side),
            size = Size(side, side),
            cornerRadius = CornerRadius(side * BoardStyle.CORNER_RATIO),
            style = Stroke(width = side * BoardStyle.HINT_STROKE_RATIO),
        )
    }

    private fun drawLetter(cell: Int, side: Float, style: CellStyle) {
        val letter = board.letters[cell]
        val glyph = glyphs.getOrPut(GlyphKey(letter, style.leading, style.scale)) {
            measure(letter, side, style.leading)
        }
        drawText(
            textLayoutResult = glyph,
            color = style.letter,
            topLeft = metrics.topLeftOf(cell, side) +
                Offset((side - glyph.size.width) / 2f, (side - glyph.size.height) / 2f),
        )
    }

    private fun measure(letter: Char, side: Float, leading: Boolean): TextLayoutResult =
        textMeasurer.measure(
            text = letter.uppercaseChar().toString(),
            style = TextStyle(
                fontSize = (side * BoardStyle.FONT_RATIO).toSp(),
                fontWeight = if (leading) FontWeight.Bold else FontWeight.Medium,
            ),
        )

    /** The bars joining consecutive cells of a path, drawn beneath them so it reads as one word. */
    private fun drawRibbon(cells: List<Int>, colour: Color) {
        val thickness = metrics.tile * BoardStyle.CONNECTOR_RATIO
        for (index in 1 until cells.size) {
            val start = metrics.centreOf(cells[index - 1])
            val end = metrics.centreOf(cells[index])
            drawRoundRect(
                color = colour,
                topLeft = Offset(minOf(start.x, end.x), minOf(start.y, end.y)) -
                    Offset(thickness / 2, thickness / 2),
                size = Size(
                    width = abs(end.x - start.x) + thickness,
                    height = abs(end.y - start.y) + thickness,
                ),
                cornerRadius = CornerRadius(thickness / 2),
            )
        }
    }
}

/**
 * What a measured letter is cached under.
 *
 * [scale] belongs in the key as much as the letter does: a full-sized untouched cell and a shrunken
 * one inside a marked word are both drawn unweighted, and keying on weight alone would hand one of
 * them the other's measurement.
 */
private data class GlyphKey(val letter: Char, val leading: Boolean, val scale: Float)
