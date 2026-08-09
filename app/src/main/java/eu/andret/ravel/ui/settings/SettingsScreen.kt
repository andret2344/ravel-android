package eu.andret.ravel.ui.settings

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.andret.ravel.R
import eu.andret.ravel.generator.Language
import eu.andret.ravel.ui.about.Licence
import java.util.Locale

/**
 * The switches. There are two of them, and on some devices a third.
 *
 * The order is what the player is most likely to have come for: the language, then the haptics, then
 * the ad privacy row. Haptics are second rather than first because they are the setting somebody
 * arrives at having already felt the thing they want changed, while the language is the one people
 * go looking for before the game has told them anything.
 *
 * The third is the ad privacy row, and it is conditional because the consent SDK says whether this
 * device was ever asked - a player outside the regions that require a form has nothing to change,
 * and a row that opens a form which reports "nothing to show" is worse than no row. It sits above
 * the word sources rather than below them because it is a setting and they are a notice.
 *
 * The language row shows what is set and opens a picker, where it used to lay every option out on
 * the tab. Two changes at once, and they are the same change: "follow the phone" is gone, and with
 * two options left an open list was three rows spent on the setting a player touches least. It is
 * also the setting that grows - a third language would have been a third permanent row - so the
 * list moved into [LanguageDialog] and the tab kept one line.
 *
 * Nothing was lost with the phone option. A player who has never chosen is put on the phone's
 * language already, falling back to English where the game does not speak it, which is what that
 * row did; what has gone is the ability to go back to *following*, and see
 * [eu.andret.ravel.settings.LanguageSettings] for why that is a question players do not ask.
 *
 * The language is one setting and not two. It picks the dictionary boards are built from and the
 * language the interface is read in, because a player who wants English puzzles wants English
 * buttons - see [eu.andret.ravel.settings.LanguageSettings].
 *
 * The word sources sit under it rather than on an "About" screen of their own. Three of them carry
 * attribution the game is required to show, and a screen holding one setting has room; a second
 * screen reached from this one would be a tap in front of a list that is already short.
 *
 * @param onLicence opens one source. The names are on this screen and the licences are a page
 *   deeper, because a player who came here to change the language should not have to scroll past a
 *   Princeton disclaimer to find out they have already passed it.
 */
@Composable
fun SettingsScreen(
    onLicence: (Licence) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(),
) {
    val language by viewModel.language.collectAsStateWithLifecycle()
    val unfinished by viewModel.unfinished.collectAsStateWithLifecycle()
    val privacyOptions by viewModel.privacyOptions.collectAsStateWithLifecycle()
    val hapticsEnabled by viewModel.hapticsEnabled.collectAsStateWithLifecycle()

    // Null under a preview, and the consent form has nowhere to open without one - so the row that
    // opens it is not offered rather than offered and dead.
    val activity = LocalActivity.current

    /** Whether the picker is open. */
    var picking by remember { mutableStateOf(false) }

    /*
     * The language waiting for the warning to be answered, or null when nothing is waiting.
     *
     * A bare `Language?` now. It used to be boxed in a `Pending` class, because back when the picker
     * offered "follow the phone" that option *was* a null language and could not be told apart from
     * "no dialog is open". One option removed, one wrapper class removed with it.
     */
    var pending by remember { mutableStateOf<Language?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = stringResource(R.string.tab_settings),
            style = MaterialTheme.typography.headlineSmall,
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.settings_language),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { picking = true }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    // The row is the current language and not the word "Language", which the header
                    // above it already says. A closed picker still has to answer the question most
                    // players open this tab with - which one am I on - and a row repeating its own
                    // heading would make them open the dialog to find out.
                    Text(text = nameOf(language), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = stringResource(R.string.settings_change),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                text = stringResource(R.string.settings_language_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.settings_feedback),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    // Toggleable on the row rather than clickable on the switch, for the reason the
                    // language options are selectable on the row: a switch is a few millimetres of
                    // something a finger tall, and a player who taps the words means the switch.
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = hapticsEnabled,
                            onValueChange = viewModel::setHaptics,
                            role = Role.Switch,
                        )
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.settings_haptics),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    // Drawn, not clicked: the row above already took the press, and a switch that
                    // handled its own would be a second target firing the same toggle twice.
                    Switch(checked = hapticsEnabled, onCheckedChange = null)
                }
            }
            // Says what the switch cannot do as well as what it does. Turning it on cannot give a
            // player haptics their phone has switched off system-wide, and somebody who tried that
            // and felt nothing would otherwise be looking for a bug in the game.
            Text(
                text = stringResource(R.string.settings_haptics_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (privacyOptions && activity != null) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.settings_privacy),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        // The same full-width target the licence rows use: a row that is a finger
                        // tall should be tappable everywhere a finger lands on it.
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.changePrivacyOptions(activity) }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.settings_privacy_ads),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.settings_privacy_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.settings_sources),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Licence.entries.forEach { licence ->
                        SourceRow(licence = licence, onOpen = { onLicence(licence) })
                    }
                }
            }
            Text(
                text = stringResource(R.string.settings_sources_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (picking) {
        LanguageDialog(
            selected = language,
            onDismiss = { picking = false },
            onPick = { picked ->
                // Closed before anything else happens, so the warning below - when there is one -
                // arrives on the settings screen rather than on top of the list it came from. Two
                // stacked dialogs would leave the player answering a question about a board while
                // still looking at a list of languages.
                picking = false
                when {
                    // Same language: nothing is lost, so nothing is asked. The write still happens,
                    // and it is not a no-op - see LanguageSettings.choose.
                    picked == language -> viewModel.choose(picked)
                    unfinished -> pending = picked
                    else -> viewModel.choose(picked)
                }
            },
        )
    }

    pending?.let { waiting ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.settings_switch_title)) },
            text = { Text(stringResource(R.string.settings_switch_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pending = null
                        viewModel.choose(waiting)
                    },
                ) {
                    Text(stringResource(R.string.settings_switch_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) {
                    Text(stringResource(R.string.settings_switch_cancel))
                }
            },
        )
    }
}

/**
 * The languages, as a dialog.
 *
 * A dialog rather than the list this used to be, laid open on the tab. The list was three rows of a
 * screen that has four things on it, which made the least-changed setting in the game the biggest
 * thing on its own screen - and it will only get worse, since every language added is another
 * permanent row. Behind a picker, a third language costs nothing on the tab.
 *
 * No confirm button. Tapping a language is the choice, which is what a picker is for; a dialog that
 * needed a second tap on "OK" would be asking the player to confirm the thing they just did. What
 * *is* confirmed, when there is a board to lose, is the consequence - and that question is asked
 * afterwards, on its own dialog, because it is about the board and not about the list.
 *
 * @param selected the language being played, marked rather than merely defaulted to. There is always
 *   one - the game has no "unset" language since the phone option went away - so the radio group is
 *   never drawn with nothing in it.
 */
@Composable
private fun LanguageDialog(
    selected: Language,
    onDismiss: () -> Unit,
    onPick: (Language) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_language)) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                Language.entries.forEach { language ->
                    LanguageOption(
                        label = nameOf(language),
                        selected = language == selected,
                        onSelect = { onPick(language) },
                    )
                }
            }
        },
        // Dismiss only. The way out of a picker is to not pick, and a dialog whose only button
        // cancels puts that where a thumb already expects to find it.
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_close)) }
        },
    )
}

/**
 * A language's name in that language - "polski", "English" - and never translated.
 *
 * A player looking for English on a Polish screen is looking for the word "English", and a list that
 * offered them "angielski" would be a list they have to already read Polish to escape. Taken from
 * the platform rather than from a string resource for the same reason it is not translated: there is
 * exactly one right answer per language and the platform has it for all of them.
 *
 * Capitalised by the locale it belongs to, because the platform returns these lower-cased in the
 * languages that write them that way, and this one is at the start of a line.
 */
private fun nameOf(language: Language): String {
    val locale = Locale.forLanguageTag(language.code)
    return locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
}

/**
 * One source: what it is called, and what it is used under.
 *
 * The licence label is on the row rather than only on the page behind it, because it is the part a
 * reader is checking. Somebody who opens this list is asking "what is in here and what does it
 * oblige you to", and answering the second half only after a tap makes them open six pages to find
 * out there was nothing to worry about.
 */
@Composable
private fun SourceRow(licence: Licence, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = stringResource(licence.source), style = MaterialTheme.typography.bodyLarge)
        Text(
            text = stringResource(licence.licence),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LanguageOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        // The whole row is the target, and the button is drawn rather than clicked: a radio button
        // is a few millimetres of a row that is a finger tall, and a player who taps the word is
        // choosing the language as much as one who hits the circle.
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}
