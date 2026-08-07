package eu.andret.ravel.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import eu.andret.ravel.RavelApplication
import eu.andret.ravel.generator.Language
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * The options screen's one setting, and the one warning it has to give before applying it.
 *
 * Thin on purpose. [eu.andret.ravel.settings.LanguageSettings] already holds the choice, already
 * publishes it and already tells the stores about it; what is left for a view model is the question
 * the screen cannot answer alone - whether there is a half-finished board that the switch will cost
 * the player.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = (application as RavelApplication).language

    private val levels = (application as RavelApplication).levels

    private val daily = (application as RavelApplication).progress

    /** The language picked in the options, or null while the game follows the phone. */
    val choice: StateFlow<Language?> = settings.choice

    /**
     * Whether a board would be thrown away by switching now.
     *
     * True for a level part-way through **or** a daily part-way through, because a switch reaches
     * both: the level's ladder swaps to the other language's, and the day's saved board is in the
     * language it was started in and cannot be laid over the new grid.
     *
     * What the player loses is the grid, not the record. The level number, the day's solved mark,
     * the streak and the coins all survive - but the hints they paid for on that board do not come
     * back, and the counters keep charging them, which is the part worth a dialog. The alternative,
     * refunding the coins, would make a language switch a way to undo a purchase.
     */
    val unfinished: StateFlow<Boolean> = combine(levels.state, daily.state) { level, day ->
        level.started || day.board != null
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * Applies [language], or hands the game back to the phone when it is null.
     *
     * Everything that has to happen afterwards is somebody else's collector: the stores swap ladders,
     * the board screens regenerate, and the activity rebuilds itself so the interface is read in the
     * new language too. Nothing is sequenced here, because a screen that had to order those would be
     * a screen that could get the order wrong.
     */
    fun choose(language: Language?) {
        settings.chosen = language
    }
}
