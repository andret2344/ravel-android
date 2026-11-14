package eu.andret.ravel.ui.game

import android.content.ActivityNotFoundException
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import eu.andret.ravel.R
import eu.andret.ravel.game.SolvedWords
import eu.andret.ravel.game.definitionSource
import eu.andret.ravel.game.definitionUrl

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
 * The words are lowercase, as the dictionary has them, rather than in the board's capitals: this is
 * the form a dictionary page is headed with, and it is a list to look things up in, not a board.
 *
 * A row per word rather than chips in a flow: a row is a target the width of the dialog, and a
 * twelve-letter Polish word in a chip beside another one wraps unpredictably on a narrow phone. The
 * line under the list names the site, so leaving the game for the browser is never a surprise.
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
            words.words.forEach { word ->
                WordRow(word = word, url = definitionUrl(word, words.language))
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

@Composable
private fun WordRow(word: String, url: String) {
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
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = word, style = MaterialTheme.typography.bodyLarge)
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
