package eu.andret.ravel.ui.board

import androidx.compose.ui.graphics.Color

/**
 * What the board currently shows, and what that means for any one cell.
 *
 * Deciding a cell's appearance is kept apart from drawing it because the two ask different
 * questions: this is about the state of the puzzle, [BoardPainter] is about pixels. It also means
 * the interesting decision - which of four states a cell is in - is one small class that can be
 * read in one sitting.
 *
 * @param path the cells marked right now, which is the live gesture while one is under way and the
 *   puzzle's standing path otherwise.
 */
internal class BoardView(
    val path: List<Int>,
    val solvedPaths: List<List<Int>>,
    private val solvedCells: Set<Int>,
    private val hintedCells: Set<Int>,
) {
    /**
     * Where each marked word begins.
     *
     * A word is a path, and a path has a direction its cells alone do not show. Since a word can
     * only be traced the way it reads, where it begins is the one thing the player needs to see.
     */
    private val heads: Set<Int> = buildSet {
        solvedPaths.forEach { solved -> solved.firstOrNull()?.let(::add) }
        path.firstOrNull()?.let(::add)
    }

    /** Everything after a head, drawn smaller - which says the same thing as enlarging the head
     * would, without anything growing outside its own cell. */
    private val tails: Set<Int> = buildSet {
        solvedPaths.forEach { solved -> addAll(solved.drop(1)) }
        addAll(path.drop(1))
    }

    fun styleOf(cell: Int, palette: BoardPalette): CellStyle {
        val solved = cell in solvedCells
        val marked = cell in path
        return CellStyle(
            fill = when {
                solved -> palette.solved
                marked -> palette.marked
                else -> palette.cell
            },
            letter = when {
                solved -> palette.solvedLetter
                marked -> palette.markedLetter
                else -> palette.letter
            },
            scale = if (cell in tails) BoardStyle.TAIL_SCALE else 1f,
            leading = cell in heads,
            // A found word is drawn as found; repeating that a hint had a hand in it would only
            // clutter a cell the player is done with.
            hint = palette.hint.takeIf { cell in hintedCells && !solved },
        )
    }
}

/**
 * How one cell is drawn.
 *
 * @param scale shrinks the tile about its own centre, which is how every cell of a marked word
 *   except the first is played down.
 * @param leading adds weight to the letter a word begins at. Size alone would say it, but the two
 *   together survive a glance at a board where most cells are untouched and full-sized anyway.
 * @param hint the outline colour when a hint gave this cell away, null when it did not.
 */
internal class CellStyle(
    val fill: Color,
    val letter: Color,
    val scale: Float,
    val leading: Boolean,
    val hint: Color?,
)
