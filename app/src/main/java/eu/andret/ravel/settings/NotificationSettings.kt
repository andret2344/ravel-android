package eu.andret.ravel.settings

import android.content.Context
import androidx.core.content.edit
import eu.andret.ravel.notify.NotificationPlan
import eu.andret.ravel.notify.NotificationPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The daily notification's [NotificationPrefs], on disk.
 *
 * Not to be confused with [DailyReminder], which is the question the game asks *inside* the app at
 * launch. That one was called a reminder first; this is the system notification, and the two are
 * switched separately because they are different intrusions - one waits until the player has
 * opened the game, the other comes to find them.
 *
 * In [SETTINGS_FILE] rather than a file of its own, for the reason given there: the backup rules
 * already name it, and everything here is either a preference or a few numbers that describe one.
 * A restore onto a new phone brings the choice back without the system permission behind it, which
 * the options tab shows as blocked - the honest reading of that state.
 *
 * Storage only. Every rule about the values is in [NotificationPlan]; this writes back whatever it
 * answered.
 */
class NotificationSettings(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE)

    private val _prefs = MutableStateFlow(read())

    val prefs: StateFlow<NotificationPrefs> = _prefs.asStateFlow()

    /** Applies [change] to what is stored, writing only when it changed something. */
    fun update(change: (NotificationPrefs) -> NotificationPrefs) {
        val before = _prefs.value
        val after = change(before)
        if (after == before) return
        preferences.edit {
            putBoolean(KEY_ENABLED, after.enabled)
            putBoolean(KEY_AUTOMATIC, after.automatic)
            putInt(KEY_MANUAL, after.manualMinute)
            putInt(KEY_LEARNED, after.learnedMinute ?: ABSENT)
            putLong(KEY_SENT, after.sentDay ?: ABSENT.toLong())
            putInt(KEY_IGNORED, after.ignored)
            putBoolean(KEY_OFFERED, after.offered)
            putBoolean(KEY_OFFER_BACK, after.offerBack)
        }
        _prefs.value = after
    }

    private fun read(): NotificationPrefs = NotificationPrefs(
        enabled = preferences.getBoolean(KEY_ENABLED, false),
        automatic = preferences.getBoolean(KEY_AUTOMATIC, true),
        manualMinute = preferences.getInt(KEY_MANUAL, NotificationPlan.DEFAULT_MINUTE)
            .takeIf { it in 0 until NotificationPlan.MINUTES_PER_DAY }
            ?: NotificationPlan.DEFAULT_MINUTE,
        learnedMinute = preferences.getInt(KEY_LEARNED, ABSENT)
            .takeIf { it in 0 until NotificationPlan.MINUTES_PER_DAY },
        sentDay = preferences.getLong(KEY_SENT, ABSENT.toLong()).takeIf { it > 0 },
        ignored = preferences.getInt(KEY_IGNORED, 0).coerceAtLeast(0),
        offered = preferences.getBoolean(KEY_OFFERED, false),
        offerBack = preferences.getBoolean(KEY_OFFER_BACK, false),
    )

    private companion object {
        const val KEY_ENABLED = "notify-enabled"
        const val KEY_AUTOMATIC = "notify-automatic"
        const val KEY_MANUAL = "notify-manual"
        const val KEY_LEARNED = "notify-learned"
        const val KEY_SENT = "notify-sent"
        const val KEY_IGNORED = "notify-ignored"
        const val KEY_OFFERED = "notify-offered"
        const val KEY_OFFER_BACK = "notify-offer-back"

        /** Nothing stored. Below every valid minute and every valid day. */
        const val ABSENT = -1
    }
}
