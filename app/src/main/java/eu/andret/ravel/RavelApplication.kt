package eu.andret.ravel

import android.app.Application
import eu.andret.ravel.ads.AdConsent
import eu.andret.ravel.ads.RewardedAds
import eu.andret.ravel.generator.Ravel
import eu.andret.ravel.settings.AdAllowance
import eu.andret.ravel.settings.DailyProgress
import eu.andret.ravel.settings.HapticSettings
import eu.andret.ravel.settings.LanguageSettings
import eu.andret.ravel.settings.LevelProgress
import eu.andret.ravel.settings.StoredDailyProgress
import eu.andret.ravel.settings.StoredLevelProgress
import eu.andret.ravel.settings.Wallet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Gives the dictionary memory back when the system asks for it.
 *
 * A loaded dictionary is a trie of up to 25 000 words and their reversals - about 17 MB on Polish
 * hard - cached inside [Ravel] for as long as the process lives. That is the right trade while the
 * player is on a board, since rebuilding it costs a visible tenth of a second, and the wrong one as
 * soon as the app is off screen: 17 MB of an idle process is 17 MB of reason for the system to kill
 * it, and the reload it saves would have happened during a launch that is already loading.
 */
class RavelApplication : Application() {
    /**
     * One wallet for the whole process. Screens reach it through their view models rather than
     * building their own: two wallets over one file would drift apart the moment a hint was bought
     * on one screen while another was showing the balance.
     */
    val wallet: Wallet by lazy { Wallet(this) }

    /**
     * Where the two stores below are told about a language switch, and where it stops.
     *
     * Tied to the process rather than to a screen because that is what these collectors watch over:
     * a store that stopped following the setting when the last board was closed would be reading the
     * wrong ladder the next time one opened. Nothing is ever cancelled here for the same reason -
     * the scope ends when the process does.
     *
     * The dispatcher is named here rather than injected, against Sonar's advice, because there is
     * nothing on the other side of the seam it is asking for: no test builds a [RavelApplication],
     * and the whole point of this scope is that it is the one the process's stores live on.
     */
    @Suppress("kotlin:S6310")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * The one language setting, for the whole process.
     *
     * One instance and not one per screen, on the argument [wallet] makes about two wallets over one
     * file - and more sharply, because this one is written to. It also picks which ladder [levels] is
     * reading, so a second copy that had not heard about a switch would be a second answer to "which
     * level am I on".
     */
    val language: LanguageSettings by lazy { LanguageSettings(this) }

    /**
     * Whether the board may buzz, for the same reason [language] is here: it is written from the
     * options tab and read on a board, and those are two screens that must not each hold their own
     * answer. It shares a preference file with [language] and not an instance - see
     * [eu.andret.ravel.settings.SETTINGS_FILE].
     */
    val haptics: HapticSettings by lazy { HapticSettings(this) }

    /**
     * The daily history and the board in progress, for the same reason and with the same lifetime
     * as [wallet]: the menu draws the calendar from it while the board is writing today into it.
     */
    val progress: DailyProgress by lazy {
        StoredDailyProgress(this, language.current, scope)
    }

    /** How far through the main mode the player is, on the same terms as [progress]. */
    val levels: LevelProgress by lazy {
        StoredLevelProgress(this, language.current, scope)
    }

    /**
     * What this device has said about ads, and the ad itself.
     *
     * Process-wide for the reasons everything above is, plus one of their own: fetching an ad takes
     * seconds and the SDK holds a single initialised instance whatever this code does, so a copy
     * per screen would be several screens each waiting out the same fetch to use one of the
     * results. The shop reaches both through its view model.
     *
     * [ads] is built from [consent] rather than beside it, so there is no way to hold one without
     * the other and no way for an ad to be requested before the answer to [AdConsent.canRequestAds]
     * exists - see [RewardedAds] for why it starts itself off that flow instead of being started.
     */
    val consent: AdConsent by lazy { AdConsent(this, scope) }

    val ads: RewardedAds by lazy { RewardedAds(this, consent.canRequestAds, scope) }

    /** Today's three watches, counted here so every screen sees the same three. */
    val adAllowance: AdAllowance by lazy { AdAllowance(this) }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Everything from UI_HIDDEN up, which is to say: the app is off screen.
        //
        // This used to name TRIM_MEMORY_RUNNING_CRITICAL, and read as a response to memory
        // pressure. It has not been one since API 34 - "apps are not notified of this level since
        // API level 34", and the same sentence is on every other RUNNING_* and on BACKGROUND's
        // heavier neighbors. onLowMemory, which used to be overridden here for the older devices
        // that still delivered it, carries the blunter version: "since API level 34 this is never
        // called". Neither is a hook any more, so an override of it is a release path that runs
        // only on hardware from before Android 14.
        //
        // What is still delivered is UI_HIDDEN and BACKGROUND, and UI_HIDDEN is the moment this
        // cache should go regardless of what the device is short of: a rebuildable 17 MB in a
        // process nobody is looking at buys nothing and costs the process its place in the LRU
        // list. The old constant happened to be numerically below both, so the branch did keep
        // firing - by arithmetic, not by saying what it meant.
        //
        // Nothing visible is paid for dropping it this eagerly. A board being resumed is already
        // in memory or comes back from JSON; the reload lands on the next Puzzles.generate, which
        // is off the main thread behind a spinner already.
        if (level >= TRIM_MEMORY_UI_HIDDEN) Ravel.forget()
    }
}
