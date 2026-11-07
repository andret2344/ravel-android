package eu.andret.ravel.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import eu.andret.ravel.R
import kotlinx.coroutines.delay

/**
 * How long a finished board stays in view before what it paid is put over it.
 *
 * The dialog used to arrive on the very frame the last word was found, which meant the scrim went
 * over the board at the one moment it was worth looking at: every word in place, the board's own
 * haptic still going. The player never saw what they had made. Three quarters of a second is long
 * enough to take that in and short enough not to read as the game hanging - nothing on the board
 * can change any more by then, so there is nothing for the wait to be confused with.
 */
private const val SOLVED_PAUSE_MILLIS = 750L

/**
 * [value], held back for [SOLVED_PAUSE_MILLIS] each time it turns up.
 *
 * Only the showing waits. The coins and the record are written when the word is found, as before,
 * so a player who leaves - or whose process is killed - inside the pause has lost the summary and
 * nothing else. Going away is immediate, so dismissing never lags.
 *
 * Remembered rather than saved, so turning the phone inside the pause, or with the dialog open,
 * starts the pause again. That costs one more look at a finished board, which is what the pause is
 * for anyway.
 */
@Composable
internal fun <T : Any> afterSolvedPause(value: T?): T? {
    var shown by remember { mutableStateOf<T?>(null) }
    LaunchedEffect(value) {
        if (value != null) delay(SOLVED_PAUSE_MILLIS)
        shown = value
    }
    return shown
}

/**
 * The dialog a finished board shows, in either mode: a title, what the board paid, and the ways on.
 *
 * [onStay] is the bottom button, a tap on the scrim and the back button alike. All three leave the
 * player on the finished board, which is worth a look; the button is there so that looking is a
 * choice on offer and not only something that happens to a player who missed the dialog.
 *
 * @param primary the label of the way on - the next level, or done for the day.
 */
@Composable
internal fun CompletionDialog(
    title: String,
    primary: String,
    onPrimary: () -> Unit,
    onStay: () -> Unit,
    body: @Composable ColumnScope.() -> Unit,
) {
    StackedDialog(
        title = title,
        primary = primary,
        onPrimary = onPrimary,
        secondary = stringResource(R.string.done_view_board),
        onSecondary = onStay,
        onDismiss = onStay,
        body = body,
    )
}

/**
 * A dialog with its two buttons one above the other, full width.
 *
 * ## Why not an `AlertDialog`
 *
 * An alert lays its buttons out in a row at the end, and the labels in this game do not fit in one:
 * "Następny poziom" and "Zobacz planszę" side by side need about 290dp against roughly 264dp on a
 * 360dp phone, so the row wraps into two right-aligned lines that look like a layout bug. Stacked,
 * each button has the dialog's full width whatever the language makes of its label, and the one the
 * player is most likely to want is the wide one at the top, under the thumb.
 *
 * Built from the alert's own defaults - shape, colours, elevation, width - so it looks like the alert
 * it replaced and like every other dialog Material draws.
 *
 * The body scrolls and the buttons do not, because a wide window is a short one: on a phone on its
 * side the body gets whatever height the title and the buttons leave, and a body that overflowed
 * would take the buttons off the bottom of the screen with it.
 *
 * @param onDismiss the scrim and the back button.
 */
@Composable
internal fun StackedDialog(
    title: String,
    primary: String,
    onPrimary: () -> Unit,
    secondary: String,
    onSecondary: () -> Unit,
    onDismiss: () -> Unit,
    body: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.sizeIn(minWidth = 280.dp, maxWidth = 560.dp),
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = AlertDialogDefaults.titleContentColor,
                )
                CompositionLocalProvider(LocalContentColor provides AlertDialogDefaults.textContentColor) {
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        content = body,
                    )
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Button(onClick = onPrimary, modifier = Modifier.fillMaxWidth()) {
                        Text(primary)
                    }
                    TextButton(onClick = onSecondary, modifier = Modifier.fillMaxWidth()) {
                        Text(secondary)
                    }
                }
            }
        }
    }
}
