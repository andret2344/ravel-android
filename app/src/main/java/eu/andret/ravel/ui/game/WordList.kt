package eu.andret.ravel.ui.game

import android.content.ActivityNotFoundException
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.andret.ravel.R
import eu.andret.ravel.game.SolvedWords
import eu.andret.ravel.game.definitionSource
import eu.andret.ravel.game.definitionUrl
import eu.andret.ravel.ui.board.BoardStyle
import eu.andret.ravel.ui.board.UNVEILED_STAR_ALPHA
import eu.andret.ravel.ui.board.UNVEILED_STAR_RATIO
import eu.andret.ravel.ui.board.UNVEILED_STAR_STROKE
import eu.andret.ravel.ui.board.drawStarLines
import eu.andret.ravel.ui.board.rememberBoardPalette

/**
 * A finished board's words, folded under one line, each opening its meaning in the browser.
 *
 * Shown in both completion dialogs and in the calendar's day card, and nowhere on a board being
 * played: the board must never scroll, and the band under it has no room to spare. A finished
 * board's words also give nothing away any more.
 *
 * **Folded by default, and remembered** - see [eu.andret.ravel.settings.WordListSettings]. The
 * dialog leads with what the board paid; the list is for the player who wants it, and a player who
 * opened it once gets it open from then on.
 *
 * **Each word is drawn as the board drew it once found**: a row of small tiles in the found word's
 * colors, capitals in the board's proportions, and a faint star behind a letter that was under one
 * (owner's choice, 2026-11-15 - the plain list read as a settings screen, not a reward). Screen
 * readers get the word itself, lowercase, as a dictionary heads it.
 *
 * **One tile size for every word in the list**, set by the longest so it fits the width, between
 * [MIN_TILE] and [MAX_TILE]: the words line up like the board they came from instead of each shrinking
 * to its own length. A word too long for even [MIN_TILE] - none in the current length band - gets
 * smaller tiles of its own rather than overflowing.
 *
 * A row per word rather than chips in a flow: a row is a target the width of the dialog. Each ends in
 * an "open in new" arrow rather than a chevron (owner's choice): a chevron promises another screen of
 * the game, and the row leaves for the browser. The line under the list names the site, so leaving
 * the game is never a surprise.
 *
 * @param open whether the list is unfolded; [onOpenChange] asks for it the other way.
 */
@Composable
internal fun WordList(
    words: SolvedWords,
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().animateContentSize()) {
        Header(count = words.words.size, open = open, onOpenChange = onOpenChange)
        if (open) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                // What a row leaves for its tiles: the chevron, the gap before it and the padding.
                val room = maxWidth - CHEVRON - GAP_BEFORE_CHEVRON - ROW_PADDING * 2
                val longest = words.words.maxOf { it.length }
                val shared = tileFor(room, longest).coerceIn(MIN_TILE, MAX_TILE)
                Column {
                    words.words.forEachIndexed { index, word ->
                        WordRow(
                            word = word,
                            hidden = words.hiddenIn(index),
                            tile = minOf(shared, tileFor(room, word.length)),
                            url = definitionUrl(word, words.language),
                        )
                    }
                }
            }
            Text(
                text = stringResource(R.string.words_source, definitionSource(words.language)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
    }
}

/**
 * The line that folds and unfolds the list.
 *
 * The chevron points down while folded and up while open, turned rather than swapped, so the one
 * glyph in the set does both and the turn itself says what the tap did. The state is spoken through
 * [stateDescription], because a screen reader cannot see which way a chevron points.
 */
@Composable
private fun Header(count: Int, open: Boolean, onOpenChange: (Boolean) -> Unit) {
    val state = stringResource(if (open) R.string.words_expanded else R.string.words_collapsed)
    val turn by animateFloatAsState(targetValue = if (open) -90f else 90f, label = "wordListChevron")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button) { onOpenChange(!open) }
            .semantics { stateDescription = state }
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.words_heading, count),
            style = MaterialTheme.typography.titleSmall,
        )
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            modifier = Modifier.rotate(turn),
        )
    }
}

/** The side of [count] tiles laid edge to edge, with their gaps, across [room]. */
private fun tileFor(room: Dp, count: Int): Dp = (room - TILE_GAP * (count - 1)) / count

@Composable
private fun WordRow(word: String, hidden: Set<Int>, tile: Dp, url: String) {
    val uriHandler = LocalUriHandler.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClickLabel = stringResource(R.string.words_look_up), role = Role.Button) {
                // A phone with no browser at all is rare and real - a work profile, a kiosk build.
                // The tap then does nothing rather than taking the game down with it. Both
                // exceptions, because which one arrives depends on the Compose version: older
                // ones let the system's through, newer ones wrap it.
                try {
                    uriHandler.openUri(url)
                } catch (noBrowser: ActivityNotFoundException) {
                    // Nothing to open it with; see above.
                } catch (noBrowser: IllegalArgumentException) {
                    // The same, wrapped.
                }
            }
            .semantics(mergeDescendants = true) { contentDescription = word }
            .padding(horizontal = ROW_PADDING, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GAP_BEFORE_CHEVRON),
    ) {
        Row(
            modifier = Modifier.weight(1f).clearAndSetSemantics {},
            horizontalArrangement = Arrangement.spacedBy(TILE_GAP),
        ) {
            word.forEachIndexed { position, letter ->
                WordTile(letter = letter.uppercaseChar(), starred = position in hidden, side = tile)
            }
        }
        Icon(
            painter = painterResource(R.drawable.ic_open_external),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(CHEVRON),
        )
    }
}

/** One letter of a found word, in the board's colors and proportions. */
@Composable
private fun WordTile(letter: Char, starred: Boolean, side: Dp) {
    val palette = rememberBoardPalette()
    Box(
        modifier = Modifier
            .size(side)
            .background(palette.solved, RoundedCornerShape(side * BoardStyle.CORNER_RATIO))
            .drawBehind {
                if (starred) {
                    drawStarLines(
                        center = center,
                        size = size.minDimension * UNVEILED_STAR_RATIO,
                        color = palette.solvedLetter.copy(alpha = UNVEILED_STAR_ALPHA),
                        stroke = UNVEILED_STAR_STROKE,
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter.toString(),
            color = palette.solvedLetter,
            style = TextStyle(
                fontSize = with(LocalDensity.current) { (side * BoardStyle.FONT_RATIO).toSp() },
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

/** The list's tiles never grow past this, however short the words. */
private val MAX_TILE = 28.dp

/** Nor shrink below this to make room for the longest - see the class note. */
private val MIN_TILE = 18.dp

private val TILE_GAP = 2.dp
private val CHEVRON = 24.dp
private val GAP_BEFORE_CHEVRON = 8.dp
private val ROW_PADDING = 4.dp
