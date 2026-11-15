package eu.andret.ravel.ui.settings

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.andret.ravel.R
import eu.andret.ravel.ui.AdaptiveScreen
import eu.andret.ravel.ui.about.BackHeader

/** The privacy page's route, under "settings/" so the options tab stays lit while it is open. */
object PrivacyRoute {
    const val SCREEN = "settings/privacy"
}

/**
 * The published privacy policy. The site's root is a 404 - the policy is one page of it - so this is
 * the full address, the same one the Play listing and the AdMob consent message give.
 */
private const val PRIVACY_POLICY_URL = "https://andret2344.github.io/ravel-android/privacy-policy"

/**
 * Everything about what the game sends, on a page of its own behind one row in the options, as the
 * licenses are: the usage statistics and crash reports switches, the ad privacy row where the
 * consent SDK owes one, and the privacy policy.
 *
 * Called "Privacy", not "Consents" (owner's choice, 2026-11-15; the name is argued in
 * `docs/DECISIONS.md`): crash reports do not rest on consent, and a page calling them one would
 * contradict the policy.
 *
 * The policy is linked here because Google Play wants it reachable inside an app that handles
 * personal data, not only from the store listing.
 *
 * Each switch keeps its own note: they are two different promises - see
 * [eu.andret.ravel.analytics.Telemetry] for why crash reports do not wait on consent and analytics
 * does. The ad privacy row is there only where the consent SDK says this device was asked; a row
 * that opens a form which reports "nothing to show" is worse than no row.
 */
@Composable
fun PrivacyScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PrivacyViewModel = viewModel(),
) {
    val analyticsEnabled by viewModel.analyticsEnabled.collectAsStateWithLifecycle()
    val crashReportsEnabled by viewModel.crashReportsEnabled.collectAsStateWithLifecycle()
    val privacyOptions by viewModel.privacyOptions.collectAsStateWithLifecycle()
    // Null under a preview, and the consent form has nowhere to open without one - so the row that
    // opens it is not offered rather than offered and dead.
    val activity = LocalActivity.current
    val links = LocalUriHandler.current

    val switches: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Card(modifier = Modifier.fillMaxWidth()) {
                SwitchRow(
                    label = stringResource(R.string.settings_analytics),
                    checked = analyticsEnabled,
                    enabled = true,
                    onChange = viewModel::setAnalytics,
                )
            }
            Note(stringResource(R.string.settings_analytics_note))
            Card(modifier = Modifier.fillMaxWidth()) {
                SwitchRow(
                    label = stringResource(R.string.settings_crash_reports),
                    checked = crashReportsEnabled,
                    enabled = true,
                    onChange = viewModel::setCrashReports,
                )
            }
            Note(stringResource(R.string.settings_crash_reports_note))
        }
    }

    val documents: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (privacyOptions && activity != null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    LinkRow(
                        label = stringResource(R.string.settings_privacy_ads),
                        onClick = { viewModel.changePrivacyOptions(activity) },
                    )
                }
                Note(stringResource(R.string.settings_privacy_note))
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                LinkRow(
                    label = stringResource(R.string.settings_privacy_policy),
                    onClick = { links.openUri(PRIVACY_POLICY_URL) },
                    external = true,
                )
            }
        }
    }

    AdaptiveScreen(modifier = modifier) { wide ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            BackHeader(title = stringResource(R.string.settings_privacy), onBack = onBack)
            if (wide) {
                // The switches on the left, the form and the policy on the right, as the options tab
                // splits what is set from what is opened.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) { switches() }
                    Column(modifier = Modifier.weight(1f)) { documents() }
                }
            } else {
                switches()
                documents()
            }
        }
    }
}

/**
 * A row that opens something, the whole row its target, as the licenses' rows are.
 *
 * @param external whether it leaves the app for the browser, which the row says with the same icon
 *   a solved board's words carry beside their dictionary links.
 */
@Composable
private fun LinkRow(label: String, onClick: () -> Unit, external: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (external) {
            Icon(
                painter = painterResource(R.drawable.ic_open_external),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
