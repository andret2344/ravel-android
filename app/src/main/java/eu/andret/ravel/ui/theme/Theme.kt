package eu.andret.ravel.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * A fixed palette rather than dynamic colour.
 *
 * The board is read by contrast between four surfaces - open cell, wall, marked cell, found word -
 * and a wallpaper-derived scheme can collapse two of them into near-identical tones on some
 * devices. The puzzle is the product, so the puzzle gets to keep its colours.
 *
 * Both schemes are written out in full rather than left to the baseline defaults, because a
 * half-specified scheme mixes this palette's colours with Material's purples on exactly the roles
 * nobody remembered to check.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF7A4F2B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF2D9BE),
    onPrimaryContainer = Color(0xFF2C1600),
    secondary = Color(0xFF5B6350),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDDE5D0),
    onSecondaryContainer = Color(0xFF1A2013),
    background = Color(0xFFFDF8F4),
    onBackground = Color(0xFF1E1B18),
    surface = Color(0xFFFDF8F4),
    onSurface = Color(0xFF1E1B18),
    surfaceVariant = Color(0xFFEDE0D3),
    onSurfaceVariant = Color(0xFF4F453B),
    outline = Color(0xFF827568),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE9BC8C),
    onPrimary = Color(0xFF452A0F),
    primaryContainer = Color(0xFF5F3E1C),
    onPrimaryContainer = Color(0xFFFFDDBB),
    secondary = Color(0xFFC2CBB4),
    onSecondary = Color(0xFF2C3423),
    secondaryContainer = Color(0xFF434B39),
    onSecondaryContainer = Color(0xFFDDE5D0),
    background = Color(0xFF14110F),
    onBackground = Color(0xFFE9E1D9),
    surface = Color(0xFF14110F),
    onSurface = Color(0xFFE9E1D9),
    surfaceVariant = Color(0xFF4F453B),
    onSurfaceVariant = Color(0xFFD3C4B4),
    outline = Color(0xFF9C8F81),
)

/**
 * The colour a finished word wears, on the board and in the list of lengths alike.
 *
 * Material has no role for "done" - the nearest is `tertiary`, which means "an accent" and nothing
 * more - so it is declared here instead of borrowed. One colour has to carry the meaning in both
 * places: a player who learns that green means found on the board should not have to learn it
 * again for the row underneath.
 *
 * @param fill behind a found word's letters on the board.
 * @param onFill those letters.
 * @param container the quieter version, for a found length among the clues.
 * @param onContainer text on [container].
 */
@Immutable
data class FoundColors(
    val fill: Color,
    val onFill: Color,
    val container: Color,
    val onContainer: Color,
)

private val LightFound = FoundColors(
    fill = Color(0xFF2E6B4B),
    onFill = Color(0xFFFFFFFF),
    container = Color(0xFFBFE3CD),
    onContainer = Color(0xFF08331E),
)

/**
 * Lighter and less saturated than its light-theme counterpart: the same green at full strength on
 * a near-black background glares, and a board is looked at for a long time.
 */
private val DarkFound = FoundColors(
    fill = Color(0xFF4E9C74),
    onFill = Color(0xFF04240F),
    container = Color(0xFF23503A),
    onContainer = Color(0xFFBFE3CD),
)

private val LocalFoundColors = staticCompositionLocalOf { LightFound }

/** The found-word colours for the theme in force. */
val foundColors: FoundColors
    @Composable get() = LocalFoundColors.current

/**
 * The colour of the prize at the end of a week.
 *
 * Declared for the same reason as [FoundColors] and not borrowed from a Material role: nothing in
 * the scheme means "this one is worth more than the others". `tertiary` is the usual place people
 * put a colour like this, and it means "an accent" - so a week later somebody uses it for a chip
 * that is not a prize, and the seventh day stops standing out.
 *
 * Gold rather than a brighter version of the primary brown, because the seventh day has to be
 * legible as different in kind from the six leading up to it, not merely louder. It is also the one
 * place in the game where green does not mean done: a claimed seventh day stays gold, since what
 * the player earned there is the thing worth remembering about the week.
 */
@Immutable
data class GoldColors(
    val fill: Color,
    val onFill: Color,
    val container: Color,
    val onContainer: Color,
)

private val LightGold = GoldColors(
    fill = Color(0xFF9A6F0E),
    onFill = Color(0xFFFFFFFF),
    container = Color(0xFFF7E3A9),
    onContainer = Color(0xFF3D2C00),
)

/** Lighter and less saturated for the same reason as [DarkFound]: gold at full strength on a
 * near-black background reads as a light source rather than as a colour. */
private val DarkGold = GoldColors(
    fill = Color(0xFFE3BE5E),
    onFill = Color(0xFF3A2A00),
    container = Color(0xFF5A450F),
    onContainer = Color(0xFFF7E3A9),
)

private val LocalGoldColors = staticCompositionLocalOf { LightGold }

/** The prize colours for the theme in force. */
val goldColors: GoldColors
    @Composable get() = LocalGoldColors.current

@Composable
fun RavelTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalFoundColors provides if (darkTheme) DarkFound else LightFound,
        LocalGoldColors provides if (darkTheme) DarkGold else LightGold,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            content = content,
        )
    }
}
