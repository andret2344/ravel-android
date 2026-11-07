package eu.andret.ravel.settings

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the player wants levels without the word lengths.
 *
 * This is the player's choice for levels **not yet opened**, and nothing more. The mode a level is
 * actually played in is decided once, when its board first comes up, and kept with the level - see
 * [LevelProgress.open]. A level that has been seen with its lengths cannot be switched to hard,
 * since the lengths are exactly what the bonus is paid for not having; one opened hard cannot be
 * switched back, since the player chose it for that board and the rule is that a level's mode does
 * not move under it.
 *
 * Off by default. It is offered on the main tab where nobody can miss it, which is the whole of the
 * discoverability it needs, and a first board with no lengths would be a first board nobody
 * understands.
 *
 * Not per language, unlike the ladders: how a player likes to play does not change with the
 * dictionary. Stored in [SETTINGS_FILE] beside the other preferences.
 */
class HardModeSettings(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(preferences.getBoolean(KEY_HARD, false))

    /** Whether the next level opened will be played without lengths. */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    /** Written before it is published, as with [HapticSettings.setEnabled]. */
    fun setEnabled(value: Boolean) {
        preferences.edit { putBoolean(KEY_HARD, value) }
        _enabled.value = value
    }

    private companion object {
        const val KEY_HARD = "hardMode"
    }
}
