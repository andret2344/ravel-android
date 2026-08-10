package eu.andret.ravel.ui.level

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.andret.ravel.R
import eu.andret.ravel.game.Puzzle
import eu.andret.ravel.ui.CoinAmount
import eu.andret.ravel.ui.game.BoardLayout
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

/**
 * One level's board, laid out exactly like the daily challenge's.
 *
 * The same three bands - header, board, clues and hints - and none of them scrolls, because a grid
 * that moves under a finger being dragged across it is a grid that loses the drag. What differs is
 * one line of the header and what happens at the end: a day is finished, while a level leads to the
 * next one.
 *
 * @param onNext opens the level after this one, in place of this board rather than on top of it.
 * @param onMenu goes back to the ladder.
 */
@Composable
fun LevelScreen(
    onNext: (Int) -> Unit,
    onMenu: () -> Unit,
    onShop: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LevelViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val balance by viewModel.balance.collectAsStateWithLifecycle()
    val bought by viewModel.bought.collectAsStateWithLifecycle()
    val completion by viewModel.completion.collectAsStateWithLifecycle()
    val elapsed by viewModel.elapsed.collectAsStateWithLifecycle()
    val unreachable by viewModel.unreachable.collectAsStateWithLifecycle()
    val hapticsEnabled by viewModel.hapticsEnabled.collectAsStateWithLifecycle()

    // A language switch moves the player onto that language's ladder, which this level may be far
    // ahead of - see LevelViewModel.unreachable. The board behind them is not theirs to be on, so
    // the screen closes itself rather than staying open over a level that cannot be completed.
    LaunchedEffect(unreachable) {
        if (unreachable) onMenu()
    }

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

    BoardLayout(
        modifier = modifier,
        // No confirmation on the way out. Every move is written down as it is made, so leaving a
        // half-solved level loses nothing: the same board, with the same words found and the same
        // clock, is what opens next time.
        topBar = {
            BoardTopBar(balance = balance, elapsed = elapsed, onClose = onMenu, onShop = onShop)
        },
        title = { Title(level = viewModel.level, puzzle = (state as? BoardState.Playing)?.puzzle) },
        board = {
            when (val current = state) {
                BoardState.Generating -> GeneratingBoard()
                is BoardState.Failed -> FailedBoard(current.reason, viewModel::generate)
                is BoardState.Playing -> PlayableBoard(
                    puzzle = current.puzzle,
                    onPathTraced = viewModel::trace,
                    onTap = viewModel::tap,
                    hapticsEnabled = hapticsEnabled,
                )
            }
        },
        footer = {
            (state as? BoardState.Playing)?.puzzle?.let { puzzle ->
                CluesRow(puzzle.clues)
                HintRow(
                    complete = puzzle.isComplete,
                    // The view model takes the coins if there are any; if there are not, the player
                    // wanted to spend and could not, which is the one moment the shop is worth
                    // showing.
                    onHint = { hint -> if (!viewModel.hint(hint)) onShop() },
                )
                BoughtLine(letters = bought.boughtLetters, words = bought.boughtWords)
            }
        },
    )

    completion?.let {
        CompletedDialog(
            completion = it,
            onNext = {
                viewModel.dismissCompletion()
                onNext(it.level + 1)
            },
            onMenu = {
                viewModel.dismissCompletion()
                onMenu()
            },
            onStay = viewModel::dismissCompletion,
        )
    }
}

/**
 * What the level paid, and the only two places to go from here.
 *
 * Two buttons rather than one, because a ladder has an obvious next move and a menu is not it.
 * Carrying on is the filled button on the right, where the affirmative action lives on every
 * Material dialog; the menu is the quiet one beside it, for the player who is done for the evening.
 *
 * Dismissing it without either button leaves the player on the finished board. A solved grid is
 * worth a moment's looking at, and both ways on are a tap away either way - which is also why the
 * board's own close button still goes to the menu underneath.
 */
@Composable
private fun CompletedDialog(
    completion: LevelCompletion,
    onNext: () -> Unit,
    onMenu: () -> Unit,
    onStay: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onStay,
        title = { Text(stringResource(R.string.level_done_title, completion.level)) },
        text = {
            Column(
                // Scrollable because a wide window is a short one: the dialog's body is given
                // whatever height is left after the title and the buttons, and on a phone lying on
                // its side that is not much. A body that overflows takes the buttons off the bottom
                // of the screen with it, which is a celebration the player cannot dismiss.
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CoinAmount(
                    amount = completion.coins,
                    text = stringResource(R.string.done_gained, completion.coins),
                    style = MaterialTheme.typography.displaySmall,
                    color = goldColors.fill,
                )
                Text(
                    text = asClock(completion.seconds),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = onNext) {
                Text(stringResource(R.string.level_next, completion.level + 1))
            }
        },
        dismissButton = {
            TextButton(onClick = onMenu) { Text(stringResource(R.string.level_menu)) }
        },
    )
}

/**
 * Which level this is, and how big it turned out to be.
 *
 * Bigger than the daily challenge's heading and sitting right on top of the grid, because it is not
 * the same kind of line. A date names the one board there is today; a level number is the player's
 * position in a game with no end, and it is the thing they came to this tab to see.
 *
 * The size is here rather than left for the player to count because it is the one thing about a
 * level they cannot know before opening it, and it is what the first fifteen levels are quietly
 * teaching: boards come in sizes, and a bigger one is not a harder one.
 *
 * It appears only once there is a board, and the line keeps its place while the search runs, so the
 * grid underneath does not jump when the size arrives.
 */
@Composable
private fun Title(level: Int, puzzle: Puzzle?) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.level_number, level),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = puzzle?.let {
                stringResource(R.string.level_size, it.board.width, it.board.height)
            }.orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
