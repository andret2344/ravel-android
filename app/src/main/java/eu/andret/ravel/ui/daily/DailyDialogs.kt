package eu.andret.ravel.ui.daily

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import eu.andret.ravel.R
import eu.andret.ravel.settings.DailyProgressState

/**
 * The calendar, and whichever day is selected in it, over the top of the menu.
 *
 * It moved off the menu and into a dialog when the menu became three tiles. A month grid is the
 * tallest thing the tab owns and the least often needed - a player opens this to settle a question
 * about a day gone by, not on the way to today's board - so it earns a tap rather than permanent
 * space above the fold.
 *
 * Scrollable inside, because a six-row month, a day card and a button can outgrow a short phone in
 * landscape, and a dialog that clips its own close button is a trap.
 */
@Composable
internal fun CalendarDialog(
    month: MonthGrid,
    progress: DailyProgressState,
    today: Long,
    selected: Long?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDay: (Long) -> Unit,
    onOpenBoard: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    DialogFrame(title = stringResource(R.string.tile_calendar), onDismiss = onDismiss) {
        CalendarCard(
            month = month,
            progress = progress,
            today = today,
            selected = selected,
            onPrevious = onPrevious,
            onNext = onNext,
            onDay = onDay,
        )
        DayDetailCard(
            ordinal = selected,
            result = selected?.let(progress::resultOf),
            today = today,
            onOpen = onOpenBoard,
        )
    }
}

/**
 * The shell the calendar sits in: a titled surface with a way out of it.
 *
 * A plain [Dialog] rather than an `AlertDialog`, because this is not asking a question. An alert's
 * shape - title, body, confirm, dismiss - would put a "confirm" on a calendar, and there is nothing
 * here to confirm.
 *
 * Still a shell of its own rather than folded into [CalendarDialog], because the lifetime numbers
 * used to share it and the leaderboards will want it back once they exist.
 */
@Composable
private fun DialogFrame(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = title, style = MaterialTheme.typography.titleLarge)
                content()
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.dialog_close))
                }
            }
        }
    }
}
