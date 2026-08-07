package eu.andret.ravel.ui.board

import androidx.compose.ui.unit.dp

/**
 * The proportions the board is drawn to, all of them relative to a cell.
 *
 * Relative and not absolute because the same board is drawn at every size from a 5x5 on a small
 * phone to a 12x12 on a tablet, and a gap or a corner radius fixed in dp reads as a hairline on one
 * and as a moat on the other. The one exception is [MAX_GAP], which exists precisely to stop a
 * proportion running away on a large screen.
 */
internal object BoardStyle {
    /** Share of a cell given over to the gap between tiles. */
    const val GAP_RATIO = 0.10f

    /** Beyond this the gaps read as a fence rather than a grid on large phones. */
    val MAX_GAP = 6.dp

    const val CORNER_RATIO = 0.18f

    /** Thickness of the ribbon joining a word's cells, against the tile it joins. */
    const val CONNECTOR_RATIO = 0.55f

    /** Thickness of the outline marking a hinted cell. */
    const val HINT_STROKE_RATIO = 0.09f

    /** Cap height against tile size. Above ~0.6 the diacritics on Ł and Ó clip on small boards. */
    const val FONT_RATIO = 0.52f

    /**
     * How much smaller every cell of a marked word after the first is drawn.
     *
     * Shrinking the rest rather than growing the head keeps every tile inside its own cell, so the
     * grid stays a grid and nothing has to be drawn in a second pass to avoid being clipped. At
     * 0.82 the difference is plain beside the full-sized head without the word looking like it is
     * dissolving.
     */
    const val TAIL_SCALE = 0.82f
}
