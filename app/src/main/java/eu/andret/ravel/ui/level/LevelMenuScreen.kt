package eu.andret.ravel.ui.level

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.andret.ravel.R
import eu.andret.ravel.settings.LevelProgressState
import eu.andret.ravel.ui.game.BalanceButton
import eu.andret.ravel.ui.game.asClock
import eu.andret.ravel.ui.theme.foundColors

/**
 * The main mode's front page: the one level that can be played, and the run it belongs to.
 *
 * There is no list of levels to pick from, because there is no picking. The mode is a ladder walked
 * in order - a level already beaten cannot be replayed and one further up cannot be reached - so a
 * grid of taps would be a screen mostly made of things that refuse to happen. What is on offer is
 * one thing, and it gets the whole card.
 *
 * The strip under it is the same fact drawn as a position rather than a number: five behind, this
 * one, three ahead. It is not a control and nothing on it can be tapped. What it buys is the sense
 * of a road, which "Level 7" alone does not give - and it makes the rule visible without a sentence
 * explaining it, because the levels ahead are plainly there and plainly not offered.
 *
 * @param onPlay the level to open, which is only ever the current one.
 * @param onShop where the balance in the corner leads, as it does on every board.
 */
@Composable
fun LevelMenuScreen(
    onPlay: (level: Int) -> Unit,
    onShop: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LevelMenuViewModel = viewModel(),
) {
    val progress by viewModel.state.collectAsStateWithLifecycle()
    val balance by viewModel.balance.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Header(balance = balance, onShop = onShop)
        PlayCard(progress = progress, onPlay = { onPlay(progress.current) })
        Ladder(progress.current)
    }
}

@Composable
private fun Header(balance: Int, onShop: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.main_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        BalanceButton(
            text = pluralStringResource(R.plurals.coins_balance, balance, balance),
            onShop = onShop,
        )
    }
}

/**
 * The way into the level, which is two different offers wearing one card.
 *
 * - untouched: an invitation, in the primary container.
 * - part-way through: the same invitation turned up. Full primary rather than the container, a badge
 *   on the corner, the count of words already found, and a label that says finish rather than start.
 *   Three signals and not one, because the difference matters most to the player who opens the app
 *   for two minutes and needs to know at a glance whether there is something waiting.
 *
 * The level number carries the card. It is the player's position in the game and the only thing here
 * worth reading from across a room; the word under it says what tapping does, and the caption says
 * how far in they already are.
 */
@Composable
private fun PlayCard(progress: LevelProgressState, onPlay: () -> Unit) {
    val started = progress.boardFor(progress.current)
    Card(
        onClick = onPlay,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (started) {
                null -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.primary
            },
            contentColor = when (started) {
                null -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onPrimary
            },
        ),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(text = "▶", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = stringResource(R.string.level_number, progress.current),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(
                        if (started == null) R.string.level_play else R.string.level_resume,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
                // How far in, rather than a word: "2/5" is the reason to come back, and it is the
                // only thing here a player cannot already guess from the colour.
                //
                // The clock beside it is the other half of that reason. Two words out of five is a
                // different proposition after forty seconds than after nine minutes, and the time
                // already spent is the part a player coming back tomorrow will not remember.
                if (started != null) {
                    val seconds = progress.secondsFor(progress.current)
                    Text(
                        text = when {
                            // Zero while the level has been opened but not yet timed - the board
                            // reports its time when it closes - and "0:00" would read as a stopwatch
                            // that is broken rather than one that has not started.
                            seconds > 0 -> "${started.found.size}/${started.wordCount} · ${asClock(seconds)}"
                            else -> "${started.found.size}/${started.wordCount}"
                        },
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            // The notification dot, in the corner every notification dot has ever been in. It is
            // redundant with the colour and the label on purpose: this is the one thing on the
            // screen that has to survive being seen out of the corner of an eye.
            if (started != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
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

/**
 * The road: a few levels behind, the one on offer, a few ahead.
 *
 * A fixed window rather than the whole ladder, because the whole ladder has no end - the mode goes
 * on for as long as the player does - and a strip that grew by one square a level would be a strip
 * of unreadable squares by level fifty.
 *
 * The window is anchored on the current level and clipped at one, so a new player sees the road
 * starting rather than a row of blanks where their history would be. Nothing here is clickable, and
 * that is the point being made: the levels behind are done and the ones ahead are not open yet.
 */
@Composable
private fun Ladder(current: Int, modifier: Modifier = Modifier) {
    // Four behind and three ahead, which is as many as fit at a legible size across a phone.
    val first = maxOf(1, current - BEHIND)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = pluralStringResource(R.plurals.levels_done, current - 1, current - 1),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (first until first + WINDOW).forEach { level ->
                Rung(
                    level = level,
                    current = current,
                    modifier = Modifier.weight(1f).aspectRatio(1f),
                )
            }
        }
    }
}

/**
 * One square of the road, coloured by where the player stands relative to it.
 *
 * Green for done, matching every other "done" in the game. The current level is outlined as well as
 * filled, so it is findable without colour and still findable once the fill beside it is the same
 * family of brown. Everything ahead is the flat surface variant and, unlike the other two, its
 * number is dimmed - a locked level is a fact about the future, not a thing to read.
 */
@Composable
private fun Rung(level: Int, current: Int, modifier: Modifier = Modifier) {
    val done = level < current
    val now = level == current
    val description = stringResource(
        when {
            done -> R.string.level_rung_done
            now -> R.string.level_rung_current
            else -> R.string.level_rung_locked
        },
        level,
    )
    val background = when {
        done -> foundColors.container
        now -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val content = when {
        done -> foundColors.onContainer
        now -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val shape = RoundedCornerShape(10.dp)

    Box(
        modifier = modifier
            .background(background, shape)
            .border(
                width = 2.dp,
                color = if (now) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = shape,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = level.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = content,
            fontWeight = if (now) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

private const val BEHIND = 4
private const val WINDOW = 8
