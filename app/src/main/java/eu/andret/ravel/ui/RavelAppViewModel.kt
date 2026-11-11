package eu.andret.ravel.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import eu.andret.ravel.RavelApplication
import eu.andret.ravel.game.DailyDate
import eu.andret.ravel.notify.NoticeOffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
     * on the board will not recognise "play today's puzzle" as the board they are half way through.
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
}
