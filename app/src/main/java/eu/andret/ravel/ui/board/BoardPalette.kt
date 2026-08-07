package eu.andret.ravel.ui.board

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import eu.andret.ravel.ui.theme.foundColors

/**
 * Every colour the board draws with, resolved once instead of at each of a hundred-odd cells.
 *
 * It also keeps the theme out of the drawing code: a painter that reached for `MaterialTheme` would
 * have to be a composable, and a composable cannot be the thing that runs inside a draw pass.
 */
@Immutable
internal class BoardPalette(
    val cell: Color,
    val wall: Color,
    val letter: Color,
    val solved: Color,
    val solvedLetter: Color,
    val marked: Color,
    val markedLetter: Color,
    val hint: Color,
)

@Composable
internal fun rememberBoardPalette(): BoardPalette {
    val scheme = MaterialTheme.colorScheme
    val found = foundColors
    return remember(scheme, found) {
        BoardPalette(
            cell = scheme.surfaceVariant,
            wall = scheme.outline.copy(alpha = WALL_ALPHA),
            letter = scheme.onSurface,
            solved = found.fill,
            solvedLetter = found.onFill,
            marked = scheme.secondary,
            markedLetter = scheme.onSecondary,
            hint = scheme.primary,
        )
    }
}

/** A wall is a hole in the board, not a feature of it, so it sits back rather than stands out. */
private const val WALL_ALPHA = 0.35f
