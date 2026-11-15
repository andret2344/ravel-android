package eu.andret.ravel.ui.level

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import eu.andret.ravel.RavelApplication
import eu.andret.ravel.analytics.AnalyticsEvent
import eu.andret.ravel.game.LevelModifier
import eu.andret.ravel.settings.ModifierSettings
import eu.andret.ravel.settings.LevelProgressState
import kotlinx.coroutines.flow.StateFlow

/**
 * What the ladder in front of the main mode knows: which level is next, and what is in the wallet.
 *
 * Holds no puzzle and starts no generation. The board is a screen away and a second or two of work,
 * and starting it here would spend that on every player who opened the tab to see where they were.
 */
class LevelMenuViewModel(application: Application) : AndroidViewModel(application) {
    val state: StateFlow<LevelProgressState> = (application as RavelApplication).levels.state

    val balance: StateFlow<Int> = (application as RavelApplication).wallet.balance

    private val modifiers = (application as RavelApplication).modifiers

    /** What the player has chosen for levels not yet opened - see [ModifierSettings]. */
    val chosen: StateFlow<Set<LevelModifier>> = modifiers.chosen

    private val telemetry = (application as RavelApplication).telemetry

    fun setModifier(modifier: LevelModifier, enabled: Boolean) {
        modifiers.set(modifier, enabled)
        telemetry.log(AnalyticsEvent.ModifierSet(modifier, enabled))
    }
}
