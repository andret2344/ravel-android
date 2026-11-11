package eu.andret.ravel.settings

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Which colors the game is drawn in. [code] is what is stored, so a rename cannot lose a choice. */
enum class ThemeChoice(val code: String) {
    /** Light or dark as the phone is, switching with it - the behavior before there was a choice. */
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    ;

    companion object {
        fun byCode(code: String?): ThemeChoice = entries.firstOrNull { it.code == code } ?: SYSTEM
    }
}

/**
 * The player's theme choice, for the whole process.
 *
 * In [SETTINGS_FILE] with the other options, so the backup rules did not need a line. Observable
 * because the activity redraws in the new colors the moment the choice is made, with no restart.
 * An unknown stored value - from a later version, say - falls back to [ThemeChoice.SYSTEM], which is
 * also what a player who never chose gets.
 */
class ThemeSettings(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE)

    private val _choice = MutableStateFlow(ThemeChoice.byCode(preferences.getString(KEY_THEME, null)))

    val choice: StateFlow<ThemeChoice> = _choice.asStateFlow()

    /** Written before it is published, as [HapticSettings.setEnabled] explains. */
    fun choose(choice: ThemeChoice) {
        preferences.edit { putString(KEY_THEME, choice.code) }
        _choice.value = choice
    }

    private companion object {
        const val KEY_THEME = "theme"
    }
}
