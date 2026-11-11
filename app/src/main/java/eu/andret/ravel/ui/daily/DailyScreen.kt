package eu.andret.ravel.ui.daily

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import eu.andret.ravel.settings.ShareCard
import eu.andret.ravel.ui.CoinAmount
import eu.andret.ravel.ui.board.TraceState
import eu.andret.ravel.ui.board.rememberTraceState
import eu.andret.ravel.ui.game.BoardLayout
import eu.andret.ravel.ui.game.BoardState
import eu.andret.ravel.ui.game.BoardTopBar
import eu.andret.ravel.ui.game.BoughtLine
import eu.andret.ravel.ui.game.CluesRow
import eu.andret.ravel.ui.game.CompletionDialog
import eu.andret.ravel.ui.game.FailedBoard
import eu.andret.ravel.ui.game.GeneratingBoard
import eu.andret.ravel.ui.game.HintRow
import eu.andret.ravel.ui.game.PlayableBoard
import eu.andret.ravel.ui.game.StuckOffer
import eu.andret.ravel.ui.game.TracedWord
import eu.andret.ravel.ui.game.afterSolvedPause
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
    val share by viewModel.share.collectAsStateWithLifecycle()
    val completion by viewModel.completion.collectAsStateWithLifecycle()
    val elapsed by viewModel.elapsed.collectAsStateWithLifecycle()
    val hapticsEnabled by viewModel.hapticsEnabled.collectAsStateWithLifecycle()

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
    // Out here rather than inside the grid, because the word strip in the footer spells it too.
    val trace = rememberTraceState(puzzle?.board, hapticsEnabled)

    BoardLayout(

        onTapOffBoard = viewModel::deselect,
        modifier = modifier,
        // No confirmation on the way out any more, and no red on the button. Every move is written
        // down as it is made, so leaving a half-solved board loses nothing: the same board, with the
        // same words found and the same clock, is what opens next time. A dialog guarding against a
        // loss that cannot happen is a dialog that teaches players to dismiss dialogs.
        topBar = {
            BoardTopBar(
                balance = balance,
                elapsed = elapsed.takeIf { !viewModel.review },
                onClose = onBack,
                onShop = onShop,
            )
        },
        title = { Title(viewModel.date) },
        board = {
            when (val current = state) {
                BoardState.Generating -> GeneratingBoard()
                is BoardState.Failed -> FailedBoard(current.reason, viewModel::generate)
                is BoardState.Playing -> PlayableBoard(
                    puzzle = current.puzzle,
                    trace = trace,
                    onPathTraced = viewModel::trace,
                    onTap = viewModel::tap,
                    onTapOffGrid = viewModel::deselect,
                    hapticsEnabled = hapticsEnabled,
                )
            }
        },
        footer = {
            puzzle?.let {
                if (viewModel.review) {
                    Reviewed(puzzle = it, share = share, seconds = bought.seconds)
                } else {
                    Footer(
                        puzzle = it,
                        trace = trace,
                        bought = bought,
                        // The view model takes the coins if there are any; if there are not, the
                        // player wanted to spend and could not, which is the one moment the shop is
                        // worth showing.
                        onHint = { hint -> if (!viewModel.hint(hint)) onShop() },
                    )
                }
            }
        },
    )

    // Not on a review: a day already over cannot be stuck on, and nothing on it can be bought.
    if (!viewModel.review) StuckOffer(puzzle = puzzle, elapsed = elapsed, onShop = onShop)

    afterSolvedPause(completion)?.let {
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
 * Staying on the finished board is a button of its own as well as what the scrim and the back
 * button do - see [CompletionDialog]. One board a day makes the solved grid more worth a look here
 * than anywhere, and the level dialog offers the same choice in the same place.
 *
 * Sharing sits inside the dialog's body rather than with the buttons. Those are about this dialog
 * - stay or leave - and sharing is about the board behind it, so a third button there would be a
 * third answer to a two-answer question. It is also the moment the player is most likely to want it,
 * which is why it is here at all and not only on the calendar afterwards.
 */
@Composable
private fun CompletedDialog(completion: Completion, onExit: () -> Unit, onStay: () -> Unit) {
    CompletionDialog(
        title = stringResource(R.string.done_title),
        primary = stringResource(R.string.done_exit),
        onPrimary = onExit,
        onStay = onStay,
    ) {
        CoinAmount(
            amount = completion.total,
            text = stringResource(R.string.done_gained, completion.total),
            style = MaterialTheme.typography.displaySmall,
            color = goldColors.fill,
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Earned(stringResource(R.string.done_for_board), completion.forBoard)
            // Zero when the day was already on the record - a board being finished a second time
            // still pays for the board and cannot pay for the day twice.
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
        ShareButton(completion.card, completion.seconds)
    }
}

@Composable
private fun Earned(label: String, amount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        CoinAmount(
            amount = amount,
            text = stringResource(R.string.done_gained, amount),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
        )
    }
}

/**
 * The word being traced, the lengths still to find, the two ways to stop looking for one, and what
 * the day has cost. [Reviewed] has no word strip: a finished board has nothing left to trace.
 */
@Composable
private fun Footer(puzzle: Puzzle, trace: TraceState, bought: DayResult, onHint: (Hint) -> Unit) {
    TracedWord(puzzle, trace)
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
 *
 * The share button comes back for the one day that still has a card, the last one solved, so a
 * player who closed the completion dialog and reopened the day can still post it. It reveals what
 * the completion dialog's button does - word lengths and how each was found - and nothing of the
 * board, so having it on a reread board gives nobody else an answer.
 */
@Composable
private fun Reviewed(puzzle: Puzzle, share: ShareCard?, seconds: Int?) {
    CluesRow(puzzle.clues)
    Text(
        text = stringResource(R.string.daily_reviewing),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    share?.let { ShareButton(card = it, seconds = seconds) }
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
