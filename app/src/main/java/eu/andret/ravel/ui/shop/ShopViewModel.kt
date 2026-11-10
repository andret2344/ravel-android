package eu.andret.ravel.ui.shop

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import eu.andret.ravel.RavelApplication
import eu.andret.ravel.ads.RewardedAds
import eu.andret.ravel.game.Rewards
import kotlinx.coroutines.flow.StateFlow

/**
 * The shop's view of the wallet, and of the one ad it is allowed to offer.
 *
 * It holds nothing of its own. The wallet, the ad and the day's allowance all live on the
 * application, because all three are answers that have to be the same on every screen - see
 * [RavelApplication]. What this adds is the order the three are touched in, which is the part that
 * would be wrong if a composable did it.
 */
class ShopViewModel(application: Application) : AndroidViewModel(application) {
    private val ravel = application as RavelApplication

    init {
        // The first ad of the process is fetched when the shop is first opened, not at launch - see
        // RewardedAds. A view model is built once per visit to the tab at most, which is all want()
        // needs: only the first call does anything.
        ravel.ads.want()
    }

    val balance: StateFlow<Int> = ravel.wallet.balance

    val adState: StateFlow<RewardedAds.State> = ravel.ads.state

    /** Watches left today. Drives what the card says as much as whether the button works. */
    val watchesLeft: StateFlow<Int> = ravel.adAllowance.remaining

    /** Whether today's gift is still there to take. */
    val giftAvailable: StateFlow<Boolean> = ravel.dailyGift.available

    /**
     * Re-counts the day, for a screen that is being looked at again.
     *
     * The allowance and the gift cache their answers and a process outlives midnights - see
     * [eu.andret.ravel.settings.AdAllowance.refresh]. The shop is the only screen that shows them,
     * so it is the only one that has to ask.
     */
    fun refresh() {
        ravel.adAllowance.refresh()
        ravel.dailyGift.refresh()
    }

    /** Takes today's gift and pays it - paid only when the claim went through, so once a day. */
    fun claimGift() {
        if (ravel.dailyGift.claim()) ravel.wallet.earn(Rewards.DAILY_GIFT)
    }

    /**
     * Plays the ad, and pays for it once the player has earned it.
     *
     * The order is the whole reason this is not in the composable. The allowance is booked first
     * and the coins are only added if that booking succeeded: reversing it would pay a player whose
     * fourth watch of the day should not have counted, and doing them independently would let a
     * booked watch go unpaid. Both happen inside the reward callback, so an ad that was started and
     * abandoned costs nothing and pays nothing.
     *
     * The `false` branch is unreachable through the button, which is disabled at zero. It is
     * checked anyway because the two states can disagree for one tap - a day that rolled over while
     * the screen was open shows yesterday's count - and the disagreement should cost the player a
     * tap rather than the game its cap.
     *
     * Takes an [Activity] because a full-screen ad needs one. That is also the reason it cannot be
     * held: the view model outlives the activity, so it is passed in per press and never stored.
     */
    fun watch(activity: Activity) = ravel.ads.show(activity) {
        if (ravel.adAllowance.consume()) ravel.wallet.earn(Rewards.FOR_WATCHING_AD)
    }

    /** Asks for another ad after a failed fetch. Nothing else in the app would ever ask again. */
    fun retry() = ravel.ads.load()
}
