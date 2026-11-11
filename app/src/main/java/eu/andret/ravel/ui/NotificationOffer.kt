package eu.andret.ravel.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import eu.andret.ravel.R
import eu.andret.ravel.notify.NoticeOffer

/**
 * The one question about the daily notification, asked after a solve and never at launch.
 *
 * After a solve because that is when the player has just shown they care about the daily - the
 * second solve for [NoticeOffer.FIRST], which is the earliest the game can tell. At launch it would
 * be a permission prompt from a game the player has not played yet, which is the version of this
 * everybody has learned to refuse.
 *
 * Our own dialog first and the system's only after a yes: the system lets an app ask twice, and a
 * no spent on our dialog costs nothing, while a no spent on the system's is one of the two.
 *
 * The body says what the notification will and will not do, because that is the whole case for
 * saying yes: at most one a day, only while the board is waiting.
 */
@Composable
internal fun NotificationOfferDialog(
    offer: NoticeOffer,
    onYes: () -> Unit,
    onNo: () -> Unit,
) {
    val (title, body) = when (offer) {
        NoticeOffer.FIRST -> R.string.notify_offer_title to R.string.notify_offer_body
        NoticeOffer.BACK -> R.string.notify_offer_back_title to R.string.notify_offer_back_body
    }
    AlertDialog(
        // Put away is a no. It is asked once either way, and a question that came back because it
        // was dismissed rather than answered would be the nagging this is designed not to do.
        onDismissRequest = onNo,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(body)) },
        confirmButton = {
            TextButton(onClick = onYes) { Text(stringResource(R.string.notify_offer_yes)) }
        },
        dismissButton = {
            TextButton(onClick = onNo) { Text(stringResource(R.string.notify_offer_no)) }
        },
    )
}
