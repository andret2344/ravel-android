package eu.andret.ravel.ui.daily

import android.icu.text.SimpleDateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.andret.ravel.R
import eu.andret.ravel.game.DailyDate
import eu.andret.ravel.settings.DailyProgressState
import eu.andret.ravel.ui.theme.foundColors
import java.util.Date

/**
 * The month, day by day, as a record of what the player did with it.
 *
 * This is history and not a way in. A solved day opens the board it was solved on, to be looked at;
 * a missed day opens nothing, because letting a player go back and fill in a gap would make the
 * streak beside it meaningless - a run of thirty days that can be assembled on a quiet Sunday is
 * not a run of thirty days.
 *
 * Drawn as plain rows rather than a lazy grid: a month is at most six rows, all of which are on
 * screen at once, and a lazy grid inside a scrolling column has to be told a height it cannot work
 * out for itself.
 */
@Composable
internal fun CalendarCard(
    month: MonthGrid,
    progress: DailyProgressState,
    today: Long,
    selected: Long?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDay: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MonthHeader(
                month = month,
                canGoForward = !month.shifted(1).isAfter(today),
                onPrevious = onPrevious,
                onNext = onNext,
            )
            WeekdayHeader()
            Weeks(month, progress, today, selected, onDay)
        }
    }
}

@Composable
private fun MonthHeader(
    month: MonthGrid,
    canGoForward: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    // The month's own name in the player's language, from the platform, rather than twelve strings
    // per translation that would each have to be declined correctly. "LLLL" is the standalone form
    // - "sierpień", not "sierpnia" - which is what a heading needs and what "MMMM" would not give.
    val name = remember(month, locale) {
        SimpleDateFormat("LLLL yyyy", locale).format(Date(month.startMillis))
            .replaceFirstChar { it.titlecase(locale) }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The arrows are glyphs, which a screen reader would otherwise announce as punctuation, so
        // each carries a description of what it actually does.
        val previous = stringResource(R.string.calendar_previous)
        TextButton(
            onClick = onPrevious,
            modifier = Modifier.semantics { contentDescription = previous },
        ) {
            Text(text = "‹", style = MaterialTheme.typography.titleLarge)
        }
        Text(text = name, style = MaterialTheme.typography.titleMedium)
        // Kept in place rather than removed at the current month, so the header does not shuffle
        // sideways every time the player walks back through the year.
        val next = stringResource(R.string.calendar_next)
        TextButton(
            onClick = onNext,
            enabled = canGoForward,
            modifier = Modifier.semantics { contentDescription = next },
        ) {
            Text(text = "›", style = MaterialTheme.typography.titleLarge)
        }
    }
}

/** Column headings, starting on whichever day the player's locale calls the first of the week. */
@Composable
private fun WeekdayHeader() {
    val locale = LocalConfiguration.current.locales[0]
    val labels = remember(locale) { MonthGrid.weekdayLabels(locale) }
    Row(modifier = Modifier.fillMaxWidth()) {
        labels.forEach { label ->
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Weeks(
    month: MonthGrid,
    progress: DailyProgressState,
    today: Long,
    selected: Long?,
    onDay: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        (0 until month.cells step MonthGrid.WEEK).forEach { start ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (start until start + MonthGrid.WEEK).forEach { index ->
                    val ordinal = month.ordinalAt(index)
                    if (ordinal == null) {
                        // An empty cell of the same size, so the days around it keep their columns.
                        Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        DayCell(
                            ordinal = ordinal,
                            state = stateOf(ordinal, progress, today),
                            selected = ordinal == selected,
                            onClick = onDay,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** What a single day can be, which is the whole vocabulary the calendar has to draw. */
private enum class DayState {
    /** Solved, and therefore openable: the board is rebuilt from its date and shown finished. */
    SOLVED,

    /** Today, still unplayed - the one cell the player is being asked to do something about. */
    TODAY,

    /** Gone by unsolved. Nothing to open; the gap in the run is the entire message. */
    MISSED,

    /** Not yet. Drawn, because a month with its future cut off is not a month. */
    FUTURE,
}

private fun stateOf(ordinal: Long, progress: DailyProgressState, today: Long): DayState = when {
    progress.isSolved(ordinal) -> DayState.SOLVED
    ordinal == today -> DayState.TODAY
    ordinal > today -> DayState.FUTURE
    else -> DayState.MISSED
}

/**
 * One day, as a circle with the number in it.
 *
 * A solved day is filled with the same green a found word wears, so the calendar and the board
 * agree on what "done" looks like. Today is its own colour and a bold number rather than a ring,
 * because the ring is now spoken for: the ring means "this is the day the card below is talking
 * about", and two rings meaning two different things on the same grid is one ring too many.
 *
 * Nothing here leans on colour alone - the weight of the number carries the same distinctions -
 * because a month of green and grey circles is exactly the sort of thing colour blindness flattens
 * into one circle repeated.
 */
@Composable
private fun DayCell(
    ordinal: Long,
    state: DayState,
    selected: Boolean,
    onClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val day = DailyDate.day(ordinal)
    val description = stringResource(
        when (state) {
            DayState.SOLVED -> R.string.calendar_day_solved
            DayState.TODAY -> R.string.calendar_day_today
            DayState.MISSED -> R.string.calendar_day_missed
            DayState.FUTURE -> R.string.calendar_day_future
        },
        day,
    )
    val background = if (state == DayState.SOLVED) foundColors.container else Color.Transparent
    val content = when (state) {
        DayState.SOLVED -> foundColors.onContainer
        DayState.TODAY -> MaterialTheme.colorScheme.primary
        DayState.MISSED -> MaterialTheme.colorScheme.onSurfaceVariant
        DayState.FUTURE -> MaterialTheme.colorScheme.outline
    }
    val ring = if (selected) {
        Modifier.border(BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface), CircleShape)
    } else {
        Modifier
    }
    val opens = if (state == DayState.SOLVED || state == DayState.TODAY) {
        Modifier.clickable { onClick(ordinal) }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(background)
            .then(ring)
            .then(opens)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = day.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = content,
            fontWeight = when (state) {
                DayState.TODAY -> FontWeight.Bold
                DayState.FUTURE -> FontWeight.Normal
                else -> FontWeight.Medium
            },
        )
    }
}
