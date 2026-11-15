package eu.andret.ravel.ui.board

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
        style.hint?.let { color -> outlineTile(cell, side, color) }
        when {
            style.concealed -> drawStar(cell, side * STAR_RATIO, style.letter)
            style.unveiled -> {
                // Behind the letter, large and faint: a mark of what the cell was, not a second
                // symbol competing with the letter for the eye.
                drawStar(
                    cell = cell,
                    size = side * UNVEILED_STAR_RATIO,
                    color = style.letter.copy(alpha = UNVEILED_STAR_ALPHA),
                    stroke = UNVEILED_STAR_STROKE,
                )
                drawLetter(cell, side, style)
            }
            else -> drawLetter(cell, side, style)
        }
    }

    /**
     * The hidden-letters star, centered in [cell] in a square of [size]: the geometry of
     * `ic_modifier_hidden_letters` - three strokes through the center, arms of 7/24 of the square,
     * 2/24 thick, round caps - drawn as lines rather than a typed "*", which sits high on the line like
     * a superscript.
     *
     * Lines and not the vector drawable through a `VectorPainter`: that painter renders into one
     * cached bitmap at the size it was last asked for, and a board draws stars at three sizes in one
     * frame - covered, behind an unveiled letter, shrunk in a marked word - so each one overwrote the
     * cache mid-frame and the board showed torn fragments of a star near the top of the cell.
     */
    private fun drawStar(cell: Int, size: Float, color: Color, stroke: Float = STAR_STROKE) {
        drawStarLines(metrics.centerOf(cell), size, color, stroke)
    }

    /** Fills a cell's tile and returns the side it was drawn at, which the letter is sized from. */
    private fun fillTile(cell: Int, scale: Float, color: Color): Float {
        val side = metrics.tile * scale
        drawRoundRect(
            color = color,
            topLeft = metrics.topLeftOf(cell, side),
            size = Size(side, side),
            cornerRadius = CornerRadius(side * BoardStyle.CORNER_RATIO),
        )
        return side
    }

    private fun outlineTile(cell: Int, side: Float, color: Color) {
        drawRoundRect(
            color = color,
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
    private fun drawRibbon(cells: List<Int>, color: Color) {
        val thickness = metrics.tile * BoardStyle.CONNECTOR_RATIO
        for (index in 1 until cells.size) {
            val start = metrics.centerOf(cells[index - 1])
            val end = metrics.centerOf(cells[index])
            drawRoundRect(
                color = color,
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

/** A covering star's side against the tile's, about a letter's height. */
private const val STAR_RATIO = 0.55f

/** The faint star behind an unveiled letter: most of the tile, so it reads as a background. */
internal const val UNVEILED_STAR_RATIO = 0.9f

/**
 * Faint enough that the letter in front of it reads first, and no fainter: 0.18 with the covering
 * star's thin stroke all but vanished into the found word's green, and 0.35 with a 3/24 stroke
 * crossed the letter out. Between the two, by eye.
 */
internal const val UNVEILED_STAR_ALPHA = 0.25f

/** Heavier than the covering star's, for the same reason: thin and faint together disappear. */
internal const val UNVEILED_STAR_STROKE = 2.5f / 24f

/** A star's arm and stroke against its square, as in `ic_modifier_hidden_letters` (7 and 2 of 24). */
private const val STAR_ARM = 7f / 24f
internal const val STAR_STROKE = 2f / 24f

/**
 * The hidden-letters star, centered on [center] in a square of [size], [stroke] of that square
 * thick - the board's cells and the completion list's tiles both draw it through here, so it is one
 * shape wherever it appears.
 */
internal fun DrawScope.drawStarLines(center: Offset, size: Float, color: Color, stroke: Float = STAR_STROKE) {
    val arm = size * STAR_ARM
    val across = arm * COS_30
    val width = size * stroke
    val strokes = listOf(
        Offset(0f, -arm) to Offset(0f, arm),
        Offset(-across, -arm / 2) to Offset(across, arm / 2),
        Offset(-across, arm / 2) to Offset(across, -arm / 2),
    )
    strokes.forEach { (from, to) ->
        drawLine(color, center + from, center + to, strokeWidth = width, cap = StrokeCap.Round)
    }
}

/** The two slanted strokes run at 60 degrees from the upright one. */
private const val COS_30 = 0.8660254f

/**
 * What a measured letter is cached under.
 *
 * [scale] belongs in the key as much as the letter does: a full-sized untouched cell and a shrunken
 * one inside a marked word are both drawn unweighted, and keying on weight alone would hand one of
 * them the other's measurement.
 */
private data class GlyphKey(val letter: Char, val leading: Boolean, val scale: Float)
