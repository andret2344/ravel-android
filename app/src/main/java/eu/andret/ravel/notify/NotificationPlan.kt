package eu.andret.ravel.notify

import eu.andret.ravel.game.DailyDate
import java.util.Calendar

/**
 * What the player has chosen about the daily notification, and what the game has had to remember
 * to keep that choice honest.
 *
 * @param enabled whether a notification may be sent at all. Off until the player says yes - on
 *   every API level, including those below 33 where the system would not have asked.
 * @param automatic whether the time follows the player ([learnedMinute]) or is [manualMinute].
 * @param manualMinute the time the player picked, in minutes after local midnight. Kept while
 *   [automatic] is on, so switching back to the fixed time finds the one they had chosen.
 * @param learnedMinute when today's daily was last solved, in minutes after local midnight, or null
 *   before the first solve. Stored as it happened, quiet hours and all - [NotificationPlan.dueMinute]
 *   decides what to do with a solve at three in the morning, so the rule can change without the
 *   record having to.
 * @param sentDay the last day a notification went out, as a [DailyDate] ordinal, until
 *   [NotificationPlan.settle] has counted it.
 * @param ignored how many notifications in a row went out on days that were then not solved.
 * @param offered whether the first offer - "remind you about the daily?" - has been shown. Once.
 * @param offerBack whether the game switched the notifications off itself and owes the player the
 *   one question about switching them back on.
 */
data class NotificationPrefs(
    val enabled: Boolean = false,
    val automatic: Boolean = true,
    val manualMinute: Int = NotificationPlan.DEFAULT_MINUTE,
    val learnedMinute: Int? = null,
    val sentDay: Long? = null,
    val ignored: Int = 0,
    val offered: Boolean = false,
    val offerBack: Boolean = false,
)

/** A moment to wake up at: a day, and a time on it in minutes after local midnight. */
data class Slot(val day: Long, val minute: Int)

/** What the one notification of the day says, most important first. See [NotificationPlan.kindOf]. */
enum class NoticeKind {
    /** Yesterday was missed and can still be bought back - today only. */
    REPAIR,

    /** A run worth keeping ends at midnight unless today is solved. */
    STREAK,

    /** Today's board is part-way through. */
    STARTED,

    /** Today's board has not been opened. */
    FRESH,
}

/** The two questions asked after a solve. See [NotificationPlan.offerAfterSolve]. */
enum class NoticeOffer {
    /** The first one: would the player like to be reminded at all. */
    FIRST,

    /** The game switched the notifications off after [NotificationPlan.IGNORED_LIMIT] unanswered. */
    BACK,
}

/**
 * Every rule about the daily notification, as arithmetic over [NotificationPrefs] and a few facts
 * about today - so all of it is testable without a device, the way [eu.andret.ravel.game.Puzzle]
 * and [eu.andret.ravel.game.StreakRun] are. `DailyNotifications` feeds it what is stored and does
 * what it answers; nothing else decides when or whether a notification goes out.
 *
 * The design, argued in `docs/DECISIONS.md` ("The daily notification"):
 *
 * - **One a day at most**, and none once today is solved. The decision is made when the alarm goes
 *   off, against the state then, not when it was set.
 * - **Automatic time is when the daily was last solved**, not when it was opened: a player who
 *   keeps their habit has finished by then and never sees one.
 * - **Quiet hours, 22:00 to 08:00, bind the automatic time only.** A fixed time the player picked
 *   is theirs, however late.
 * - **The app on screen postpones; it never cancels.** An hour at a time, until 22:00 for the
 *   automatic time and midnight for a fixed one.
 * - **Three ignored in a row switch them off** - visibly, in the options - and the next solve asks
 *   once whether to switch them back on.
 */
object NotificationPlan {
    const val MINUTES_PER_DAY = 24 * 60

    /** Where the automatic time starts, and where it goes back to. */
    const val DEFAULT_MINUTE = 19 * 60

    /** Quiet hours, for the automatic time: from 22:00 ... */
    const val QUIET_FROM = 22 * 60

    /** ... until 08:00. */
    const val QUIET_UNTIL = 8 * 60

    /** How far a notification moves when the game is on screen at its time. */
    const val POSTPONE_MINUTES = 60

    /** Unanswered notifications in a row after which they switch themselves off. */
    const val IGNORED_LIMIT = 3

    /** The shortest run worth a word in the notification - for keeping it, and for buying it back. */
    const val STREAK_FROM = 3

    /** Solved dailies before the first offer. The second, not the first: one could be curiosity. */
    const val OFFER_AFTER_SOLVED = 2

    /** Whether [minute] falls in quiet hours. */
    fun isQuiet(minute: Int): Boolean = minute >= QUIET_FROM || minute < QUIET_UNTIL

    /**
     * When today's notification is due.
     *
     * The fixed time as picked. The automatic one is the last solve, unless that fell in quiet
     * hours - 23:40, or after midnight, or at six in the morning - in which case it is the default.
     * One rule rather than one per case: a time the player plays at but would not want to be woken
     * at is a time this cannot use, whichever side of midnight it is on.
     */
    fun dueMinute(prefs: NotificationPrefs): Int = when {
        !prefs.automatic -> prefs.manualMinute
        prefs.learnedMinute == null || isQuiet(prefs.learnedMinute) -> DEFAULT_MINUTE
        else -> prefs.learnedMinute
    }

    /**
     * The latest a postponed notification may still go out today: the start of quiet hours for the
     * automatic time, midnight for a fixed one - after midnight it would be about another board.
     */
    fun limitMinute(prefs: NotificationPrefs): Int =
        if (prefs.automatic) QUIET_FROM else MINUTES_PER_DAY

    /**
     * Whether the notification goes out now.
     *
     * Not while the game is on screen - the player is looking at the tab already, and a banner over
     * the board would be noise. That is a postponement, not a refusal: [next] puts the next attempt
     * an hour on.
     */
    fun shouldSend(
        prefs: NotificationPrefs,
        today: Long,
        nowMinute: Int,
        solvedToday: Boolean,
        onScreen: Boolean,
    ): Boolean = prefs.enabled &&
        !solvedToday &&
        prefs.sentDay != today &&
        !onScreen &&
        nowMinute >= dueMinute(prefs) &&
        nowMinute < limitMinute(prefs)

    /**
     * When to wake up next, or null when nothing should.
     *
     * Today while today is still owed one: at its time if that is ahead, and an hour from now if it
     * has passed without one going out - which is both the postponement and the recovery for an
     * alarm that was lost or set late. Past the day's limit, or once today is solved or has had
     * its one, it is tomorrow's time.
     */
    fun next(prefs: NotificationPrefs, today: Long, nowMinute: Int, solvedToday: Boolean): Slot? {
        if (!prefs.enabled) return null
        val due = dueMinute(prefs)
        if (!solvedToday && prefs.sentDay != today) {
            val at = if (due > nowMinute) due else nowMinute + POSTPONE_MINUTES
            if (at < limitMinute(prefs)) return Slot(today, at)
        }
        return Slot(DailyDate.shifted(today, 1), due)
    }

    /**
     * Counts the last notification sent, once its day is over.
     *
     * Ignored means its day ended unsolved - a tap on it is not what counts, since a player who
     * swiped it away and then solved the board from the launcher did what it asked. Today's is
     * never counted: it can still be answered.
     *
     * The [IGNORED_LIMIT]th in a row turns the notifications off and owes the player one question
     * about turning them back on ([NoticeOffer.BACK]).
     */
    fun settle(prefs: NotificationPrefs, today: Long, solved: (Long) -> Boolean): NotificationPrefs {
        val sent = prefs.sentDay ?: return prefs
        if (sent >= today) return prefs
        if (solved(sent)) return prefs.copy(sentDay = null, ignored = 0)
        val ignored = prefs.ignored + 1
        return if (ignored >= IGNORED_LIMIT) {
            prefs.copy(sentDay = null, ignored = 0, enabled = false, offerBack = true)
        } else {
            prefs.copy(sentDay = null, ignored = ignored)
        }
    }

    /** Today's daily was just solved, at [minute]: that is the new automatic time. */
    fun onSolved(prefs: NotificationPrefs, minute: Int): NotificationPrefs =
        prefs.copy(learnedMinute = minute, sentDay = null, ignored = 0)

    /**
     * The question to ask after a solve, or null.
     *
     * [NoticeOffer.BACK] first: it is owed. [NoticeOffer.FIRST] once, from the
     * [OFFER_AFTER_SOLVED]th solved daily, and never to a player who already has them on.
     */
    fun offerAfterSolve(prefs: NotificationPrefs, totalSolved: Int): NoticeOffer? = when {
        prefs.offerBack -> NoticeOffer.BACK
        !prefs.enabled && !prefs.offered && totalSolved >= OFFER_AFTER_SOLVED -> NoticeOffer.FIRST
        else -> null
    }

    /** The question was shown. Each is asked once, whatever the answer. */
    fun offerShown(prefs: NotificationPrefs, offer: NoticeOffer): NotificationPrefs = when (offer) {
        NoticeOffer.FIRST -> prefs.copy(offered = true)
        NoticeOffer.BACK -> prefs.copy(offerBack = false)
    }

    /**
     * The player switched them on or off themselves.
     *
     * Either way the count starts again, and a question the game owed about switching them back on
     * is answered: the player just did.
     */
    fun switched(prefs: NotificationPrefs, enabled: Boolean): NotificationPrefs =
        prefs.copy(enabled = enabled, ignored = 0, sentDay = null, offerBack = false)

    /**
     * What the notification says, the most pressing first.
     *
     * A repair before everything: it is the one offer in the game with a real deadline. Both streak
     * kinds need a run of [STREAK_FROM] - a two-day run is not worth an urgent sentence.
     *
     * @param repairStake the run a repair of yesterday would save, zero when there is none.
     * @param streak the run that ends today unless today is solved.
     * @param remaining words left on today's board when it is part-way through, otherwise null.
     */
    fun kindOf(repairStake: Int, streak: Int, remaining: Int?): NoticeKind = when {
        repairStake >= STREAK_FROM -> NoticeKind.REPAIR
        streak >= STREAK_FROM -> NoticeKind.STREAK
        remaining != null && remaining > 0 -> NoticeKind.STARTED
        else -> NoticeKind.FRESH
    }

    /** Minutes after local midnight at [millis]. */
    fun minuteOf(millis: Long): Int {
        val calendar = Calendar.getInstance().apply { timeInMillis = millis }
        return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
    }

    /**
     * [slot] as an instant, by the wall clock.
     *
     * Set through the calendar's fields rather than added to midnight, so on the day daylight
     * saving moves the clock 19:00 is still 19:00.
     */
    fun millisOf(slot: Slot): Long = DailyDate.calendarOf(slot.day).apply {
        set(Calendar.HOUR_OF_DAY, slot.minute / 60)
        set(Calendar.MINUTE, slot.minute % 60)
    }.timeInMillis
}
