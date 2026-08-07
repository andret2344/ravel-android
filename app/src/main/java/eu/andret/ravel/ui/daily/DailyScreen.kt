package eu.andret.ravel.ui.daily

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.andret.ravel.R
import eu.andret.ravel.game.Hint
import eu.andret.ravel.game.Puzzle
import eu.andret.ravel.settings.DayResult
import eu.andret.ravel.ui.game.BoardState
import eu.andret.ravel.ui.game.BoardTopBar
import eu.andret.ravel.ui.game.BoughtLine
import eu.andret.ravel.ui.game.CluesRow
import eu.andret.ravel.ui.game.FailedBoard
import eu.andret.ravel.ui.game.GeneratingBoard
import eu.andret.ravel.ui.game.HintRow
import eu.andret.ravel.ui.game.PlayableBoard
import eu.andret.ravel.ui.game.asClock
import eu.andret.ravel.ui.theme.goldColors
import java.util.Date

/**
 * One day's board: today's to be solved, or an earlier one to be looked at.
 *
 * Laid out in three bands - header, board, clues and hint - with only the board's band flexible, so
 * the screen never scrolls. The board fits itself into whatever is left over, which is what makes
 * that safe on a short phone as much as on a tall one.
 *
 * Reached from [DailyMenuScreen] rather than being the tab itself, so the generator runs when a
 * player asks for a board and not every time they glance at their streak.
 */
@Composable
fun DailyScreen(
    onBack: () -> Unit,
    onShop: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DailyViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val balance by viewModel.balance.collectAsStateWithLifecycle()
    val bought by viewModel.bought.collectAsStateWithLifecycle()
    val completion by viewModel.completion.collectAsStateWithLifecycle()
    val elapsed by viewModel.elapsed.collectAsStateWithLifecycle()

    /*
     * The clock runs while the board is on screen and stops when it is not.
     *
     * ON_START and ON_STOP rather than resume and pause, because pause fires for things that leave
     * the player looking at the board - a system dialog over the top, the other half of a split
     * screen taking focus - and time spent looking at a board is time spent on it.
     */
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.resumeClock()
                Lifecycle.Event.ON_STOP -> viewModel.pauseClock()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            // Navigating away is not a lifecycle event - the activity never stops - so the clock
            // has to be stopped by the screen going away as well as by the app doing so.
            viewModel.pauseClock()
        }
    }

    val puzzle = (state as? BoardState.Playing)?.puzzle

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // No confirmation on the way out any more, and no red on the button. Every move is written
        // down as it is made, so leaving a half-solved board loses nothing: the same board, with the
        // same words found and the same clock, is what opens next time. A dialog guarding against a
        // loss that cannot happen is a dialog that teaches players to dismiss dialogs.
        BoardTopBar(
            balance = balance,
            elapsed = elapsed.takeIf { !viewModel.review },
            onClose = onBack,
            onShop = onShop,
        )
        Title(viewModel.date)

        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            when (val current = state) {
                BoardState.Generating -> GeneratingBoard()
                is BoardState.Failed -> FailedBoard(current.reason, viewModel::generate)
                is BoardState.Playing -> PlayableBoard(
                    puzzle = current.puzzle,
                    onPathTraced = viewModel::trace,
                    onTap = viewModel::tap,
                )
            }
        }

        puzzle?.let {
            if (viewModel.review) {
                Reviewed(it)
            } else {
                Footer(
                    puzzle = it,
                    bought = bought,
                    // The view model takes the coins if there are any; if there are not, the player
                    // wanted to spend and could not, which is the one moment the shop is worth
                    // showing.
                    onHint = { hint -> if (!viewModel.hint(hint)) onShop() },
                )
            }
        }
    }

    completion?.let {
        CompletedDialog(
            completion = it,
            onExit = {
                viewModel.dismissCompletion()
                onBack()
            },
            onStay = viewModel::dismissCompletion,
        )
    }
}

/**
 * What the board just paid, and the way out.
 *
 * It leads with the total and breaks it down underneath, because the total is the answer to the
 * question the player is actually asking and the breakdown is the answer to "why that much" - which
 * only some players ask, and never first.
 *
 * Dismissing it without the button leaves the player on the finished board rather than backing out
 * for them. A solved grid is worth a moment's looking at, and the way out is a tap away either way.
 */
@Composable
private fun CompletedDialog(completion: Completion, onExit: () -> Unit, onStay: () -> Unit) {
    AlertDialog(
        onDismissRequest = onStay,
        title = { Text(stringResource(R.string.done_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.done_gained, completion.total),
                    style = MaterialTheme.typography.displaySmall,
                    color = goldColors.fill,
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Earned(stringResource(R.string.done_for_board), completion.forBoard)
                    // Zero when the day was already on the record - a board being finished a second
                    // time still pays for the board and cannot pay for the day twice.
                    if (completion.forStreak > 0) {
                        Earned(
                            label = stringResource(R.string.streak_day, completion.cycleDay),
                            amount = completion.forStreak,
                        )
                    }
                }
                Text(
                    text = buildString {
                        if (completion.streak > 0) {
                            append(completion.streak)
                            append(' ')
                            append(pluralStringResource(R.plurals.streak_days, completion.streak))
                            append(" · ")
                        }
                        append(asClock(completion.seconds))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = onExit) { Text(stringResource(R.string.done_exit)) }
        },
    )
}

@Composable
private fun Earned(label: String, amount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = stringResource(R.string.done_gained, amount),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** The lengths still to find, the two ways to stop looking for one, and what the day has cost. */
@Composable
private fun Footer(puzzle: Puzzle, bought: DayResult, onHint: (Hint) -> Unit) {
    CluesRow(puzzle.clues)
    HintRow(complete = puzzle.isComplete, onHint = onHint)
    // The day's tally and not the board's: the board is thrown away every time this screen closes
    // and the day is not, so counting the board would let a player buy a word, leave, and come back
    // to a counter at zero.
    BoughtLine(letters = bought.boughtLetters, words = bought.boughtWords)
}

/**
 * The same band under a board being reread, with the prices taken out of it.
 *
 * The lengths stay, because they are what the board is measured against and the row is the shape
 * the player remembers. The hints go, because there is nothing left to reveal and a price on a
 * finished board is an invitation to spend on nothing.
 */
@Composable
private fun Reviewed(puzzle: Puzzle) {
    CluesRow(puzzle.clues)
    Text(
        text = stringResource(R.string.daily_reviewing),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * What board this is, under the bar and centred.
 *
 * Below the controls rather than among them, because it is read once on arrival and never again -
 * and because centring it here costs nothing, where centring it on the bar would have needed a
 * spacer as wide as the close button on the other side.
 */
@Composable
private fun Title(dateMillis: Long) {
    val context = LocalContext.current
    // The phone's own date format, so the header reads the way every other date on the device does.
    val date = remember(dateMillis, context) {
        DateFormat.getLongDateFormat(context).format(Date(dateMillis))
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.daily_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = date,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
