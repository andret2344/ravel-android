package eu.andret.ravel.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.andret.ravel.R
import eu.andret.ravel.ui.AdaptiveScreen
import eu.andret.ravel.ui.game.asDuration

/**
 * How the player is doing, measured against nobody.
 *
 * This used to be a dialog over the daily menu, opened from a tile called "Records". It is a tab now
 * because those were two different things sharing one screen: what a player has done is theirs and
 * always available, while a record is a comparison against other people - and that belongs to the
 * leaderboards, which are still a locked tile.
 *
 * Tiles rather than the rows it was: a row of label and value is a table, and a table is read from
 * the top down every time. These are read by glancing at whichever number the player came for. The
 * number is the loud part and the label under it is the small print, which is the opposite of how
 * the rows had it.
 *
 * Grouped rather than listed, because the three groups answer three different questions - how the
 * calendar is going, what has been paid for hints, how much has been played in total - and the last
 * of them is the only one that counts both modes together. A single ungrouped grid would silently
 * mix "daily only" numbers with "everything" ones, and nothing on screen would say which was which.
 */
@Composable
fun StatsScreen(modifier: Modifier = Modifier, viewModel: StatsViewModel = viewModel()) {
    val stats by viewModel.state.collectAsStateWithLifecycle()

    val daily: @Composable () -> Unit = {
        Section(stringResource(R.string.stats_section_daily)) {
            StatTile(
                value = stats.days.toString(),
                label = stringResource(R.string.stats_solved),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            // The best run, and not the current one. The streak the player is on is the daily tab's
            // whole front page - a ladder, a count and a promise about tomorrow - and repeating it
            // here would make this screen a worse copy of that one. What this screen can say that
            // the daily tab cannot is what the player has ever managed.
            StatTile(
                value = stats.longest.toString(),
                label = stringResource(R.string.stats_longest),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
    val hints: @Composable () -> Unit = {
        Section(stringResource(R.string.stats_section_hints)) {
            StatTile(
                value = stats.boughtLetters.toString(),
                label = stringResource(R.string.stats_letters),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            StatTile(
                value = stats.boughtWords.toString(),
                label = stringResource(R.string.stats_words),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
    val total: @Composable () -> Unit = {
        Section(stringResource(R.string.stats_section_total)) {
            StatTile(
                value = stats.boards.toString(),
                label = stringResource(R.string.stats_boards),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            StatTile(
                value = stats.wordsFound.toString(),
                label = stringResource(R.string.stats_found),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
    // The wide one, alone on its line and in the filled colour. Time is the total the other two are
    // measured in - boards and words are what the hours went on - and a clock squeezed into a third
    // of a row is also the one value here that can outgrow its tile.
    val time: @Composable () -> Unit = {
        StatTile(
            value = asDuration(stats.seconds),
            label = stringResource(R.string.stats_total_time),
            container = MaterialTheme.colorScheme.primaryContainer,
            content = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    // The one thing a number on this screen cannot say for itself. The main mode is a separate
    // ladder per language, so a player halfway through two of them has two current levels and one
    // history - and the tiles above are the history. Without this line the count of boards reads as
    // the count for the language they happen to be playing, which is the number the game tab shows
    // and a different thing entirely.
    val note: @Composable () -> Unit = {
        Text(
            text = stringResource(R.string.stats_all_languages),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    AdaptiveScreen(modifier = modifier) { wide ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = stringResource(R.string.tab_stats),
                style = MaterialTheme.typography.headlineSmall,
            )
            if (wide) {
                /*
                 * Two columns of groups, and never one row of six tiles.
                 *
                 * The groups are the whole structure of this screen - three questions, and only the
                 * last of them counts both modes - so the thing that has to survive a wide window is
                 * which tile belongs to which heading. Six tiles across would fit and would read as
                 * one undivided row of numbers under three headings nobody can attach them to, which
                 * is exactly the failure the grouping exists to prevent.
                 *
                 * The split is two groups against one, not one and a half each: the total group
                 * carries the time tile and the caption, which makes it as tall as the two short
                 * groups stacked. Both columns therefore end at about the same line without either
                 * being padded to get there.
                 */
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        daily()
                        hints()
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        total()
                        time()
                        note()
                    }
                }
            } else {
                daily()
                hints()
                total()
                time()
                note()
            }
        }
    }
}

/**
 * A titled row of tiles, which is the only shape this screen has.
 *
 * The row is measured at [IntrinsicSize.Min] and its tiles fill that height, so both squares are as
 * tall as the taller one. Without it a tile carrying a caption stands a line higher than the tile
 * beside it, and two cards of different heights read as two kinds of thing rather than as one pair -
 * the eye takes the taller one for the more important one, which is not what the extra line means.
 */
@Composable
private fun Section(title: String, tiles: @Composable RowScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = tiles,
        )
    }
}

/**
 * One number, what it counts, and sometimes a second number that qualifies it.
 *
 * Not square, unlike the daily tab's tiles. Those are buttons and a square is a target; this is a
 * readout, and a square would be an invitation to tap something that does nothing.
 */
@Composable
private fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    container: Color = MaterialTheme.colorScheme.surfaceVariant,
    content: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Column(
            // Fills whatever height the row settled on, and centres what is in it. A tile stretched
            // to match its neighbour would otherwise hang its number from the top and leave the
            // spare line as a gap underneath, which looks like a missing caption rather than a
            // tile that had none.
            modifier = Modifier.fillMaxSize().padding(vertical = 16.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
            )
            if (caption != null) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
