package eu.andret.ravel.ui.daily

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.sp
import eu.andret.ravel.R
import eu.andret.ravel.game.Streak
import eu.andret.ravel.settings.DailyProgressState
import eu.andret.ravel.ui.theme.foundColors
import eu.andret.ravel.ui.theme.goldColors

/**
 * The run so far, the ladder it is climbing, and what today is worth.
 *
 * All seven rungs are shown at once rather than only the next one, because the seventh is the
 * reason the other six are worth doing: a player who can see the fifty four days out has a reason
 * to come back on the third day that "ten coins today" cannot give them. It is also the honest
 * version - the ladder resets, and a screen that showed one number at a time could hide that until
 * the eighth day.
 */
@Composable
internal fun StreakCard(
    progress: DailyProgressState,
    today: Long,
    modifier: Modifier = Modifier,
) {
    val solvedToday = progress.isSolved(today)

    // Which rung today is: the one just taken when the board is done, the one on offer when it is
    // not. They differ by more than one at the turn of a cycle - a seventh day played leaves the
    // player on rung seven and offers rung one - which is why this is not simply streak plus one.
    val rung = if (solvedToday) {
        Streak.dayInCycle(progress.streak)
    } else {
        Streak.nextDay(progress.streak)
    }
    val claimed = if (solvedToday) rung else rung - 1

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.streak_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // No sentence under the ladder. It used to say what today pays and how to collect it,
            // and both halves were already on screen: the outlined rung is the amount, and the play
            // tile below the card is the way to collect it. A third statement of the same thing
            // reads as filler, which is what it was.
            Ladder(claimed = claimed, rung = rung, solvedToday = solvedToday)
        }
    }
}

/**
 * The unbroken run, whole, on a card of its own above the week.
 *
 * This is the number the player is actually protecting, and it is not a fact about the week, so it
 * is not on the week's card. The ladder resets every seventh day: read alone it says a thirty-day
 * run and a three-day run are both "day two", which is the one thing about a streak that is not
 * true. Its own card fixes that without the two competing - one card answers "how long", the next
 * answers "where in the week", and neither has to be read to understand the other.
 *
 * Laid out along a line rather than stacked, because a card holding one number is otherwise mostly
 * height. The count carries the size and the noun stays small beside it, so the card is about as
 * tall as the number needs and no taller.
 *
 * Filled in the secondary family rather than left the default card colour. Two cards of the same
 * shade stacked eight points apart read as one card with a line through it, which undoes the whole
 * reason for splitting them: the surface change is what says these are two facts and not two halves
 * of one. Secondary and not primary, because primary already means "this is today's move" - it is
 * on the rung being offered and on the play tile - and this card is a report, not a move.
 *
 * The best run ever is deliberately not here. It is a number with nothing to act on and, beside the
 * current run, it mostly reports how much better things once were. It lives in the records sheet,
 * where looking it up is the point.
 */
@Composable
internal fun TotalStreakCard(streak: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            // Centred rather than on a shared baseline: the two differ by twenty points of type, and
            // a baseline would hang the small word off the bottom of the big number.
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Colours are left to the card's own content colour rather than set here. On a filled
            // surface the usual onSurfaceVariant grey is a colour from a different background, and
            // the size difference already says which of the two lines is the important one.
            if (streak == 0) {
                Text(
                    text = stringResource(R.string.streak_none),
                    style = MaterialTheme.typography.titleMedium,
                )
            } else {
                Text(
                    text = streak.toString(),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = pluralStringResource(R.plurals.streak_days, streak),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        }
    }
}

/**
 * The week as two rows of three and then the prize, across the full width.
 *
 * Seven rungs on one line left each of them about a finger wide and the card mostly empty
 * underneath - a row of small boxes under a lot of nothing. Three to a row gives every rung room to
 * be a readable size, and it turns the seventh into a shape the other six lead down to rather than
 * a slightly bigger box at the end of a queue.
 *
 * The order is still left to right and then down, which is how a week is read anywhere else.
 */
@Composable
private fun Ladder(claimed: Int, rung: Int, solvedToday: Boolean) {
    fun stateOf(day: Int) = when {
        day <= claimed -> RungState.TAKEN
        day == rung && solvedToday -> RungState.TAKEN
        day == rung -> RungState.OFFERED
        else -> RungState.AHEAD
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        listOf(1..3, 4..6).forEach { days ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                days.forEach { day ->
                    Rung(
                        day = day,
                        reward = Streak.rewardFor(day),
                        state = stateOf(day),
                        // Squarish rather than square. The gaps take the width and the ratio gives
                        // the height back, so the six read as a set of counters instead of a row of
                        // bars - and the ratio does it on any screen, where a height in dp would be
                        // right on the phone it was picked on and wrong on the next one.
                        modifier = Modifier.weight(1f).aspectRatio(1.15f),
                    )
                }
            }
        }
        Rung(
            day = Streak.CYCLE,
            reward = Streak.rewardFor(Streak.CYCLE),
            state = stateOf(Streak.CYCLE),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private enum class RungState {
    /** Already paid out in this cycle. */
    TAKEN,

    /** What today pays, once today is played. */
    OFFERED,

    /** Still to come, including the seventh - which is the whole point of drawing it. */
    AHEAD,
}

/**
 * One day of the ladder: what it pays, and where the player stands relative to it.
 *
 * Every rung is named, not only the seventh. Six identical amounts arranged two rows deep are not
 * self-evidently days one to six - the reading order says so, but only to someone who already knows
 * what they are looking at - and the name costs one small line in a box that now has the height for
 * it.
 *
 * Six identical numbers under those names is the honest picture: the first six days really are
 * interchangeable, and the whole shape of the week is that the last one is not.
 *
 * The seventh says it is different four ways at once - gold, wider, taller, larger type - because it
 * is the one thing on this card that has to survive being glanced at. Colour alone would fail a
 * colour-blind player and size alone would read as a layout accident.
 */
@Composable
private fun Rung(day: Int, reward: Int, state: RungState, modifier: Modifier = Modifier) {
    val peak = day == Streak.CYCLE
    val description = stringResource(
        when (state) {
            RungState.TAKEN -> R.string.streak_rung_taken
            RungState.OFFERED -> R.string.streak_rung_offered
            RungState.AHEAD -> R.string.streak_rung_ahead
        },
        day,
        pluralStringResource(R.plurals.coins_balance, reward, reward),
    )
    val background = when {
        // A claimed seventh day stays gold rather than turning green with the rest. Green means
        // "done" everywhere in this game, and the seventh day being done is not the interesting
        // fact about it - what the player took from it is.
        peak && state == RungState.TAKEN -> goldColors.fill
        peak -> goldColors.container
        state == RungState.TAKEN -> foundColors.container
        state == RungState.OFFERED -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val content = when {
        peak && state == RungState.TAKEN -> goldColors.onFill
        peak -> goldColors.onContainer
        state == RungState.TAKEN -> foundColors.onContainer
        state == RungState.OFFERED -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val shape = RoundedCornerShape(if (peak) 14.dp else 10.dp)
    val outline = when {
        // The day on offer is outlined as well as filled, so it is findable without colour and
        // still findable once the fill beside it is the same family of brown.
        state != RungState.OFFERED -> Color.Transparent
        peak -> goldColors.fill
        else -> MaterialTheme.colorScheme.primary
    }

    Box(
        modifier = modifier
            .background(background, shape)
            .border(2.dp, outline, shape)
            .padding(vertical = if (peak) 14.dp else 12.dp, horizontal = 4.dp)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.streak_day, day),
                style = MaterialTheme.typography.labelSmall,
                color = content,
                textAlign = TextAlign.Center,
            )
            Text(
                text = reward.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = content,
                textAlign = TextAlign.Center,
                fontSize = if (peak) 26.sp else 20.sp,
                fontWeight = if (peak) FontWeight.Bold else FontWeight.Medium,
            )
        }
    }
}
