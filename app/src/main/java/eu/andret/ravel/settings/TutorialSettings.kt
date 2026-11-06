package eu.andret.ravel.settings

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the tutorial still has to be shown.
 *
 * Finishing it and skipping it are the same answer - the player has said they are done with it -
 * and both are final until they ask for it again from the options tab, which is one tap and starts
 * it on the spot.
 *
 * Stored in [SETTINGS_FILE] beside the language and the haptics switch, so the backup rules, which
 * are written per file, did not need a line for it.
 *
 * @param hasPlayed whether this install already has a level or a day solved. Asked only when
 *   nothing has been stored yet - see [pendingFrom].
 */
class TutorialSettings(context: Context, hasPlayed: () -> Boolean) {
    private val preferences = context.applicationContext
        .getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE)

    private val _pending = MutableStateFlow(
        pendingFrom(
            stored = if (preferences.contains(KEY_DONE)) preferences.getBoolean(KEY_DONE, false) else null,
            hasPlayed = hasPlayed,
        ),
    )

    /** True while the tutorial should be on screen. */
    val pending: StateFlow<Boolean> = _pending.asStateFlow()

    /** Finished or skipped: written before it is published, as [HapticSettings.setEnabled] explains. */
    fun markDone() {
        preferences.edit { putBoolean(KEY_DONE, true) }
        _pending.value = false
    }

    /**
     * Starts it again, from the options tab.
     *
     * Not written to disk. A replay the player closes the app in the middle of is not a tutorial
     * they still owe; the next launch should open the game, not the lesson they chose to revisit.
     */
    fun restart() {
        _pending.value = true
    }

    internal companion object {
        const val KEY_DONE = "tutorial_done"

        /**
         * The decision, apart from the storage so it can be tested without a device.
         *
         * A stored answer always wins. With none, a player who has already solved something is
         * taken as having learned the rules the hard way - they installed a version with no tutorial
         * and played anyway - and is not stopped by one on their next launch. [hasPlayed] is a
         * function so the progress stores are only opened in that one case.
         */
        fun pendingFrom(stored: Boolean?, hasPlayed: () -> Boolean): Boolean =
            if (stored != null) !stored else !hasPlayed()
    }
}
