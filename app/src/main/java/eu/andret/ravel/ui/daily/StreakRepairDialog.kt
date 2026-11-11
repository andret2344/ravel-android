package eu.andret.ravel.ui.daily

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import eu.andret.ravel.R
import eu.andret.ravel.ads.RewardedAds
import eu.andret.ravel.game.Streak
import eu.andret.ravel.ui.CoinAmount

/**
 * The offer to buy back yesterday: [Streak.REPAIR_PRICE] coins, or one rewarded ad.
 *
 * Opens by itself once, the first time the daily menu is seen with a gap on offer; after that it
 * waits on the run card until midnight, behind its "Save" button. Closing it with the back button or
 * the scrim is therefore *not* a refusal - a stray gesture would otherwise cost a thirty-day run -
 * and only [onDecline] ends the offer.
 *
 * Built like [eu.andret.ravel.ui.game.StackedDialog] - stacked full-width buttons on the alert's own
 * surface - but not from it, because the ad button has states that one does not: it is disabled
 * while an ad is being found, turns into a retry when none came, and is missing altogether when the
 * day's watches are spent or ads were refused, so the coins are then the only way and nothing on
 * screen pretends otherwise.
 *
 * A successful repair closes it from the outside: the gap stops being repairable, and the caller
 * stops drawing this.
 *
 * @param stake the run that is at risk, in days.
 */
@Composable
internal fun StreakRepairDialog(
    stake: Int,
    balance: Int,
    adState: RewardedAds.State,
    watchesLeft: Int,
    onPrepare: () -> Unit,
    onWatch: (Activity) -> Unit,
    onRetry: () -> Unit,
    onPay: () -> Unit,
    onDecline: () -> Unit,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(Unit) { onPrepare() }
    val activity = LocalActivity.current

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
                    text = stringResource(R.string.repair_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = AlertDialogDefaults.titleContentColor,
                )
                CompositionLocalProvider(LocalContentColor provides AlertDialogDefaults.textContentColor) {
                    // Scrolls and the buttons do not, for the reason StackedDialog gives: a phone on
                    // its side is a short window.
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(pluralStringResource(R.plurals.repair_body, stake, stake))
                        Text(
                            text = stringResource(R.string.repair_rule),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        AdNote(adState = adState, watchesLeft = watchesLeft)
                    }
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    AdButton(
                        adState = adState,
                        watchesLeft = watchesLeft,
                        onWatch = { activity?.let(onWatch) },
                        onRetry = onRetry,
                        enabled = activity != null,
                    )
                    OutlinedButton(
                        onClick = onPay,
                        enabled = balance >= Streak.REPAIR_PRICE,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.repair_pay))
                        CoinAmount(
                            amount = Streak.REPAIR_PRICE,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    TextButton(onClick = onDecline, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.repair_decline))
                    }
                }
            }
        }
    }
}

/** Whether the ad is a way to pay today at all: watches left, and ads not refused. */
private fun adOffered(adState: RewardedAds.State, watchesLeft: Int): Boolean =
    watchesLeft > 0 && adState != RewardedAds.State.BLOCKED

/**
 * The ad button in whatever state the ad is in, or nothing when the ad is not on offer.
 *
 * The labels are the shop tile's own - finding an ad, none right now - so the two places an ad can
 * start describe the same states in the same words.
 */
@Composable
private fun AdButton(
    adState: RewardedAds.State,
    watchesLeft: Int,
    onWatch: () -> Unit,
    onRetry: () -> Unit,
    enabled: Boolean,
) {
    if (!adOffered(adState, watchesLeft)) return
    val (label, press) = when (adState) {
        RewardedAds.State.READY -> stringResource(R.string.repair_watch) to onWatch
        RewardedAds.State.UNAVAILABLE -> stringResource(R.string.shop_ad_unavailable) to onRetry
        else -> stringResource(R.string.shop_ad_loading) to null
    }
    Button(
        onClick = { press?.invoke() },
        enabled = enabled && press != null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (press == null) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = LocalContentColor.current,
            )
        }
        Text(
            text = label,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(start = if (press == null) 8.dp else 0.dp),
        )
    }
}

/**
 * Why there is no ad button, when the reason is one the player can do something about.
 *
 * Refused ads are a choice made in the consent form and changeable from the options tab, so that
 * gets a line. A day with its watches spent gets none: the coins are still there, and a sentence
 * about tomorrow's allowance would be about a repair that cannot wait for tomorrow.
 */
@Composable
private fun AdNote(adState: RewardedAds.State, watchesLeft: Int) {
    if (watchesLeft > 0 && adState == RewardedAds.State.BLOCKED) {
        Text(
            text = stringResource(R.string.shop_ad_blocked, stringResource(R.string.tab_settings)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
