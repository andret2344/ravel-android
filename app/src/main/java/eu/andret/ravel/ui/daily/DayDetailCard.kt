package eu.andret.ravel.ui.daily

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.andret.ravel.R
import eu.andret.ravel.game.DailyDate
import eu.andret.ravel.settings.DayResult
import eu.andret.ravel.ui.game.asClock
import java.util.Date

/**
 * One day out of the calendar, spelled out: what it cost, how long it took, and a way into it.
 *
 * This exists because a seven-column month cannot hold any of that. A cell is around forty-eight
 * density-independent pixels across on a phone, which is a day number and nothing else; two hint
 * counts and a duration underneath it would each land at about eight scaled pixels, which is not a
 * number anyone reads, it is a smudge that means "there was a number here".
 *
 * So the grid keeps saying the one thing it can say well - done, missed, today - and everything
 * else moves here, where there is room for it and room for whatever is added next. The time is
 * already drawn as a dash: the column is being reserved now so that the clock, when it arrives,
 * fills a gap rather than forcing this card to be laid out again.
 */
@Composable
internal fun DayDetailCard(
    ordinal: Long?,
    result: DayResult?,
    today: Long,
    onOpen: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (ordinal == null) {
                // Paging to another month drops the selection rather than leaving this card
                // describing a day that is no longer on screen above it.
                Text(
                    text = stringResource(R.string.day_pick),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Title(ordinal)
                Stats(result)
                Action(ordinal, result, today, onOpen)
            }
        }
    }
}

@Composable
private fun Title(ordinal: Long) {
    val context = LocalContext.current
    val date = remember(ordinal, context) {
        DateFormat.getLongDateFormat(context).format(Date(DailyDate.startOfDay(ordinal)))
    }
    Text(
        text = date,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
}

/**
 * Three numbers side by side, each with the noun it is a count of underneath.
 *
 * The number is large and the noun small, rather than one sentence per statistic, because the three
 * are read against each other - two letters against nine is the comparison the card is for - and a
 * column of sentences makes that a reading exercise instead of a glance.
 */
@Composable
private fun Stats(result: DayResult?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Stat(
            value = result?.boughtLetters?.toString() ?: MISSING,
            label = pluralStringResource(R.plurals.day_stat_letters, result?.boughtLetters ?: 0),
        )
        Stat(
            value = result?.boughtWords?.toString() ?: MISSING,
            label = pluralStringResource(R.plurals.day_stat_words, result?.boughtWords ?: 0),
        )
        Stat(
            value = result?.seconds?.let(::asClock) ?: MISSING_CLOCK,
            label = stringResource(R.string.day_stat_time),
        )
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.headlineSmall)
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The way into the day, or nothing at all.
 *
 * Today unplayed gets the filled button, because it is the only case where something is still at
 * stake. A solved day gets a tonal one rather than an outline: it has to stay quieter than playing,
 * but it also has to look pressable on a screen where nothing else is filled, which is exactly the
 * screen a player sees once they have solved the day. An outline reads as a button by contrast with
 * something more solid, and with nothing more solid on screen it reads as a box.
 *
 * A day that is neither is a day the calendar would not have let them select in the first place.
 */
@Composable
private fun Action(ordinal: Long, result: DayResult?, today: Long, onOpen: (Long) -> Unit) {
    when {
        result != null -> FilledTonalButton(
            onClick = { onOpen(ordinal) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.day_open_solved))
        }

        ordinal == today -> Button(
            onClick = { onOpen(ordinal) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.daily_play))
        }
    }
}

/** Not a word in any language, so it is written here rather than translated eight times. */
private const val MISSING = "–"
private const val MISSING_CLOCK = "–:––"
