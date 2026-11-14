package eu.andret.ravel.settings

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether a finished board's list of words comes up open or folded.
 *
 * Remembered rather than folded every time, because opening it says something about the player:
 * somebody who wanted to see the words on one board wants to see them on the next, and making them
 * unfold it on every board is a tap charged for a preference they already stated. Somebody who never
 * opened it never sees it open.
 *
 * Folded by default. The completion dialog leads with what the board paid, and a column of words
 * above the buttons would push the way on below the fold on a short phone before the player has
 * asked for any of it.
 *
 * One answer for the completion dialogs and the calendar alike - it is the same list, and a list
 * that was open in one place and folded in the other would look like two features. Stored in
 * [SETTINGS_FILE] beside the other preferences, so the backup rules need nothing new.
 */
class WordListSettings(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE)

    private val _open = MutableStateFlow(preferences.getBoolean(KEY_OPEN, false))

    /** Whether the list is shown unfolded. */
    val open: StateFlow<Boolean> = _open.asStateFlow()

    /** Written before it is published, as with [HapticSettings.setEnabled]. */
    fun setOpen(value: Boolean) {
        preferences.edit { putBoolean(KEY_OPEN, value) }
        _open.value = value
    }

    private companion object {
        const val KEY_OPEN = "wordsOpen"
    }
}
