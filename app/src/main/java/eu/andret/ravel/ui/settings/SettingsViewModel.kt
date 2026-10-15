package eu.andret.ravel.ui.settings

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import eu.andret.ravel.RavelApplication
import eu.andret.ravel.generator.Language
import eu.andret.ravel.settings.ThemeChoice
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * The options screen's settings, and the one warning any of them has to give before being applied.
 *
 * Thin on purpose. Each setting is already held, published and acted on by a store of its own -
 * [eu.andret.ravel.settings.LanguageSettings], [eu.andret.ravel.settings.HapticSettings],
 * [eu.andret.ravel.ads.AdConsent] - so what is left for a view model is the one question no store
 * can answer alone: whether there is a half-finished board that a language switch will cost the
 * player. That question belongs to the language and to nothing else here, which is why the haptics
 * switch below is two lines and the language is the rest of the file.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = (application as RavelApplication).language

    private val levels = (application as RavelApplication).levels

    private val daily = (application as RavelApplication).progress

    private val consent = (application as RavelApplication).consent

    private val haptics = (application as RavelApplication).haptics

    /** The language being played and read in, which is what the picker marks as chosen. */
    val language: StateFlow<Language> = settings.current

    /** Whether the board buzzes. See [eu.andret.ravel.settings.HapticSettings]. */
    val hapticsEnabled: StateFlow<Boolean> = haptics.enabled

    private val themes = (application as RavelApplication).theme

    val theme: StateFlow<ThemeChoice> = themes.choice

    /** Applies at once, on every screen; nothing is lost by it, so nothing is confirmed. */
    fun chooseTheme(choice: ThemeChoice) = themes.choose(choice)

    /**
     * Whether this device is one that has to be offered a way back into the consent form.
     *
     * The consent SDK decides it, and it is false for most of the world. The privacy policy promises
     * the choice can be changed afterwards, which is a promise to the players who were asked in the
     * first place; showing the row to everyone else would open a form that reports there is nothing
     * to show. See [eu.andret.ravel.ads.AdConsent.privacyOptionsRequired].
     */
    val privacyOptions: StateFlow<Boolean> = consent.privacyOptionsRequired

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
     * Applies [language].
     *
     * Everything that has to happen afterwards is somebody else's collector: the stores swap ladders,
     * the board screens regenerate, and the activity rebuilds itself so the interface is read in the
     * new language too. Nothing is sequenced here, because a screen that had to order those would be
     * a screen that could get the order wrong.
     */
    fun choose(language: Language) {
        settings.choose(language)
    }

    /**
     * Turns board haptics on or off.
     *
     * No warning and no confirmation, unlike the language: nothing is lost by it and it is undone by
     * touching the same switch again. It also takes effect on a board the player is already in the
     * middle of, since the setting is a flow the board is reading rather than a value it copied.
     */
    fun setHaptics(value: Boolean) = haptics.setEnabled(value)

    /**
     * Reopens the consent form.
     *
     * Takes an [Activity] for the same reason the shop's ad does - a form is a dialog and needs
     * something on screen to sit in front of - and for the same reason never holds one: this view
     * model outlives the activity that made it.
     *
     * Nothing is done with the outcome here. Whatever the player changes is written by the SDK and
     * read back by [eu.andret.ravel.ads.AdConsent], which republishes it to everything watching,
     * including the shop's ad. A screen that tried to apply the result itself would be a second
     * copy of an answer that has one owner.
     */
    fun changePrivacyOptions(activity: Activity) = consent.showPrivacyOptions(activity)
}
