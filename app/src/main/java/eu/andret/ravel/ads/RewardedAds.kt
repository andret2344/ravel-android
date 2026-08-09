package eu.andret.ravel.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import eu.andret.ravel.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The one ad this game shows: a rewarded video the player asked for, in exchange for coins.
 *
 * Rewarded and nothing else, because that is what the privacy policy committed to in writing - no
 * banners, no interstitials, nothing the player did not ask for. That is also why this class has no
 * "show when convenient" entry point: [show] takes an activity and is called from a button press,
 * and there is no other way in.
 *
 * One instance for the process, held by the application, on the same argument the wallet is held
 * there. An ad takes seconds to fetch and is worth having ready before the player asks; a copy per
 * screen would each fetch their own and throw away all but the one that happened to be on screen.
 *
 * ## Why it starts itself rather than being started
 *
 * Nothing may be requested until the consent flow says so, and that answer arrives asynchronously
 * some time after launch. Rather than have the shop poll for it or the activity remember to call
 * back, this watches [AdConsent.canRequestAds] and starts the SDK the moment it turns true. The flag
 * turning true is a permission and not a request - see [AdConsent] - so all that happens here is one
 * ad being fetched so the button is live when the player reaches it.
 */
class RewardedAds(
    context: Context,
    allowed: StateFlow<Boolean>,
    private val scope: CoroutineScope,
) {
    private val application = context.applicationContext

    private val unitId = application.getString(R.string.ad_unit_rewarded)

    private val _state = MutableStateFlow(State.BLOCKED)

    val state: StateFlow<State> = _state.asStateFlow()

    /** The ad fetched and not yet shown. One at a time: the shop offers one button. */
    private var loaded: RewardedAd? = null

    /**
     * Whether the SDK has been started. The SDK tolerates a second `initialize`, but a second one
     * would race the first's completion callback and fetch two ads to throw one away.
     */
    private var started = false

    init {
        scope.launch {
            allowed.collect { permitted -> if (permitted) start() }
        }
    }

    /**
     * What the shop button should say.
     *
     * A refusal and a failed fetch are different states rather than one "not now", because they are
     * different answers to the player: [BLOCKED] is a choice they made and can change from settings,
     * [UNAVAILABLE] is nobody having an ad to show and is worth a retry.
     */
    enum class State {
        /** No consent, so nothing may be requested. The shop points at the settings screen. */
        BLOCKED,

        /** Consent is in, an ad is on its way. */
        LOADING,

        /** An ad is in hand and the button works. */
        READY,

        /** On screen. The button is disabled rather than gone, so the card does not jump. */
        SHOWING,

        /** The fetch failed, or the ad network had nothing. Retryable. */
        UNAVAILABLE,
    }

    /**
     * Starts the SDK, then fetches the first ad.
     *
     * `MobileAds.initialize` reads and writes storage and can take hundreds of milliseconds on a
     * cold start, which is Google's own reason for keeping it off the main thread. Its completion
     * callback is not guaranteed to come back on one particular thread either, so the fetch is
     * posted back onto the main dispatcher - `RewardedAd.load` is a main-thread call.
     *
     * The dispatcher is named rather than injected, which Sonar would rather it were not. There is
     * no test on the other side of the seam it asks for and cannot be: everything this method
     * touches is the real ads SDK, which reaches storage and the network of its own accord, and the
     * thread it wants this call off is a real one. The `launch` inside the callback takes no
     * dispatcher at all - it inherits [scope]'s, which is the main one, which is where
     * `RewardedAd.load` has to be.
     */
    @Suppress("kotlin:S6310")
    private fun start() {
        if (started) return
        started = true
        _state.value = State.LOADING
        // Before initialisation, not after: the configuration has to be in place when the first
        // request goes out, and the first request is made by load() the moment initialisation
        // finishes. See res/values/ads.xml for why an unlisted developer device is a suspended
        // AdMob account rather than a wasted impression.
        val testDevices = application.resources.getStringArray(R.array.ad_test_devices).toList()
        if (testDevices.isNotEmpty()) {
            MobileAds.setRequestConfiguration(
                RequestConfiguration.Builder().setTestDeviceIds(testDevices).build(),
            )
        }
        scope.launch(Dispatchers.IO) {
            MobileAds.initialize(application) {
                scope.launch { load() }
            }
        }
    }

    /**
     * Fetches one ad, if there is not one in hand already.
     *
     * Public because the shop offers a retry after [State.UNAVAILABLE], and because a failure is
     * otherwise terminal: nothing else in the app would ever ask again.
     */
    fun load() {
        if (!started || loaded != null || _state.value == State.SHOWING) return
        _state.value = State.LOADING
        RewardedAd.load(
            application,
            unitId,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    loaded = ad
                    _state.value = State.READY
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loaded = null
                    _state.value = State.UNAVAILABLE
                }
            },
        )
    }

    /**
     * Shows the ad in hand, and pays out only if the player actually earns it.
     *
     * @param onEarned runs when the SDK says the reward was earned - watched far enough to count,
     *   which the ad network decides. It runs while the ad is still on screen rather than after it
     *   closes, on purpose: a process killed between earning and dismissing would otherwise be a
     *   player who watched an ad for nothing, and the wallet writes through to storage immediately.
     *   It can only run once per call, since the ad is dropped before it is shown.
     *
     * Does nothing when no ad is ready. The button that calls this is disabled in that state, so
     * reaching here means a race with a fetch that has just failed, and the honest response to that
     * is silence rather than an error the player cannot act on.
     */
    fun show(activity: Activity, onEarned: () -> Unit) {
        val ad = loaded ?: return
        // Dropped before it is shown, not after: an ad may be shown exactly once, and holding the
        // reference until dismissal is holding something a second button press could try to reuse.
        loaded = null
        _state.value = State.SHOWING
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                // Out of SHOWING first, and not for the sake of the button: load() refuses to fetch
                // while an ad is on screen, and this callback arrives with the state still saying
                // one is. Then straight into the next fetch, so a player who wants a second ad is
                // not made to wait for a request that could have been in flight all along.
                _state.value = State.LOADING
                load()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                _state.value = State.UNAVAILABLE
                load()
            }
        }
        ad.show(activity) { onEarned() }
    }
}
