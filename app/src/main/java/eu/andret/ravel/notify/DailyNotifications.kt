package eu.andret.ravel.notify

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import eu.andret.ravel.MainActivity
import eu.andret.ravel.R
import eu.andret.ravel.game.DailyDate
import eu.andret.ravel.game.PuzzleId
import eu.andret.ravel.game.PuzzleMode
import eu.andret.ravel.game.PuzzleSpec
import eu.andret.ravel.notify.NotificationPlan.minuteOf
import eu.andret.ravel.settings.DailyProgress
import eu.andret.ravel.settings.DailyProgressState
import eu.andret.ravel.settings.LanguageSettings
import eu.andret.ravel.settings.NotificationSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The daily notification: the alarm that wakes the app, the decision when it does, and the one
 * notification that may come of it.
 *
 * Every rule is in [NotificationPlan]; this is the Android half - [AlarmManager], the channels, the
 * posting - and it does what the plan answers. One alarm is ever set, and it is set again after
 * anything that can move it: a launch, a solve, a change in the options, the alarm itself, and the
 * system broadcasts [NotificationReceiver] listens for (a reboot clears alarms, an update may).
 *
 * The alarm is inexact ([AlarmManager.setAndAllowWhileIdle]) on purpose. An exact one needs
 * `SCHEDULE_EXACT_ALARM`, which the player has to grant by hand, or `USE_EXACT_ALARM`, which Play
 * keeps for alarm clocks and calendars - and a reminder about a puzzle a few minutes late is still
 * the same reminder.
 *
 * Nothing here runs a search or loads a dictionary: the board's size comes from [PuzzleSpec], which
 * is arithmetic over the day, and how many words are left comes from the saved board.
 */
class DailyNotifications(
    private val context: Context,
    private val settings: NotificationSettings,
    private val progress: DailyProgress,
) {
    /** What the player chose, for the options tab to draw. */
    val prefs: StateFlow<NotificationPrefs> get() = settings.prefs

    /**
     * Whether the game is on screen now. Written by `MainActivity` as it starts and stops - it is
     * the only activity - and read when the alarm goes off: a notification due while the player is
     * in the game is postponed rather than shown. See [NotificationPlan.shouldSend].
     */
    var onScreen: Boolean = false

    private val _offer = MutableStateFlow<NoticeOffer?>(null)

    /**
     * The question owed after the solve that just happened, until it is answered. Not stored: a
     * question lost with the process comes back after the next solve, since what it is about -
     * [NotificationPrefs.offered] and [NotificationPrefs.offerBack] - is.
     */
    val offer: StateFlow<NoticeOffer?> = _offer.asStateFlow()

    /**
     * Whether the system will show what this posts: the permission on Android 13 and later, and the
     * app's notifications not blocked in the system settings on any version.
     */
    fun permitted(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED)

    /**
     * Counts what has to be counted and sets the alarm again. Safe to call at any time and as often
     * as anything likes: it only ever replaces the one alarm.
     */
    fun reschedule() {
        val today = settle()
        if (settings.prefs.value.enabled) ensureChannels()
        arm(today)
    }

    /** The alarm went off: send if the plan says so, then set the next one. */
    fun fire() {
        val today = settle()
        val state = progress.state.value
        val send = NotificationPlan.shouldSend(
            prefs = settings.prefs.value,
            today = today,
            nowMinute = minuteOf(System.currentTimeMillis()),
            solvedToday = state.isSolved(today),
            onScreen = onScreen,
        )
        // Recorded only when something was actually shown. A notification the system blocked was
        // never seen, and counting it as ignored would switch off notifications that never arrived.
        if (send && post(state, today)) settings.update { it.copy(sentDay = today) }
        arm(today)
    }

    /**
     * Today's daily was just solved, for the first time. Called by the board that paid for it.
     *
     * Takes down a notification still showing - it is no longer true - learns the time, and decides
     * whether a question is owed.
     */
    fun solved() {
        settings.update { NotificationPlan.onSolved(it, minuteOf(System.currentTimeMillis())) }
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        _offer.value = NotificationPlan.offerAfterSolve(
            settings.prefs.value,
            progress.state.value.totalSolved,
        )
        reschedule()
    }

    /**
     * The question was answered or put away. Either way it is not asked again; switching the
     * notifications on, if that was the answer, is a separate call - it may have to wait for the
     * system's permission dialog.
     */
    fun closeOffer(offer: NoticeOffer) {
        settings.update { NotificationPlan.offerShown(it, offer) }
        _offer.value = null
    }

    fun setEnabled(enabled: Boolean) {
        settings.update { NotificationPlan.switched(it, enabled) }
        if (!enabled) NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        reschedule()
    }

    fun setAutomatic(automatic: Boolean) {
        settings.update { it.copy(automatic = automatic) }
        reschedule()
    }

    /** Picking a time is choosing the fixed time, so it switches the automatic one off. */
    fun setManualMinute(minute: Int) {
        settings.update { it.copy(manualMinute = minute, automatic = false) }
        reschedule()
    }

    /** Recounts the day and settles the last notification sent. Returns today. */
    private fun settle(): Long {
        // The state in memory may be from before a midnight the process slept through.
        progress.refresh()
        val today = DailyDate.today()
        val state = progress.state.value
        settings.update { NotificationPlan.settle(it, today, state::isSolved) }
        return today
    }

    private fun arm(today: Long) {
        val slot = NotificationPlan.next(
            prefs = settings.prefs.value,
            today = today,
            nowMinute = minuteOf(System.currentTimeMillis()),
            solvedToday = progress.state.value.isSolved(today),
        )
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val wake = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, NotificationReceiver::class.java).setAction(ACTION_ALARM),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (slot == null) {
            alarms.cancel(wake)
        } else {
            alarms.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                NotificationPlan.millisOf(slot),
                wake,
            )
        }
    }

    /**
     * Shows today's one notification. Returns whether it was shown.
     *
     * In the language the game is played in, not the phone's: the same forced locale every screen
     * is read in - see [LanguageSettings.localize].
     */
    private fun post(state: DailyProgressState, today: Long): Boolean {
        // Checked here in this form, beside the call it guards, as well as in [permitted]: it is
        // the shape the platform's lint recognizes.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        ensureChannels()
        val text = LanguageSettings.localize(context)
        val resources = text.resources

        val board = state.boardFor(today)
        val kind = NotificationPlan.kindOf(
            repairStake = if (state.repairable != null) state.repairStake else 0,
            streak = state.streak,
            remaining = board?.let { it.wordCount - it.found.size },
        )
        val (title, body) = when (kind) {
            NoticeKind.REPAIR -> text.getString(R.string.notify_repair_title) to
                resources.getQuantityString(
                    R.plurals.notify_repair_body,
                    state.repairStake,
                    state.repairStake,
                )

            NoticeKind.STREAK ->
                resources.getQuantityString(R.plurals.notify_streak_title, state.streak, state.streak) to
                    text.getString(R.string.notify_streak_body)

            NoticeKind.STARTED -> {
                val left = board?.let { it.wordCount - it.found.size } ?: 0
                text.getString(R.string.notify_started_title) to
                    resources.getQuantityString(R.plurals.notify_started_body, left, left)
            }

            NoticeKind.FRESH -> text.getString(R.string.notify_fresh_title) to
                text.getString(
                    R.string.notify_fresh_body,
                    PuzzleSpec.of(PuzzleId(PuzzleMode.DAILY, today)).size,
                )
        }
        val channel = when (kind) {
            NoticeKind.REPAIR, NoticeKind.STREAK -> CHANNEL_STREAK
            NoticeKind.STARTED, NoticeKind.FRESH -> CHANNEL_DAILY
        }
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_OPEN_DAILY)
                // Into the task that is already there, onto the one activity in it, which is told
                // through onNewIntent instead of being built a second time on top of itself.
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                ),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_tab_daily_selected)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
        return true
    }

    /**
     * The two channels, so the player can silence one from the system settings and keep the other:
     * the plain "today's board is waiting", and the two about a streak.
     *
     * Created again every time rather than once: it is idempotent, and it is what renames them in
     * the system settings after a language switch.
     */
    private fun ensureChannels() {
        val text = LanguageSettings.localize(context)
        NotificationManagerCompat.from(context).createNotificationChannelsCompat(
            listOf(
                NotificationChannelCompat.Builder(CHANNEL_DAILY, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName(text.getString(R.string.notify_channel_daily))
                    .setDescription(text.getString(R.string.notify_channel_daily_note))
                    .build(),
                NotificationChannelCompat.Builder(CHANNEL_STREAK, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName(text.getString(R.string.notify_channel_streak))
                    .setDescription(text.getString(R.string.notify_channel_streak_note))
                    .build(),
            ),
        )
    }

    companion object {
        /** What the alarm's broadcast carries, to tell it from the system's own. */
        const val ACTION_ALARM = "eu.andret.ravel.action.DAILY_NOTIFICATION"

        /** What a tap on the notification carries into `MainActivity`. */
        const val ACTION_OPEN_DAILY = "eu.andret.ravel.action.OPEN_DAILY"

        /** One id: there is never more than one of these on screen. */
        private const val NOTIFICATION_ID = 1

        /** Channel ids are permanent once a device has seen them; never rename them. */
        private const val CHANNEL_DAILY = "daily"
        private const val CHANNEL_STREAK = "streak"
    }
}
