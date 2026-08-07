package eu.andret.ravel

import android.app.Application
import android.content.ComponentCallbacks2
import eu.andret.ravel.generator.Ravel
import eu.andret.ravel.settings.DailyProgress
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
     */
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

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Everything from RUNNING_CRITICAL up: the app is either off screen or the device is short
        // enough on memory that a rebuildable cache is the first thing that should go.
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) Ravel.forget()
    }

    @Deprecated("Kept for API levels that still deliver it; onTrimMemory covers the rest.")
    override fun onLowMemory() {
        @Suppress("DEPRECATION")
        super.onLowMemory()
        Ravel.forget()
    }
}
