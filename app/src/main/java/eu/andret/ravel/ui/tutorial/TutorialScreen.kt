package eu.andret.ravel.ui.tutorial

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.andret.ravel.R
import eu.andret.ravel.ui.board.rememberTraceState
import eu.andret.ravel.ui.game.BoardLayout
import eu.andret.ravel.ui.game.CluesRow
import eu.andret.ravel.ui.game.PlayableBoard
import eu.andret.ravel.ui.game.TracedWord

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
    val puzzle by viewModel.puzzle.collectAsStateWithLifecycle()
    val hapticsEnabled by viewModel.hapticsEnabled.collectAsStateWithLifecycle()
    val trace = rememberTraceState(puzzle.board, hapticsEnabled)
    val step = TutorialViewModel.stepOf(puzzle)

    BackHandler(onBack = viewModel::finish)

    Surface(modifier = Modifier.fillMaxSize()) {
        BoardLayout(
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
            title = {
                Text(
                    text = stringResource(textOf(step)),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            board = {
                PlayableBoard(
                    puzzle = puzzle,
                    trace = trace,
                    onPathTraced = viewModel::trace,
                    onTap = viewModel::tap,
                    hapticsEnabled = hapticsEnabled,
                )
            },
            footer = {
                TracedWord(puzzle, trace)
                CluesRow(puzzle.clues)
                if (step == TutorialStep.DONE) {
                    Button(onClick = viewModel::finish) {
                        Text(stringResource(R.string.tutorial_start))
                    }
                }
            },
        )
    }
}

private fun textOf(step: TutorialStep): Int = when (step) {
    TutorialStep.FIRST -> R.string.tutorial_step_first
    TutorialStep.SECOND -> R.string.tutorial_step_second
    TutorialStep.LAST -> R.string.tutorial_step_last
    TutorialStep.DONE -> R.string.tutorial_step_done
}
