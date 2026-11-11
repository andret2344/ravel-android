package eu.andret.ravel.settings

import android.content.Context
import androidx.core.content.edit
import eu.andret.ravel.game.DailyDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether today's "stuck?" dialog - the one that points a player who has gone a while without a word
 * at the ad in the shop - may still be shown. Once a day at most.
 *
 * The rule a board screen applies before showing it is in `ui/game/StuckOffer.kt`; this only counts
 * the day. Counted the way [DailyGift] counts its claim - the day of the last showing, compared
 * three ways - because it is the same question: has today's one already happened. A clock wound
 * back gets no dialog until it is right again, which is the side of the line a nag should fall on.
 *
 * In [SHOP_FILE] beside the ad allowance and the gift, so the backup rules already cover it, and a
 * restore on the same day does not show it twice.
 */
class StuckOffer(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences(SHOP_FILE, Context.MODE_PRIVATE)

    private val _available = MutableStateFlow(read())

    /** Whether the dialog may be shown today. Cached for the board screens; see [refresh]. */
    val available: StateFlow<Boolean> = _available.asStateFlow()

    /** Re-reads against the current date, for a board opened on a later day than the process began. */
    fun refresh() {
        _available.value = read()
    }

    /** Uses up today's showing. Called when the dialog appears, not when it is answered. */
    fun shown() {
        preferences.edit { putLong(KEY_DAY, DailyDate.today()) }
        _available.value = false
    }

    private fun read(): Boolean =
        DailyGift.availableFrom(preferences.getLong(KEY_DAY, ABSENT), DailyDate.today())

    private companion object {
        const val KEY_DAY = "stuckDay"

        /** Never shown. Not a valid ordinal, so it is before every real day. */
        const val ABSENT = 0L
    }
}
