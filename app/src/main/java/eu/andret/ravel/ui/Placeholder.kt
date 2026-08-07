package eu.andret.ravel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.andret.ravel.R

/**
 * A tab that exists on the bar before it exists as a screen: its name, and the admission that there
 * is nothing behind it yet.
 *
 * Deliberately one file for every such tab rather than an empty screen per package. This is scaffold
 * and it is meant to be deleted: when the statistics screen is written it goes in its own package
 * and stops calling this, and when the last placeholder is gone so is this file. An empty
 * `StatsScreen` in `ui/stats` would instead look like the beginning of the real thing, and the two
 * are hard to tell apart six weeks later.
 *
 * It says "coming soon" rather than nothing for the same reason the shop's unbuilt sources do: a
 * player who taps a tab and gets a blank page has found a bug, and one who gets a promise has found
 * a plan.
 */
@Composable
internal fun Placeholder(title: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
        Text(
            text = stringResource(R.string.shop_coming_soon),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
