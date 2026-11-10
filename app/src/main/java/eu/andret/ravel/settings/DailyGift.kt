package eu.andret.ravel.settings

import android.content.Context
import androidx.core.content.edit
import eu.andret.ravel.game.DailyDate
import eu.andret.ravel.game.Rewards
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether today's gift in the shop is still there to take - [Rewards.DAILY_GIFT], once a day.
 *
 * The same day as everything else in the game ([DailyDate], the phone's own time zone) and the same
 * three-way comparison as [AdAllowance], for the same reasons: nothing scheduled at midnight, a
 * restore on the same day keeps the gift taken, and a clock wound back gets nothing until it is put
 * right. Only the day of the last claim is stored - one claim a day needs no count beside it.
 *
 * In [SHOP_FILE] beside the ad allowance, so the backup rules already cover it, and never touched by
 * a progress reset, which would otherwise be a second gift for the price of a confirmation dialog.
 */
class DailyGift(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences(SHOP_FILE, Context.MODE_PRIVATE)

    private val _available = MutableStateFlow(read())

    /** Whether the gift can be taken now. Cached for the shop to observe; see [refresh]. */
    val available: StateFlow<Boolean> = _available.asStateFlow()

    /** Re-reads against the current date, for a shop opened on a later day than the process began. */
    fun refresh() {
        _available.value = read()
    }

    /**
     * Takes today's gift, or refuses when it is already taken. The caller pays only on true, which
     * is what keeps two quick taps from paying twice.
     */
    fun claim(): Boolean {
        val today = DailyDate.today()
        if (!availableFrom(preferences.getLong(KEY_DAY, ABSENT), today)) {
            _available.value = false
            return false
        }
        preferences.edit { putLong(KEY_DAY, today) }
        _available.value = false
        return true
    }

    /**
     * Puts today's gift back, or takes it, for testing the shop. Reachable only from `src/debug`
     * (`ShopDebug`), like [AdAllowance.setRemaining].
     */
    fun setAvailable(available: Boolean) {
        preferences.edit {
            if (available) remove(KEY_DAY) else putLong(KEY_DAY, DailyDate.today())
        }
        _available.value = available
    }

    private fun read(): Boolean = availableFrom(preferences.getLong(KEY_DAY, ABSENT), DailyDate.today())

    companion object {
        private const val KEY_DAY = "giftDay"

        /** Never claimed. Not a valid ordinal, so it is before every real day. */
        private const val ABSENT = 0L

        /**
         * The whole rule: available only when the last claim was on an earlier day. Today's claim
         * means taken; a claim stored on a later day means a clock moved back, and gets nothing until
         * the clock is right again - see [AdAllowance.remainingFrom] for the longer argument.
         */
        fun availableFrom(claimedDay: Long, today: Long): Boolean = claimedDay < today
    }
}
