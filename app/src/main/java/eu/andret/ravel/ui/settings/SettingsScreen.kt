package eu.andret.ravel.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import java.util.Locale

/**
 * The switches. There is one of them.
 *
 * A list of choices rather than a dropdown, because there are three of them and a dropdown is a
 * control that hides two of its options behind a tap - which on a screen with nothing else on it is
 * hiding the whole screen. The three are one group: "follow the phone" is not a lesser option that
 * belongs above a separator, it is the one most players are already on.
 *
 * The language is one setting and not two. It picks the dictionary boards are built from and the
 * language the interface is read in, because a player who wants English puzzles wants English
 * buttons - see [eu.andret.ravel.settings.LanguageSettings].
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier, viewModel: SettingsViewModel = viewModel()) {
    val choice by viewModel.choice.collectAsStateWithLifecycle()
    val unfinished by viewModel.unfinished.collectAsStateWithLifecycle()

    /*
     * The choice waiting for an answer, wrapped rather than held as a bare Language?.
     *
     * Null means "no dialog is open" and the phone option means "the player picked the phone", and
     * both of those are a null language. One box around it is the difference between the two.
     */
    var pending by remember { mutableStateOf<Pending?>(null) }

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
                Column(modifier = Modifier.selectableGroup()) {
                    LanguageOption(
                        label = stringResource(R.string.settings_language_system),
                        selected = choice == null,
                        onSelect = {
                            select(null, choice, unfinished, viewModel) { pending = it }
                        },
                    )
                    Language.entries.forEach { language ->
                        LanguageOption(
                            label = nameOf(language),
                            selected = choice == language,
                            onSelect = {
                                select(language, choice, unfinished, viewModel) { pending = it }
                            },
                        )
                    }
                }
            }
            Text(
                text = stringResource(R.string.settings_language_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
                        viewModel.choose(waiting.language)
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

/** A choice that has been made but not yet paid for. See the comment where this is held. */
private data class Pending(val language: Language?)

/**
 * Applies [language], or asks first when there is a board that switching would throw away.
 *
 * Picking the option already selected does nothing at all, rather than warning about a board that is
 * in no danger: the setting would not change, so nothing would be lost, and a dialog over that would
 * teach the player that the warning is noise.
 */
private inline fun select(
    language: Language?,
    choice: Language?,
    unfinished: Boolean,
    viewModel: SettingsViewModel,
    onAsk: (Pending) -> Unit,
) {
    if (language == choice) return
    if (unfinished) onAsk(Pending(language)) else viewModel.choose(language)
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
