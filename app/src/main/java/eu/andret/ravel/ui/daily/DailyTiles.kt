package eu.andret.ravel.ui.daily

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.andret.ravel.R
import eu.andret.ravel.settings.SavedBoard
import eu.andret.ravel.ui.theme.foundColors

/**
 * The three things the daily tab can do, as three squares.
 *
 * Tiles rather than a column of full-width buttons and an inline calendar, because the tab now owns
 * more than one idea and a stack of them makes the screen a list to be read top to bottom every
 * time. Three squares are read once and then aimed at.
 *
 * Playing sits in the middle and is the only filled tile, so the hierarchy is carried by position
 * and colour rather than by size. Making it bigger would break the grid, and leaving all three the
 * same would leave the primary action of the whole tab looking like a menu entry.
 */
@Composable
internal fun DailyTiles(
    solvedToday: Boolean,
    started: SavedBoard?,
    monthName: String,
    solvedThisMonth: Int,
    onPlay: () -> Unit,
    onCalendar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Tile(
            glyph = "▦",
            label = stringResource(R.string.tile_calendar),
            // The month's own name, so the tile says which month the count belongs to without
            // needing a sentence for it.
            caption = "$solvedThisMonth · $monthName",
            container = MaterialTheme.colorScheme.surfaceVariant,
            content = MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = onCalendar,
            modifier = Modifier.weight(1f),
        )
        PlayTile(solvedToday = solvedToday, started = started, onClick = onPlay, modifier = Modifier.weight(1f))
        LockedTile(
            label = stringResource(R.string.tile_leaderboards),
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * A tile for something that is coming and is not here: a padlock, a name, and no tap.
 *
 * Shown rather than left out, on the same argument as the shop's unbuilt sources. The leaderboards
 * are the reason the daily mode times anything at all, and a player who cannot see that they are on
 * the way has no reason to care how long a board took them.
 *
 * Not a disabled [Card] with a click that does nothing: it takes no [onClick] at all, so nothing
 * about it can be pressed, ripple or otherwise. The padlock says why, the dimmed content says the
 * same thing again without needing the glyph to be understood, and "soon" answers the question both
 * of them raise.
 */
@Composable
private fun LockedTile(label: String, modifier: Modifier = Modifier) {
    val content = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = LOCKED_ALPHA)
    Card(
        modifier = modifier.aspectRatio(1f),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = content,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The one drawn icon on the screen, and R.drawable.ic_lock says why it is drawn at all.
            // No description: the label under it already names the tile, and "locked" is said by the
            // caption rather than by an icon a screen reader would read out as a second heading.
            Icon(
                painter = painterResource(R.drawable.ic_lock),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.tile_locked_caption),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Material's own disabled level, so a locked tile reads as disabled and not as badly coloured. */
private const val LOCKED_ALPHA = 0.38f

/**
 * The way into today's board, which is three different offers wearing one square.
 *
 * - untouched: an invitation, in the primary container.
 * - part-way through: the same invitation turned up. Full primary rather than the container, a
 *   badge on the corner, the count of words already found, and a label that says finish rather than
 *   start. Three signals and not one, because the difference matters most to the player who opens
 *   the app for two minutes and needs to know at a glance whether there is something waiting.
 * - done: green and a tick, matching every other "done" in the game.
 *
 * The unfinished state is the loudest of the three on purpose. Something is owed, and it is the one
 * state a player can be in without remembering that they are in it.
 */
@Composable
private fun PlayTile(
    solvedToday: Boolean,
    started: SavedBoard?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glyph = when {
        // A tick, not an arrow, once the day is done: the tile still opens the board, but what it
        // is mostly doing at that point is reporting.
        solvedToday -> "✓"
        else -> "▶"
    }
    val container = when {
        solvedToday -> foundColors.container
        started != null -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val content = when {
        solvedToday -> foundColors.onContainer
        started != null -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    val label = when {
        solvedToday -> R.string.tile_play_done
        started != null -> R.string.tile_play_resume
        else -> R.string.tile_play
    }
    Tile(
        glyph = glyph,
        label = stringResource(label),
        // How far in, rather than a word: "2/5" is the reason to go back, and it is the only thing
        // here that a player cannot already guess from the colour.
        caption = when {
            solvedToday -> stringResource(R.string.tile_play_done_caption)
            started != null -> "${started.found.size}/${started.wordCount}"
            else -> stringResource(R.string.tile_play_caption)
        },
        container = container,
        content = content,
        badge = started != null && !solvedToday,
        onClick = onClick,
        modifier = modifier,
    )
}

/**
 * One square: a glyph, what it opens, and one number about it.
 *
 * The number is the point of putting these on the front screen at all. A tile that only said
 * "Calendar" would be a link, and a link is worth a tap only after the player has decided to look;
 * "5 · August" is worth a glance before they have decided anything, and sometimes answers the
 * question without the tap.
 */
@Composable
private fun Tile(
    glyph: String,
    label: String,
    caption: String,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: Boolean = false,
) {
    Card(
        onClick = onClick,
        modifier = modifier.aspectRatio(1f),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                // Fills the square so the three lines sit in the middle of it rather than at the
                // top.
                modifier = Modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = glyph, style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                )
            }
            // The notification dot, in the corner every notification dot has ever been in. It is
            // redundant with the colour and the label on purpose: this is the one thing on the
            // screen that has to survive being seen out of the corner of an eye.
            //
            // Ringed in the page's own background rather than dropped straight onto the tile. Red
            // and the tile's brown sit on the same side of the lightness scale in both themes -
            // dark red on dark brown, then pale pink on pale tan - so the dot alone disappears
            // whichever way the phone is set. A ring in the one colour that is always opposite the
            // tile separates it from the tile without having to know what the tile is.
            if (badge) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(14.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .background(MaterialTheme.colorScheme.error, CircleShape),
                    )
                }
            }
        }
    }
}
