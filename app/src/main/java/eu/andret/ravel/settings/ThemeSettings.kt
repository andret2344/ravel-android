package eu.andret.ravel.settings

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Light, dark, or whatever the phone is set to. */
enum class ThemeChoice(internal val code: String) {
    SYSTEM("s"),
    LIGHT("l"),
    DARK("d"),
}

/**
 * Which palette the game is drawn in.
 *
 * Defaults to following the phone, which is what the game did before this setting existed, so a
 * player who never opens it sees no change. Stored by a one-letter code rather than the enum's name,
 * so renaming an entry does not quietly reset everybody to [ThemeChoice.SYSTEM].
 *
 * Stored in [SETTINGS_FILE] beside the language and haptics, which keeps it inside the backup rules
 * without a new line in either of them. Observable because the activity draws from it, and a choice
 * made on the options tab has to repaint the screen the player is looking at.
 */
class ThemeSettings(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE)

    private val _choice = MutableStateFlow(
        preferences.getString(KEY_THEME, null)
            ?.let { code -> ThemeChoice.entries.firstOrNull { it.code == code } }
            ?: ThemeChoice.SYSTEM,
    )

    val choice: StateFlow<ThemeChoice> = _choice.asStateFlow()

    /** Written before it is published, on the argument [HapticSettings.setEnabled] makes. */
    fun choose(value: ThemeChoice) {
        preferences.edit { putString(KEY_THEME, value.code) }
        _choice.value = value
    }

    private companion object {
        const val KEY_THEME = "theme"
    }
}
