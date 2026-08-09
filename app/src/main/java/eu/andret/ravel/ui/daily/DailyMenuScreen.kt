package eu.andret.ravel.ui.daily

import android.icu.text.SimpleDateFormat
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.andret.ravel.R
import eu.andret.ravel.game.DailyDate
import eu.andret.ravel.ui.game.BalanceButton
import java.util.Date

/**
 * What the daily tab opens on: the week's run, and three ways into the rest of the mode.
 *
 * A menu in front of the board rather than the board itself, because the board is not the only
 * thing the mode is now about. A streak is a promise the game makes about tomorrow, and a promise
 * the player only sees after solving a puzzle is one they will break before they have read it.
 * Putting it first also means the generator no longer runs on every visit to the tab - it runs when
 * the player says they want to play.
 *
 * Everything below the streak is a tile, and the two heavier things - the month, the lifetime
 * numbers - open over the top rather than sitting on the page. The tab is opened most often to do
 * one thing, and that thing should not be at the bottom of a scroll.
 *
 * @param onOpen the day to open and whether it is being reviewed rather than played.
 * @param onShop where the balance in the corner leads, as it does on every board.
 */
@Composable
fun DailyMenuScreen(
    onOpen: (ordinal: Long, solved: Boolean) -> Unit,
    onShop: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DailyMenuViewModel = viewModel(),
) {
    val progress by viewModel.state.collectAsStateWithLifecycle()
    val month by viewModel.month.collectAsStateWithLifecycle()
    val balance by viewModel.balance.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val today = viewModel.today
    val solvedToday = progress.isSolved(today)

    // Whether the calendar is over the menu. Kept here and not in the view model: nothing outside
    // this screen can open it, and nothing about the game changes while it is up.
    var calendarOpen by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Header(today = today, balance = balance, onShop = onShop)
        // The two streak cards sit closer to each other than to anything else: they are one subject
        // split across two cards, and the outer spacing would read them as unrelated.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TotalStreakCard(streak = progress.streak)
            StreakCard(progress = progress, today = today)
        }
        DailyTiles(
            // A wider gap than the card spacing, so the tiles read as a separate band rather than
            // as one more card in the same stack. The streak is a report; these are controls.
            modifier = Modifier.padding(top = 12.dp),
            solvedToday = solvedToday,
            started = progress.boardFor(today).takeIf { !solvedToday },
            monthName = monthName(month),
            solvedThisMonth = progress.solved.keys.count(month::contains),
            onPlay = { onOpen(today, solvedToday) },
            onCalendar = { calendarOpen = true },
        )
    }

    if (calendarOpen) {
        CalendarDialog(
            month = month,
            progress = progress,
            today = today,
            selected = selected,
            onPrevious = viewModel::showPreviousMonth,
            onNext = viewModel::showNextMonth,
            onDay = viewModel::select,
            onOpenBoard = { ordinal ->
                // The dialog closes on the way out, so coming back from the board lands on the
                // menu rather than on a month grid the player has finished with.
                calendarOpen = false
                // A day the card offers is either solved history or today itself; anything else was
                // never selectable, so "not today" is enough to know this is a review.
                onOpen(ordinal, ordinal != today || solvedToday)
            },
            onDismiss = { calendarOpen = false },
        )
    }
}

/** The month's own name, for the calendar tile to caption its count with. */
@Composable
private fun monthName(month: MonthGrid): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(month, locale) {
        SimpleDateFormat("LLLL", locale).format(Date(month.startMillis))
    }
}

@Composable
private fun Header(today: Long, balance: Int, onShop: () -> Unit) {
    val context = LocalContext.current
    // The phone's own date format, so the header reads the way every other date on the device does.
    val date = remember(today, context) {
        DateFormat.getLongDateFormat(context).format(Date(DailyDate.startOfDay(today)))
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = stringResource(R.string.daily_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = date,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BalanceButton(balance = balance, onShop = onShop)
    }
}
