package eu.andret.ravel.settings

import android.content.Context
import androidx.core.content.edit
import eu.andret.ravel.game.Rewards
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The player's coins: what they have, what they earn, what they spend.
 *
 * One instance for the whole process, held by the application, because every screen has to see the
 * same number. Two wallets over one file would drift apart the moment a hint was bought on one
 * screen while another was showing the balance.
 *
 * Written through to storage on every change rather than at some later save point: coins are
 * bought with real money sooner or later, and a balance that survives everything except being
 * killed by the system is not a balance anyone will accept.
 */
class Wallet(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _balance = MutableStateFlow(preferences.getInt(KEY_BALANCE, STARTING_BALANCE))

    val balance: StateFlow<Int> = _balance.asStateFlow()

    fun canAfford(cost: Int): Boolean = Rewards.afterSpending(_balance.value, cost) != null

    /**
     * Takes [cost] from the balance, or leaves it alone and returns false when it does not cover it.
     *
     * The caller decides what "no" means - on the daily screen it means a trip to the shop - which
     * is why this reports rather than throws.
     */
    fun spend(cost: Int): Boolean {
        val remaining = Rewards.afterSpending(_balance.value, cost) ?: return false
        write(remaining)
        return true
    }

    fun earn(amount: Int) {
        require(amount >= 0) { "Cannot earn a negative amount: $amount" }
        write(_balance.value + amount)
    }

    private fun write(value: Int) {
        preferences.edit { putInt(KEY_BALANCE, value) }
        _balance.value = value
    }

    private companion object {
        const val FILE = "ravel-wallet"
        const val KEY_BALANCE = "balance"

        /**
         * Enough to try both hints and still have room to waste some.
         *
         * A player who has never seen a hint cannot know whether it is worth buying, and one who
         * can only afford a single letter has to treat that letter as precious - so they hoard it,
         * never spend it, and never find out what they were being sold. Ten letters or four words
         * is enough to spend badly once and still be curious.
         *
         * Only the default for a wallet that has never been written, so raising it hands the new
         * amount to anyone who has not spent a coin yet. Harmless before release; after it, this
         * number has to be frozen and a grant written instead.
         */
        const val STARTING_BALANCE = 1000
    }
}
