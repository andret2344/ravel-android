package eu.andret.ravel.settings

import android.content.Context
import androidx.core.content.edit

/**
 * Whether the game still asks about an unsolved daily on the way in.
 *
 * On by default, because the reminder exists for the player who forgets the mode is there, and a
 * setting nobody has met yet cannot be the reason they never see it. Off is a decision the player
 * makes in the prompt itself - a checkbox under the question - so the one place the reminder is
 * ever annoying is also the place it can be switched off.
 *
 * Stored beside the language rather than in its own file: both are small answers to "how should the
 * game behave", and a preferences file per setting is a file per setting to migrate later.
 */
class DailyReminder(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Whether the prompt is still allowed to appear. */
    var enabled: Boolean
        get() = preferences.getBoolean(KEY_ENABLED, true)
        set(value) = preferences.edit { putBoolean(KEY_ENABLED, value) }

    private companion object {
        const val FILE = "ravel-settings"
        const val KEY_ENABLED = "daily-reminder"
    }
}
