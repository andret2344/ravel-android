package eu.andret.ravel.ui.tutorial

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.andret.ravel.R
import eu.andret.ravel.ui.board.rememberTraceState
import eu.andret.ravel.ui.game.BoardLayout
import eu.andret.ravel.ui.game.CluesRow
import eu.andret.ravel.ui.game.PlayableBoard
import eu.andret.ravel.ui.game.TracedWord
import java.util.Locale

/**
 * The rules, taught on a small board the player actually plays.
 *
 * **Over the game, not instead of it.** It is drawn on top of the whole shell, tabs included, by
 * `RavelApp`, rather than being a destination in its graph: replayed from the options tab it has to
 * close back onto the options tab, and opened on the first launch it has to come before any tab at
 * all. A [Surface] filling the window also swallows every touch meant for the screen underneath.
 *
 * **The same screen as a real board.** [BoardLayout], the grid, the word strip and the length row
 * are the ones a level uses, so nothing the player learns here has to be learned again in a second
 * layout; the step text takes the place of a level's heading, against the grid.
 *
 * **Skippable at every step**, from the corner and by the back gesture alike, and skipping counts
 * as done - the player was offered it and said no, which is an answer. It is one tap away again in
 * the options tab.
 */
@Composable
fun TutorialScreen(viewModel: TutorialViewModel = viewModel()) {
    val lesson by viewModel.lesson.collectAsStateWithLifecycle()
    val hapticsEnabled by viewModel.hapticsEnabled.collectAsStateWithLifecycle()
    val puzzle = lesson.puzzle
    val trace = rememberTraceState(puzzle.board, hapticsEnabled)
    val step = lesson.step

    BackHandler(onBack = viewModel::finish)

    Surface(modifier = Modifier.fillMaxSize()) {
        BoardLayout(
            onTapOffBoard = viewModel::deselect,
            modifier = Modifier.safeDrawingPadding(),
            topBar = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.tutorial_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    // Gone on the last step, where the button under the board says the same thing
                    // in the words of having finished rather than of giving up.
                    if (step != TutorialStep.DONE) {
                        TextButton(onClick = viewModel::finish) {
                            Text(stringResource(R.string.tutorial_skip))
                        }
                    }
                }
            },
            title = { StepText(step = step, decoy = lesson.decoy.word) },
            board = {
                PlayableBoard(
                    puzzle = puzzle,
                    trace = trace,
                    onPathTraced = viewModel::trace,
                    onTap = viewModel::tap,
                    onTapOffGrid = viewModel::deselect,
                    hapticsEnabled = hapticsEnabled,
                )
            },
            footer = {
                TracedWord(puzzle, trace)
                CluesRow(puzzle.clues)
                // Laid out from the first step and only shown when there is something to press -
                // after the decoy and at the end. The board is sized from what the footer leaves, so
                // a button that arrived with the last word moved the grid up under the player's eyes;
                // held in place, nothing moves. Hidden from screen readers too until it means
                // something.
                val action = when (step) {
                    TutorialStep.DECOY_TRACED -> R.string.tutorial_next to viewModel::dismissDecoy
                    TutorialStep.DONE -> R.string.tutorial_start to viewModel::finish
                    else -> null
                }
                Button(
                    onClick = { action?.second?.invoke() },
                    enabled = action != null,
                    modifier = Modifier
                        .alpha(if (action != null) 1f else 0f)
                        .then(if (action != null) Modifier else Modifier.clearAndSetSemantics {}),
                ) {
                    Text(stringResource(action?.first ?: R.string.tutorial_start))
                }
            },
        )
    }
}

/**
 * The step's text, in a box that is the same size on every step.
 *
 * The texts run from two lines to four, and in the tall layout the board takes whatever height the
 * text leaves - so a text that grew or shrank between steps moved the grid up and down under the
 * player's finger, at exactly the moments they were looking at it for the next instruction. Every
 * step's text is laid out here, invisibly and silent to screen readers, with the current one on top:
 * the box takes the height of the longest at this width and this font scale, and keeps it.
 *
 * The cost is that the board is the size it would be on the longest step throughout, which on a 4x4
 * is little. The box itself is what makes the fixed height read as intended: a panel of constant size
 * with the text centred in it, set apart from the board and the bar, rather than a gap under a short
 * line.
 */
@Composable
private fun StepText(step: TutorialStep, decoy: String) {
    val word = decoy.uppercase(Locale.ROOT)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        TutorialStep.entries.forEach { other ->
            StepLine(
                text = textFor(other, word),
                modifier = Modifier.alpha(0f).clearAndSetSemantics {},
            )
        }
        StepLine(text = textFor(step, word))
    }
}

@Composable
private fun StepLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun textFor(step: TutorialStep, decoy: String): String = when (step) {
    TutorialStep.DECOY, TutorialStep.DECOY_TRACED -> stringResource(textOf(step), decoy)
    else -> stringResource(textOf(step))
}

/** Each step's text. The two decoy steps take the decoy's word as their one argument. */
private fun textOf(step: TutorialStep): Int = when (step) {
    TutorialStep.FIRST -> R.string.tutorial_step_first
    TutorialStep.DECOY -> R.string.tutorial_step_decoy
    TutorialStep.DECOY_TRACED -> R.string.tutorial_step_decoy_traced
    TutorialStep.SECOND -> R.string.tutorial_step_second
    TutorialStep.LAST -> R.string.tutorial_step_last
    TutorialStep.DONE -> R.string.tutorial_step_done
}
