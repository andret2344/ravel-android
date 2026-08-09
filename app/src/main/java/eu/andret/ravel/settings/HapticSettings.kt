package eu.andret.ravel.settings

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the board is allowed to buzz.
 *
 * ### Why the game has this switch when the phone already has one
 *
 * It is not a duplicate of the system's touch-feedback setting, and it deliberately cannot do what
 * that one does. This switch only ever takes haptics *away*: turned on, it asks the platform, and
 * the platform still answers to the phone's own setting, which is why a player with system haptics
 * off feels nothing however this is set. See [eu.andret.ravel.ui.board.BoardHaptics] for why the
 * game plays feedback through the view rather than driving the vibrator itself.
 *
 * What it exists for is the case the system switch cannot express: a player who wants haptics in
 * their keyboard and their notifications and not on a board they drag a finger across for minutes at
 * a time. Sending them to the system settings to turn off everything would be answering "this game
 * buzzes too much" with "then have a quieter phone".
 *
 * ### Default
 *
 * On. The feature is the kind that is only discovered by feeling it, so a player who would have
 * wanted it will never go looking for a switch to turn it on - while one who does not want it has
 * already been told, by the buzzing, that there is something to look for.
 *
 * Stored in [SETTINGS_FILE] beside the language, which is what that constant is for. Observable
 * rather than a plain getter because the board reads it while it is on screen: a switch flipped in
 * the options tab has to reach a half-solved level the player goes back to, and a value read once
 * when that screen was built would not.
 */
class HapticSettings(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(preferences.getBoolean(KEY_HAPTICS, true))

    /** Whether the game may ask for haptic feedback at all. */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    /**
     * Turns board haptics on or off.
     *
     * Written before it is published, so a process killed between the two loses the announcement and
     * not the setting - the other way round is a switch that moves on screen and comes back on the
     * next launch.
     */
    fun setEnabled(value: Boolean) {
        preferences.edit { putBoolean(KEY_HAPTICS, value) }
        _enabled.value = value
    }

    private companion object {
        const val KEY_HAPTICS = "haptics"
    }
}
