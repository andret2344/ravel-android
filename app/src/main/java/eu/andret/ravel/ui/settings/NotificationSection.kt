package eu.andret.ravel.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import eu.andret.ravel.R
import eu.andret.ravel.notify.NotificationPlan
import eu.andret.ravel.notify.NotificationPrefs
import java.util.Calendar

/**
 * The daily notification's switches: on or off, then whether the time follows the player or is one
 * they pick, and the time itself when they pick it.
 *
 * Two switches and not one three-way choice, because they answer two questions in the order a
 * player asks them - "do I want this", then "when". The second and the time are drawn grayed while
 * the first is off rather than hidden, so a player deciding whether to switch it on can see what
 * they would be switching on.
 *
 * The note under the card does the explaining the switches cannot: what time the automatic one has
 * arrived at, and the rules a player would otherwise only discover - one a day, none once solved,
 * off by themselves after three in a row go unanswered. When the system will not show them, it says
 * so instead and offers the way to the system's own switch, which is the only place that can be
 * changed.
 *
 * @param blocked whether to say the system is blocking them: while they are on, or right after the
 *   system's dialog said no. Not merely "not permitted" - before the first ask on Android 13 there
 *   is no permission yet, and calling that blocked would be untrue.
 */
@Composable
internal fun NotificationSection(
    prefs: NotificationPrefs,
    blocked: Boolean,
    onToggle: (Boolean) -> Unit,
    onAutomatic: (Boolean) -> Unit,
    onPickTime: () -> Unit,
    onOpenSystem: () -> Unit,
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.settings_notify),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column {
                SwitchRow(
                    label = stringResource(R.string.settings_notify_daily),
                    checked = prefs.enabled,
                    enabled = true,
                    onChange = onToggle,
                )
                SwitchRow(
                    label = stringResource(R.string.settings_notify_automatic),
                    checked = prefs.automatic,
                    enabled = prefs.enabled,
                    onChange = onAutomatic,
                )
                if (!prefs.automatic) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = prefs.enabled, onClick = onPickTime)
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(
                                R.string.settings_notify_time,
                                formatMinute(context, prefs.manualMinute),
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            color = dimmedUnless(prefs.enabled),
                        )
                        Text(
                            text = stringResource(R.string.settings_change),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (prefs.enabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA)
                            },
                        )
                    }
                }
            }
        }
        if (blocked) {
            Text(
                text = stringResource(R.string.settings_notify_blocked),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(onClick = onOpenSystem) {
                Text(stringResource(R.string.settings_notify_open_system))
            }
        }
        if (prefs.automatic) {
            Text(
                text = stringResource(
                    R.string.settings_notify_automatic_note,
                    formatMinute(context, NotificationPlan.dueMinute(prefs)),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(R.string.settings_notify_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One switch, the whole row its target - see the haptics row for why. */
@Composable
private fun SwitchRow(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, onValueChange = onChange, role = Role.Switch)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = dimmedUnless(enabled),
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun dimmedUnless(enabled: Boolean): Color =
    if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA)

/**
 * Picks the fixed time. Material's clock face, in the phone's 12- or 24-hour style.
 *
 * Opt-in because Material still marks the picker experimental; it is the platform's own control and
 * the alternative is building a clock face here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NotificationTimeDialog(
    minute: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = minute / 60,
        initialMinute = minute % 60,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_notify_time_title)) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) {
                Text(stringResource(R.string.settings_notify_time_set))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_reset_cancel))
            }
        },
    )
}

/** [minute] after midnight, as the phone writes a time - 19:00 or 7:00 PM. */
private fun formatMinute(context: Context, minute: Int): String {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, minute / 60)
        set(Calendar.MINUTE, minute % 60)
    }
    return DateFormat.getTimeFormat(context).format(calendar.time)
}

/**
 * The system's notification settings for this app - the one place a block can be lifted. Android 8
 * and up have a page for exactly that; below it, the app's own details page is the nearest thing.
 */
internal fun openNotificationSettings(context: Context) {
    val notifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        null
    }
    val details = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    )
    try {
        context.startActivity(notifications ?: details)
    } catch (missing: ActivityNotFoundException) {
        // A manufacturer that dropped the notification page; the details page is always there.
        if (notifications == null) throw missing
        context.startActivity(details)
    }
}

/** Material's own disabled level, as the rest of this tab uses. */
private const val DISABLED_ALPHA = 0.38f
