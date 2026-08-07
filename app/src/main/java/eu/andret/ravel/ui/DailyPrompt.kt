package eu.andret.ravel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import eu.andret.ravel.R

/**
 * The question the game opens with when today's daily has not been solved.
 *
 * An `AlertDialog` and not the plain [androidx.compose.ui.window.Dialog] the daily tab uses for its
 * calendar, because this one really is asking something: it has two answers, and the shape an alert
 * gives them - the offer on the right, the refusal beside it - is the one every other app on the
 * phone has already taught the player to read.
 *
 * The checkbox is inside the dialog rather than buried in a settings screen because this is the only
 * moment the player has an opinion about it. Whoever is irritated by the prompt is irritated while
 * looking at it, and asking them to go and find a switch afterwards is asking them to be irritated
 * again tomorrow.
 *
 * @param started whether the board is half finished rather than untouched, which changes every word
 *   in the dialog. Starting something and finishing it are not the same offer, and a player who left
 *   three words on the board would not recognise "play today's puzzle" as the thing they abandoned.
 * @param remind whether the prompt is allowed to come back, ticked by default.
 * @param onRemind the player changing that, which takes effect immediately - see
 *   [RavelAppViewModel.remind].
 */
@Composable
internal fun DailyPromptDialog(
    started: Boolean,
    remind: Boolean,
    onRemind: (Boolean) -> Unit,
    onPlay: () -> Unit,
    onDismiss: () -> Unit,
) {
    val title = if (started) R.string.daily_prompt_resume_title else R.string.daily_prompt_title
    val body = if (started) R.string.daily_prompt_resume_body else R.string.daily_prompt_body
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    // The whole row toggles, not just the box: a 20 dp target beside a label that
                    // does nothing is the checkbox everyone misses twice before hitting it.
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = remind,
                            role = Role.Checkbox,
                            onValueChange = onRemind,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Null, so the row owns the click and the box is not a second target reading
                    // out the same state to a screen reader.
                    Checkbox(checked = remind, onCheckedChange = null)
                    Text(
                        text = stringResource(R.string.daily_prompt_remind),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            // The same word in both states. The title above it already says whether the board is
            // new or half done, and a button that changes its name as well makes the player read
            // the dialog twice to answer a yes-or-no question.
            TextButton(onClick = onPlay) { Text(stringResource(R.string.daily_prompt_play)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.daily_prompt_later)) }
        },
    )
}
