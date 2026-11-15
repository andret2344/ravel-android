package eu.andret.ravel.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import eu.andret.ravel.RavelApplication
import eu.andret.ravel.notify.NotificationPrefs
import eu.andret.ravel.settings.SystemHaptics
import eu.andret.ravel.settings.ThemeChoice
import eu.andret.ravel.generator.Language
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * The options screen's settings, and the one warning any of them has to give before being applied.
 *
 * Thin on purpose. Each setting is already held, published and acted on by a store of its own -
 * [eu.andret.ravel.settings.LanguageSettings], [eu.andret.ravel.settings.HapticSettings],
 * [eu.andret.ravel.settings.ThemeSettings] - so what is left for a view model is the one question no
 * store can answer alone: whether there is a half-finished board that a language switch will cost
 * the player. That question belongs to the language and to nothing else here, which is why the
 * haptics switch below is two lines and the language is the rest of the file. The privacy switches
 * have a page and a view model of their own, [PrivacyViewModel].
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = (application as RavelApplication).language

    private val levels = (application as RavelApplication).levels

    private val daily = (application as RavelApplication).progress

    private val haptics = (application as RavelApplication).haptics

    private val tutorial = (application as RavelApplication).tutorial

    private val theme = (application as RavelApplication).theme

    /** The colors the player picked. See [eu.andret.ravel.settings.ThemeSettings]. */
    val themeChoice: StateFlow<ThemeChoice> = theme.choice

    /** The language being played and read in, which is what the picker marks as chosen. */
    val language: StateFlow<Language> = settings.current

    /** Whether the board buzzes. See [eu.andret.ravel.settings.HapticSettings]. */
    val hapticsEnabled: StateFlow<Boolean> = haptics.enabled

    private val _systemHaptics = MutableStateFlow(SystemHaptics.read(application))

    /**
     * Whether the phone lets the board vibrate at all. When it does not, the screen grays out the
     * haptics switch and says why - see [SystemHaptics].
     */
    val systemHaptics: StateFlow<SystemHaptics> = _systemHaptics.asStateFlow()

    /** Reads [systemHaptics] again. Called each time the screen comes into view. */
    fun refreshSystemHaptics() {
        _systemHaptics.value = SystemHaptics.read(getApplication())
    }

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
        level.boardFor(level.current) != null || day.board != null
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

    private val notifications = (application as RavelApplication).notifications

    /** The daily notification as chosen. See [eu.andret.ravel.notify.NotificationPlan]. */
    val notificationPrefs: StateFlow<NotificationPrefs> = notifications.prefs

    private val _notificationsPermitted = MutableStateFlow(notifications.permitted())

    /**
     * Whether the system will show a notification at all - the permission, and the app not blocked
     * in the system settings. Read again each time the screen comes into view, like
     * [systemHaptics], because it is changed in another app.
     */
    val notificationsPermitted: StateFlow<Boolean> = _notificationsPermitted.asStateFlow()

    fun refreshNotificationsPermitted() {
        _notificationsPermitted.value = notifications.permitted()
    }

    fun setNotifications(enabled: Boolean) = notifications.setEnabled(enabled)

    fun setNotificationTimeAutomatic(automatic: Boolean) = notifications.setAutomatic(automatic)

    fun setNotificationMinute(minute: Int) = notifications.setManualMinute(minute)

    /** Puts the tutorial back over the game, at once. See [eu.andret.ravel.settings.TutorialSettings]. */
    fun replayTutorial() = tutorial.restart()

    /** Applies at once, on every screen; nothing is lost by it, so nothing is confirmed. */
    fun chooseTheme(choice: ThemeChoice) = theme.choose(choice)

    /**
     * Wipes all progress, after the screen has had it confirmed. What survives, and why the coins
     * must, is [RavelApplication.resetProgress]'s to say.
     */
    fun resetProgress() = getApplication<RavelApplication>().resetProgress()
}
