package eu.andret.ravel.ui.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.andret.ravel.R
import eu.andret.ravel.ui.AdaptiveScreen

/**
 * Every license the game shows, on a page of its own behind one row in the options.
 *
 * They used to be two cards at the bottom of the options tab itself - six word sources, then the
 * game's code and its icons - which made the tab mostly a list of notices with a few switches above
 * it, and grew it by a row with every dictionary added. Behind one row, the tab is the settings
 * again and the notices are still two taps from anywhere, which is where players expect an app's
 * licenses to be. Nothing about the attribution itself changed: every source is still named here,
 * and each name still opens [LicenseScreen] with the notice the license asks for.
 *
 * The two groups keep their own headings, for the reason [License.wordSources] gives: the game's own
 * license and its icons listed among the word sources would be a false claim about where the words
 * come from.
 *
 * @param onLicense opens one license.
 */
@Composable
fun LicensesScreen(
    onLicense: (License) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sources: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Heading(stringResource(R.string.settings_sources))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    License.wordSources.forEach { license ->
                        SourceRow(license = license, onOpen = { onLicense(license) })
                    }
                }
            }
            Text(
                text = stringResource(R.string.settings_sources_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    val game: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Heading(stringResource(R.string.settings_license))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    License.game.forEach { license ->
                        SourceRow(license = license, onOpen = { onLicense(license) })
                    }
                }
            }
        }
    }

    AdaptiveScreen(modifier = modifier) { wide ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Scrolls in both arrangements: the sources card grows by a row with every
                // dictionary, and a license obligation does not get to be cut off by a short screen.
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            BackHeader(title = stringResource(R.string.settings_licenses_title), onBack = onBack)
            if (wide) {
                // The two groups side by side rather than one long column, since a window on its
                // side has width to spare and the sources card alone is taller than it.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) { sources() }
                    Column(modifier = Modifier.weight(1f)) { game() }
                }
            } else {
                sources()
                game()
            }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * One source: what it is called, and what it is used under.
 *
 * The license label is on the row rather than only on the page behind it, because it is the part a
 * reader is checking. Somebody who opens this list is asking "what is in here and what does it
 * oblige you to", and answering the second half only after a tap makes them open six pages to find
 * out there was nothing to worry about.
 */
@Composable
private fun SourceRow(license: License, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = stringResource(license.source), style = MaterialTheme.typography.bodyLarge)
        Text(
            text = stringResource(license.license),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
