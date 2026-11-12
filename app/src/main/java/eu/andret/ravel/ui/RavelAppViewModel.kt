package eu.andret.ravel.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import eu.andret.ravel.RavelApplication
import eu.andret.ravel.game.DailyDate
import eu.andret.ravel.notify.NoticeOffer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * What the shell around the tabs knows: whether today's daily is still waiting, and whether the
 * player wants to be told about it.
 *
 * The whole reason this is a view model and not two remembered flags is the word "once". The prompt
 * is a launch-time question, and a rotation is not a launch: state remembered in the composable
 * would ask again every time the player turned the phone, and [androidx.compose.runtime.saveable]
 * state would survive a process death that *is* a new launch. A view model is the one lifetime that
 * matches - it outlives the configuration change and dies with the process.
 *
 * [prompt] is decided at construction rather than watched, for the same reason. The question is
 * "was the daily unsolved when the player opened the game", and a live answer would tear the dialog
 * away mid-sentence the moment the board behind it was finished.
 */
class RavelAppViewModel(application: Application) : AndroidViewModel(application) {
    private val reminder = (application as RavelApplication).reminder

    private val progress = (application as RavelApplication).progress

    /**
     * Whether the tutorial is over the shell. Live, unlike [prompt]: it is replayed from the options
     * tab in the middle of a session, and closed by the player rather than by a launch ending.
     */
    val tutorial: StateFlow<Boolean> = (application as RavelApplication).tutorial.pending

    /** The day the prompt is about, fixed here as every other screen fixes it. */
    val today: Long = DailyDate.today()

    /**
     * Whether today's board is one the player already started, rather than one they have not opened.
     *
     * The prompt says different things about the two, because they are different offers: one is an
     * invitation and the other is a reminder of something owed, and a player who left three words
     * on the board will not recognize "play today's puzzle" as the board they are half way through.
     */
    val started: Boolean = progress.state.value.isStarted(today)

    private val _remind = MutableStateFlow(reminder.enabled)

    /** Whether the checkbox in the prompt is ticked, mirroring what is stored. */
    val remind: StateFlow<Boolean> = _remind.asStateFlow()

    private val _prompt =
        MutableStateFlow(reminder.enabled && !progress.state.value.isSolved(today))

    /** Whether the question is currently on screen. */
    val prompt: StateFlow<Boolean> = _prompt.asStateFlow()

    /**
     * Writes the player's answer to "ask me every time" through immediately.
     *
     * Not held until the dialog is answered, because the dialog can be left without answering -
     * a tap outside it, or the back gesture - and a preference that only takes effect when the
     * player also picks a button is a preference that silently ignores half the people who set it.
     */
    fun remind(enabled: Boolean) {
        reminder.enabled = enabled
        _remind.value = enabled
    }

    fun dismissPrompt() {
        _prompt.value = false
    }

    private val _openDaily = MutableStateFlow(false)

    /**
     * Whether the shell owes the player today's board, because they tapped the daily notification.
     * Held until the shell has navigated, so it survives the consent screen in front of the game.
     */
    val openDaily: StateFlow<Boolean> = _openDaily.asStateFlow()

    /**
     * The notification was tapped. The launch prompt goes with it: the player has just said which
     * board they want, and asking them whether they want it would be asking twice.
     */
    fun openDaily() {
        _prompt.value = false
        _openDaily.value = true
    }

    fun openedDaily() {
        _openDaily.value = false
    }

    private val notifications = (application as RavelApplication).notifications

    /** The question owed after a solve, if any - see [eu.andret.ravel.notify.NotificationPlan.offerAfterSolve]. */
    val offer: StateFlow<NoticeOffer?> = notifications.offer

    /** Whether switching the notifications on can happen at once, without the system's dialog. */
    fun notificationsPermitted(): Boolean = notifications.permitted()

    /** The question was answered or put away; it is not asked again. */
    fun closeOffer(offer: NoticeOffer) = notifications.closeOffer(offer)

    /** The player said yes, and the system - where it was asked - agreed. */
    fun enableNotifications() = notifications.setEnabled(true)

    private val gift = (application as RavelApplication).dailyGift

    /**
     * The day the tab dots are about. Unlike [today], which is the launch prompt's day and fixed on
     * purpose, this one moves: the shell lives as long as the process, the process outlives
     * midnight, and a dot still saying "today's board is done" the next morning would be the menu
     * bug of 2026-11-08 all over again. Kept current by [followToday].
     */
    private val day = MutableStateFlow(DailyDate.today())

    /**
     * Whether the daily tab carries a dot: today's board is not solved yet, whether or not it was
     * opened. A started board is still something owed, and the dot goes only when the solve does.
     */
    val dailyWaiting: StateFlow<Boolean> =
        combine(progress.state, day) { state, today -> !state.isSolved(today) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, !progress.state.value.isSolved(day.value))

    /**
     * Whether the shop tab carries a dot: today's gift is still there to take. Only the gift, not
     * the ad watches left - an ad is an offer, and a dot that stays lit until the player has
     * watched three of them is a dot that pressures rather than informs.
     */
    val giftWaiting: StateFlow<Boolean> = gift.available

    /**
     * Re-reads the date, the daily state and the gift now and again at every midnight, for as long
     * as the shell is started - the same loop as `DailyMenuViewModel.followToday`, for the same
     * reason. Refreshing the gift here also turns the shop tile back on at midnight for a player
     * who is looking at it, which the shop's own refresh, run only when the tab opens, would miss.
     */
    suspend fun followToday() {
        while (true) {
            progress.refresh()
            gift.refresh()
            day.value = DailyDate.today()
            delay(DailyDate.millisUntilNextDay(System.currentTimeMillis()) + DailyDate.MIDNIGHT_MARGIN_MILLIS)
        }
    }
}
