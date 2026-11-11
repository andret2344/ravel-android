package eu.andret.ravel

import android.app.Application
import eu.andret.ravel.ads.AdConsent
import eu.andret.ravel.ads.RewardedAds
import eu.andret.ravel.game.PuzzleCache
import eu.andret.ravel.game.Puzzles
import eu.andret.ravel.generator.Ravel
import eu.andret.ravel.settings.AdAllowance
import eu.andret.ravel.settings.DailyGift
import eu.andret.ravel.settings.DailyReminder
import eu.andret.ravel.settings.DailyProgress
import eu.andret.ravel.settings.HapticSettings
import eu.andret.ravel.settings.HardModeSettings
import eu.andret.ravel.settings.LanguageSettings
import eu.andret.ravel.settings.LevelProgress
import eu.andret.ravel.settings.StoredDailyProgress
import eu.andret.ravel.settings.StoredBoards
import eu.andret.ravel.settings.StoredLevelProgress
import eu.andret.ravel.settings.ThemeSettings
import eu.andret.ravel.settings.TutorialSettings
import eu.andret.ravel.settings.Wallet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
     * Whether the player wants levels without lengths, chosen on the main tab and read by a level
     * when its board comes up - see [eu.andret.ravel.settings.LevelProgress.open].
     */
    val hardMode: HardModeSettings by lazy { HardModeSettings(this) }

    /** The theme the player picked, applied by the activity and chosen in the options tab. */
    val theme: ThemeSettings by lazy { ThemeSettings(this) }

    /**
     * Whether the launch prompt about an unsolved daily may appear. Read and written by the shell
     * alone today, and here anyway: a setting with its own instance per screen is the second answer
     * [language] warns about the moment a second screen offers the same switch.
     */
    val reminder: DailyReminder by lazy { DailyReminder(this) }

    /**
     * Whether the tutorial is owed, read by the shell that shows it and written by the options tab
     * that replays it - two places, one answer, as with [haptics]. A player who has already solved
     * something counts as taught; see [TutorialSettings.pendingFrom].
     */
    val tutorial: TutorialSettings by lazy {
        TutorialSettings(this) {
            levels.totals.value.levelsCompleted > 0 || progress.state.value.solved.isNotEmpty()
        }
    }

    /**
     * The daily history and the board in progress, for the same reason and with the same lifetime
     * as [wallet]: the menu draws the calendar from it while the board is writing today into it.
     */
    val progress: DailyProgress get() = storedProgress

    /** How far through the main mode the player is, on the same terms as [progress]. */
    val levels: LevelProgress get() = storedLevels

    // The concrete stores, kept to this class. Every screen is typed against the two interfaces,
    // which have no way to move the ladder; resetProgress is the one caller that needs more.
    private val storedProgress: StoredDailyProgress by lazy {
        StoredDailyProgress(this, language.current, scope)
    }

    private val storedLevels: StoredLevelProgress by lazy {
        StoredLevelProgress(this, language.current, scope)
    }

    /**
     * Wipes all progress in every language: both level ladders, the daily history with its streak,
     * and the lifetime statistics. The options tab's "reset progress", after a confirmation.
     *
     * **Coins stay, and so do today's ad allowance and gift.** Putting the balance back to the starting
     * amount would turn the reset into a coin faucet - spend, reset, spend again - which is the grant
     * `Wallet.STARTING_BALANCE` must never become. Settings stay too: they are preferences, not
     * progress.
     *
     * A board open on another tab finds out the way it does after a language switch: the level's
     * screen sees its level is no longer the one on offer and closes itself.
     */
    fun resetProgress() {
        storedLevels.clear()
        storedProgress.clear()
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

    /**
     * Boards built ahead of the player, on the process's own scope for the same reason [scope] is
     * here at all: a prefetch that died with the screen that asked for it would be exactly the work
     * this is meant to have finished by the time a screen wants it.
     */
    val puzzles: PuzzleCache by lazy { PuzzleCache(scope, StoredBoards(this)) }

    /**
     * Builds the dictionary before anything asks for a board.
     *
     * Collected rather than called once, because a language switch throws the trie away - see
     * [eu.andret.ravel.settings.LanguageSettings.choose] - and the first board after a switch would
     * otherwise pay to rebuild it in front of the player, which is the cost this exists to move.
     *
     * On [Dispatchers.Default] because [Puzzles.preload] blocks, and it is not a small block: on a
     * Redmi Note 10 5G it is about three seconds. Doing it here is what keeps those three seconds
     * off the first spinner the player sees rather than adding them to it.
     *
     * The dispatcher is named rather than injected, against Sonar's advice, for the reason given at
     * [scope]: Android builds this class with no arguments and no test builds it at all, so there is
     * no seam for an injected dispatcher to come through.
     */
    @Suppress("kotlin:S6310")
    override fun onCreate() {
        super.onCreate()
        scope.launch(Dispatchers.Default) {
            language.current.collect { Puzzles.preload(it) }
        }
    }

    val ads: RewardedAds by lazy { RewardedAds(this, consent.canRequestAds, scope) }

    /** Today's three watches, counted here so every screen sees the same three. */
    val adAllowance: AdAllowance by lazy { AdAllowance(this) }

    /** Today's free gift in the shop, here for the reason [adAllowance] is. */
    val dailyGift: DailyGift by lazy { DailyGift(this) }

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
