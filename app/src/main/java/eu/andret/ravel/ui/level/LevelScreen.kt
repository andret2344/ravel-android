package eu.andret.ravel.ui.level

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.andret.ravel.R
import eu.andret.ravel.game.LevelModifier
import eu.andret.ravel.game.Puzzle
import eu.andret.ravel.game.Rewards
import eu.andret.ravel.ui.CoinAmount
import eu.andret.ravel.ui.board.rememberTraceState
import eu.andret.ravel.ui.game.BoardLayout
import eu.andret.ravel.ui.game.BoardState
import eu.andret.ravel.ui.game.BoardTopBar
import eu.andret.ravel.ui.game.BoughtLine
import eu.andret.ravel.ui.game.CelebratedTotal
import eu.andret.ravel.ui.game.CluesRow
import eu.andret.ravel.ui.game.CompletionDialog
import eu.andret.ravel.ui.game.EarnedPart
import eu.andret.ravel.ui.game.EarnedParts
import eu.andret.ravel.ui.game.FailedBoard
import eu.andret.ravel.ui.game.GeneratingBoard
import eu.andret.ravel.ui.game.HintRow
import eu.andret.ravel.ui.game.PlayableBoard
import eu.andret.ravel.ui.game.StuckOffer
import eu.andret.ravel.ui.game.SummaryShown
import eu.andret.ravel.ui.game.TracedWord
import eu.andret.ravel.ui.game.WordList
import eu.andret.ravel.ui.game.SummaryButton
import eu.andret.ravel.ui.game.summaryToShow
import eu.andret.ravel.ui.game.asClock

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
    val summaryShown by viewModel.summaryShown.collectAsStateWithLifecycle()
    val elapsed by viewModel.elapsed.collectAsStateWithLifecycle()
    val unreachable by viewModel.unreachable.collectAsStateWithLifecycle()
    val hapticsEnabled by viewModel.hapticsEnabled.collectAsStateWithLifecycle()
    val modifiers by viewModel.modifiers.collectAsStateWithLifecycle()
    val noLengths = LevelModifier.NO_LENGTHS in modifiers
    val wordsOpen by viewModel.wordsOpen.collectAsStateWithLifecycle()
    // Saveable, so turning the phone with the question open does not answer it.
    var choosingDrop by rememberSaveable { mutableStateOf(false) }
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
            Title(level = viewModel.level, puzzle = (state as? BoardState.Playing)?.puzzle)
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
                // The owner's order: what is being traced, the lengths, the hints to buy, the hints
                // bought, then the modifiers - what is on, and the way to take it off - last, as the
                // thing least often reached for.
                TracedWord(puzzle, trace)
                CluesRow(puzzle.clues, hidden = noLengths)
                if (puzzle.isComplete) {
                    // The summary leads on to the next level as well. The direct button is left for
                    // a finished board with no summary behind it, which only a board rebuilt after
                    // its finish could be - and that one is sent back to the menu anyway.
                    if (completion != null) {
                        SummaryButton(onClick = viewModel::showSummary)
                    } else {
                        NextLevelButton(onClick = { onNext(viewModel.level + 1) })
                    }
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
                ActiveModifiers(modifiers)
                ModifiersButton(
                    enabled = modifiers.isNotEmpty() && !puzzle.isComplete,
                    onClick = { choosingDrop = true },
                )
            }
        },
    )

    StuckOffer(puzzle = (state as? BoardState.Playing)?.puzzle, elapsed = elapsed, onShop = onShop)

    if (choosingDrop && modifiers.isNotEmpty()) {
        DropModifiersDialog(
            modifiers = modifiers,
            onConfirm = { marked, alsoNext ->
                choosingDrop = false
                marked.forEach { viewModel.drop(it, alsoNext) }
            },
            onKeep = { choosingDrop = false },
        )
    }

    summaryToShow(completion, summaryShown)?.let {
        CompletedDialog(
            completion = it,
            celebrate = summaryShown == SummaryShown.AFTER_SOLVE,
            wordsOpen = wordsOpen,
            onWordsOpen = viewModel::setWordsOpen,
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
 *
 * The board's words come last, folded - see [WordList].
 */
@Composable
private fun CompletedDialog(
    completion: LevelCompletion,
    celebrate: Boolean,
    wordsOpen: Boolean,
    onWordsOpen: (Boolean) -> Unit,
    onNext: () -> Unit,
    onStay: () -> Unit,
) {
    CompletionDialog(
        title = stringResource(R.string.level_done_title, completion.level),
        primary = stringResource(R.string.level_next),
        onPrimary = onNext,
        onStay = onStay,
        celebrate = celebrate,
    ) {
        CelebratedTotal(total = completion.coins, celebrate = celebrate)
        // What for: the board, then each modifier that paid, by its icon. Nothing when the finish
        // paid nothing - a replay below the best level reached.
        if (completion.coins > 0) {
            EarnedParts(
                base = EarnedPart(stringResource(R.string.done_for_board), Rewards.FOR_COMPLETING),
                extras = LevelModifier.entries.filter { it in completion.paidFor }.map { modifier ->
                    val labels = labelsOf(modifier)
                    EarnedPart(stringResource(labels.name), modifier.bonus, labels.icon)
                },
            )
        }
        Text(
            text = asClock(completion.seconds),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        WordList(words = completion.words, open = wordsOpen, onOpenChange = onWordsOpen)
    }
}

/**
 * The modifiers this board is played with, as their icons, over the button that takes them off - so
 * the row says what that button would act on. The same icons as the levels tab's popup
 * and the dialog that takes them off, so they are already known; each is read out by name.
 *
 * **The row is always there on a level, empty with no modifiers on**, by the owner's choice: one that
 * came and went would change the board's size with the player's choice, which the footer is built not
 * to do. A fixed height rather than one taken from the icons, so it does not move with the font scale
 * either.
 */
@Composable
private fun ActiveModifiers(modifiers: Set<LevelModifier>) {
    Row(
        modifier = Modifier.fillMaxWidth().height(24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // In the enum's order, as everywhere modifiers are listed.
        LevelModifier.entries.filter { it in modifiers }.forEach { modifier ->
            val labels = labelsOf(modifier)
            Icon(
                painter = painterResource(labels.icon),
                contentDescription = stringResource(labels.name),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * The level's modifiers, under the hints: always there on a level, grayed out when there is nothing
 * left to take off - none was on, every one was taken off, or the board is finished.
 *
 * Always there, by the owner's choice, so the footer is one height on every level and the board one
 * size whatever the player chose. It replaced a "Show lengths" button that came and went with the one
 * modifier it served, growing the board when pressed, and that had no counterpart for any other
 * modifier: a player stuck under stars had no way out but hints.
 */
@Composable
private fun ModifiersButton(enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.modifiers_title))
    }
}

/**
 * The level's modifiers, each with its icon, the bonus it would cost and a way to take it off this
 * board - and one choice under them, whether what is taken off here stays off for the levels after.
 *
 * **Nothing is taken off until "OK"** (owner's choice). "Turn off" only marks a row: its name is
 * struck through and the button says "Turned off", and pressing it again takes the mark back. "OK"
 * appears once something is marked and applies every mark at once; "I'll keep trying", the scrim and
 * the back button drop the marks and change nothing. Taking a modifier off cannot be undone and costs
 * its bonus, so the player gets to change their mind until the moment they confirm - which a button
 * acting on the tap did not allow.
 *
 * The cost is the coins it takes away, through [CoinAmount] like every other coin figure; the body
 * says found words stay, since "lose what I have done" is the fear that would otherwise keep a stuck
 * player from pressing it.
 *
 * "Also for the next levels" is a checkbox rather than a second button per row: one choice about the
 * future, made once. Off by default - the player asked for a way out of this board, not to change
 * their settings - and acting from the next level opened, as the tab's choice always does.
 *
 * @param onConfirm the modifiers marked, and the checkbox.
 */
@Composable
private fun DropModifiersDialog(
    modifiers: Set<LevelModifier>,
    onConfirm: (Set<LevelModifier>, Boolean) -> Unit,
    onKeep: () -> Unit,
) {
    // Saveable, so turning the phone neither applies nor forgets the marks.
    var marked by rememberSaveable { mutableStateOf(emptyList<LevelModifier>()) }
    var alsoNext by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onKeep,
        confirmButton = {
            if (marked.isNotEmpty()) {
                TextButton(onClick = { onConfirm(marked.toSet(), alsoNext) }) {
                    Text(stringResource(R.string.modifiers_ok))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onKeep) { Text(stringResource(R.string.modifiers_drop_keep)) }
        },
        title = { Text(stringResource(R.string.modifiers_drop_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = stringResource(R.string.modifiers_drop_body), style = MaterialTheme.typography.bodyMedium)
                // In the enum's order, as everywhere modifiers are listed.
                LevelModifier.entries.filter { it in modifiers }.forEach { modifier ->
                    val labels = labelsOf(modifier)
                    val off = modifier in marked
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            painter = painterResource(labels.icon),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp).alpha(if (off) MARKED_ALPHA else 1f),
                        )
                        Column(modifier = Modifier.weight(1f).alpha(if (off) MARKED_ALPHA else 1f)) {
                            Text(
                                text = stringResource(labels.name),
                                style = MaterialTheme.typography.bodyLarge,
                                textDecoration = if (off) TextDecoration.LineThrough else null,
                            )
                            // What it pays, "+5" as on the levels tab, rather than "-5": the row says
                            // what the modifier is worth, and marking it strikes that through with
                            // the name - the loss is read off the strike, not off a minus sign.
                            CoinAmount(
                                amount = modifier.bonus,
                                text = stringResource(R.string.done_gained, modifier.bonus),
                                style = MaterialTheme.typography.labelLarge.copy(
                                    textDecoration = if (off) TextDecoration.LineThrough else null,
                                ),
                            )
                        }
                        val toggle = { marked = if (off) marked - modifier else marked + modifier }
                        if (off) {
                            // Quiet once pressed: the row has already said what will happen, and
                            // this is now the way back.
                            OutlinedButton(onClick = toggle) {
                                Text(stringResource(R.string.modifiers_dropped))
                            }
                        } else {
                            // Filled red, by the owner's choice: the one button in the dialog that
                            // costs something, in the color Material gives a destructive action.
                            Button(
                                onClick = toggle,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError,
                                ),
                            ) {
                                Text(stringResource(R.string.modifiers_drop))
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = alsoNext, role = Role.Checkbox, onValueChange = { alsoNext = it }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Drawn, not clicked: the row took the press.
                    Checkbox(checked = alsoNext, onCheckedChange = null)
                    Text(
                        text = stringResource(R.string.modifiers_drop_next),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        },
    )
}

/** A row marked to be taken off: dimmed with its strike-through, still readable. */
private const val MARKED_ALPHA = 0.6f

/**
 * The way on from a finished board, in the place the hint buttons had.
 *
 * Without it, a player who closed the completion dialog to look at the board - by the button, the
 * scrim or the back button - was left with grayed-out hints and a close button that leads to the
 * menu: the next level was two screens away from a board whose whole point was leading to it.
 *
 * In the hints' place rather than added under them, because a finished board has nothing left to
 * reveal, and because it keeps the footer the height it was: the board is sized from what the
 * footer leaves, and a footer that grew when the last word was found would shrink the grid under
 * the player's eyes at the moment they are looking at it.
 *
 * Shown only for a finished board with no summary behind it. Normally the place holds
 * [eu.andret.ravel.ui.game.SummaryButton], whose dialog leads on to the next level too and also
 * holds the board's words, which this button left out of reach.
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
 * What the level is played with is not here but under the board ([ActiveModifiers]): this line once
 * said "no lengths" when that was the only modifier, and with more than one it could only list some.
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
