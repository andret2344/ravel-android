package eu.andret.ravel.settings

import android.content.Context
import androidx.core.content.edit
import eu.andret.ravel.game.DailyDate
import eu.andret.ravel.game.Rewards
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * How many more ads the player can turn into coins today.
 *
 * The ceiling itself and the reasoning behind it are in [Rewards.ADS_PER_DAY]; this is only where
 * the counting happens. Two things about how it counts are worth stating.
 *
 * **A day is the same day the daily challenge means.** It comes from [DailyDate], in the phone's
 * own time zone, so a player never has two different midnights in one game. Storing the day
 * alongside the count and comparing it on the way out is what makes the reset free: there is
 * nothing to schedule, nothing to run at midnight, and no alarm to be killed by a battery saver. A
 * stored day that is not today reads as an untouched allowance whether the app was open at midnight
 * or reinstalled a month later.
 *
 * **Only a finished ad is counted.** [consume] is called from the reward callback, not from the
 * button, so an ad the player closes after two seconds costs them a fraction of their attention and
 * none of their three. The opposite - charging on show - would have a failed ad network eat the
 * day's allowance without paying a coin.
 *
 * **A stored day is compared three ways, not two** - see [remainingFrom]. That is what makes this
 * file survive both being restored from a backup and having the clock moved under it, without a
 * server to ask.
 */
class AdAllowance(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _remaining = MutableStateFlow(read())

    /** Watches left today, never negative. Zero means the shop offers nothing until tomorrow. */
    val remaining: StateFlow<Int> = _remaining.asStateFlow()

    /**
     * Re-reads the count against the current date.
     *
     * A [StateFlow] read once at construction is a number computed against the day the process
     * started, and this process can outlive a midnight easily - the same trap
     * [StoredDailyProgress] steps around by re-reading "today" at every use. Here the value is
     * cached because a screen observes it, so something has to ask again: the shop does, on the way
     * in. The remaining gap is a player sitting on the shop screen as midnight passes, who sees a
     * stale zero until they leave the tab and come back.
     */
    fun refresh() {
        _remaining.value = read()
    }

    /**
     * Books one watch against today, or refuses when the day is used up.
     *
     * Reports rather than throws, on [Wallet.spend]'s argument: "you have had your three" is an
     * answer the caller acts on by not paying out, and not an error.
     */
    fun consume(): Boolean {
        val today = DailyDate.today()
        val storedDay = preferences.getLong(KEY_DAY, ABSENT)
        val storedCount = preferences.getInt(KEY_COUNT, 0)
        if (remainingFrom(storedDay, storedCount, today) <= 0) {
            // Published anyway: reaching here with the flow saying otherwise means the day moved
            // under an open screen, and that screen is showing a number that is no longer true.
            _remaining.value = 0
            return false
        }
        // Only today's count is carried forward. A stored day in the past belongs to a day that is
        // over, and adding to it would count yesterday's watches against today.
        val used = if (storedDay == today) storedCount else 0
        preferences.edit {
            putLong(KEY_DAY, today)
            putInt(KEY_COUNT, used + 1)
        }
        _remaining.value = remainingFrom(today, used + 1, today)
        return true
    }

    private fun read(): Int = remainingFrom(
        storedDay = preferences.getLong(KEY_DAY, ABSENT),
        storedCount = preferences.getInt(KEY_COUNT, 0),
        today = DailyDate.today(),
    )

    companion object {
        private const val FILE = "ravel-ads"
        private const val KEY_DAY = "day"
        private const val KEY_COUNT = "count"

        /** No day has been stored. Not a valid ordinal, so it can never equal a real today. */
        private const val ABSENT = 0L

        /**
         * The whole rule, as a function of what is stored and what day it is.
         *
         * Separated from the preferences so it can be tested without a device, because the three
         * branches below are three different situations and only the first one happens on a phone
         * that is behaving itself.
         *
         * **The stored day is today.** The ordinary case: what is left is the cap minus what has
         * been watched. Clamped at both ends, because a count above the cap is exactly what a
         * *lowered* [Rewards.ADS_PER_DAY] leaves behind on an install that ran the old one, and
         * that has to read as nothing left rather than as owing the player watches back.
         *
         * **The stored day is in the past.** A new day, so a full allowance - and this is also what
         * a restored backup usually looks like. The counter is carried in the backup on purpose: a
         * restore that lands on the *same* day the backup was taken keeps the watches already spent
         * on it, rather than handing out a second three for reinstalling.
         *
         * **The stored day is in the future.** Nothing today. There is no honest way for a device
         * to have a record of tomorrow, so the two things that produce it are a clock moved
         * backwards and a backup restored from a device that was ahead. The first is the whole
         * reason this branch exists - without it, winding the date back is three more ads for the
         * cost of a trip to the settings app, repeatable - and the second costs a traveller landing
         * from a further time zone one day's ads, which they get back tomorrow. Answering the
         * exploit is worth that; the alternative is a server-side counter, which means an account,
         * which this game deliberately does not have.
         *
         * Note what this does *not* do: it never writes. A wound-back clock is refused for as long
         * as it is wound back and forgiven the moment it is put right, rather than being punished
         * with a flag that outlives the mistake. A player whose phone had the wrong date for a week
         * is not someone to lock out of the shop afterwards.
         */
        fun remainingFrom(storedDay: Long, storedCount: Int, today: Long): Int = when {
            storedDay == today ->
                (Rewards.ADS_PER_DAY - storedCount).coerceIn(0, Rewards.ADS_PER_DAY)

            storedDay < today -> Rewards.ADS_PER_DAY
            else -> 0
        }
    }
}
