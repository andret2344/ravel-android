package eu.andret.ravel.ui.level

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.andret.ravel.R
import eu.andret.ravel.game.Puzzle
import eu.andret.ravel.game.Rewards
import eu.andret.ravel.ui.CoinAmount
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
import eu.andret.ravel.ui.game.StackedDialog
import eu.andret.ravel.ui.game.TracedWord
import eu.andret.ravel.ui.game.afterSolvedPause
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
 * @param onDaily offered while a slow board builds, so the wait can be spent somewhere else.
 */
@Composable
fun LevelScreen(
    onNext: (Int) -> Unit,
    onMenu: () -> Unit,
    onShop: () -> Unit,
    onDaily: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LevelViewModel = viewModel(),
) {
    val slow by viewModel.slow.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val balance by viewModel.balance.collectAsStateWithLifecycle()
    val bought by viewModel.bought.collectAsStateWithLifecycle()
    val completion by viewModel.completion.collectAsStateWithLifecycle()
    val elapsed by viewModel.elapsed.collectAsStateWithLifecycle()
    val unreachable by viewModel.unreachable.collectAsStateWithLifecycle()
    val hapticsEnabled by viewModel.hapticsEnabled.collectAsStateWithLifecycle()
    val hard by viewModel.hard.collectAsStateWithLifecycle()
    // Saveable, so turning the phone with the question open does not answer it.
    var askingToDrop by rememberSaveable { mutableStateOf(false) }
    // Out here rather than inside the grid, because the word strip in the footer spells it too.
    val trace = rememberTraceState((state as? BoardState.Playing)?.puzzle?.board, hapticsEnabled)

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

        onTapOffBoard = viewModel::deselect,
        modifier = modifier,
        // No confirmation on the way out. Every move is written down as it is made, so leaving a
        // half-solved level loses nothing: the same board, with the same words found and the same
        // clock, is what opens next time.
        topBar = {
            BoardTopBar(balance = balance, elapsed = elapsed, onClose = onMenu, onShop = onShop)
        },
        title = {
            Title(level = viewModel.level, puzzle = (state as? BoardState.Playing)?.puzzle, hard = hard)
        },
        board = {
            when (val current = state) {
                BoardState.Generating -> GeneratingBoard(slow, onDaily)
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
            (state as? BoardState.Playing)?.puzzle?.let { puzzle ->
                TracedWord(puzzle, trace)
                CluesRow(puzzle.clues, hidden = hard)
                // Under the row the lengths would be in, since that is what it brings back. It goes
                // once pressed, which grows the board by its height that one time - at a moment the
                // player chose, behind a dialog, rather than under a finger mid-trace.
                if (hard && !puzzle.isComplete) {
                    OutlinedButton(onClick = { askingToDrop = true }) {
                        Text(stringResource(R.string.hard_drop))
                    }
                }
                if (puzzle.isComplete) {
                    NextLevelButton(onClick = { onNext(viewModel.level + 1) })
                } else {
                    HintRow(
                        complete = false,
                        // The view model takes the coins if there are any; if there are not, the
                        // player wanted to spend and could not, which is the one moment the shop is
                        // worth showing.
                        onHint = { hint -> if (!viewModel.hint(hint)) onShop() },
                    )
                }
                BoughtLine(letters = bought.boughtLetters, words = bought.boughtWords)
            }
        },
    )

    if (askingToDrop && hard) {
        DropHardDialog(
            onDrop = {
                askingToDrop = false
                viewModel.dropHard()
            },
            onKeep = { askingToDrop = false },
        )
    }

    afterSolvedPause(completion)?.let {
        CompletedDialog(
            completion = it,
            onNext = {
                viewModel.dismissCompletion()
                onNext(it.level + 1)
            },
            onStay = viewModel::dismissCompletion,
        )
    }
}

/**
 * What the level paid, and the way on.
 *
 * The title carries the number of the level just beaten and the button does not carry the next
 * one: two numbers a step apart, one above the other, left the player doing the arithmetic to find
 * out which was which. "Next level" is the whole of what the button needs to say.
 *
 * There is no way back to the menu here. The ladder's front page offers exactly one thing - the
 * next level - so a menu button was the same move as the main one plus a tap, and anywhere else in
 * the game is one tap away on the tab bar or the rail. Staying on the board is offered instead; see
 * [CompletionDialog] and [NextLevelButton] for how the player carries on from there.
 */
@Composable
private fun CompletedDialog(
    completion: LevelCompletion,
    onNext: () -> Unit,
    onStay: () -> Unit,
) {
    CompletionDialog(
        title = stringResource(R.string.level_done_title, completion.level),
        primary = stringResource(R.string.level_next),
        onPrimary = onNext,
        onStay = onStay,
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
}

/**
 * Whether to give up hard mode on this level.
 *
 * Asked rather than done on the tap, because it cannot be undone and it costs coins: the button sits
 * in the footer a thumb crosses all the time. The cost is shown as the coins it takes away, through
 * [CoinAmount] like every other coin figure, and the words already found are named as staying, since
 * "lose what I have done" is the fear that would otherwise keep a stuck player from pressing it.
 */
@Composable
private fun DropHardDialog(onDrop: () -> Unit, onKeep: () -> Unit) {
    val bonus = Rewards.FOR_COMPLETING_HARD - Rewards.FOR_COMPLETING
    StackedDialog(
        title = stringResource(R.string.hard_drop_title),
        primary = stringResource(R.string.hard_drop),
        onPrimary = onDrop,
        secondary = stringResource(R.string.hard_drop_keep),
        onSecondary = onKeep,
        onDismiss = onKeep,
    ) {
        Text(
            text = stringResource(R.string.hard_drop_body),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        CoinAmount(
            amount = bonus,
            text = stringResource(R.string.coins_lost, bonus),
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

/**
 * The way on from a finished board, in the place the hint buttons had.
 *
 * Without it, a player who closed the completion dialog to look at the board - by the button, the
 * scrim or the back button - was left with greyed-out hints and a close button that leads to the
 * menu: the next level was two screens away from a board whose whole point was leading to it.
 *
 * In the hints' place rather than added under them, because a finished board has nothing left to
 * reveal, and because it keeps the footer the height it was: the board is sized from what the
 * footer leaves, and a footer that grew when the last word was found would shrink the grid under
 * the player's eyes at the moment they are looking at it.
 */
@Composable
private fun NextLevelButton(onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.level_next))
    }
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
 *
 * A hard level says so on the same line. The missing lengths are the whole of the difference, and
 * an empty row under the board reads as a bug unless something on screen says it is meant.
 */
@Composable
private fun Title(level: Int, puzzle: Puzzle?, hard: Boolean) {
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
                stringResource(
                    if (hard) R.string.level_size_hard else R.string.level_size,
                    it.board.width,
                    it.board.height,
                )
            }.orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
