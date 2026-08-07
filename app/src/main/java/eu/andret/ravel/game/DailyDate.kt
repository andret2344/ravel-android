package eu.andret.ravel.game

import java.util.Calendar

/**
 * The daily mode's calendar: how a date becomes an ordinal, and how an ordinal becomes a date again.
 *
 * A day names itself by its calendar date, as `yyyyMMdd`, and not by an instant. Two players in
 * different time zones are on different dates for a few hours either way - nothing can fix that
 * short of picking one zone for the world - but every player who calls it the seventh of August
 * gets the seventh of August's board, which is the promise a daily challenge actually makes.
 * Deriving it from milliseconds would instead hand out different boards to players on the same date
 * in different zones.
 *
 * This lives beside [PuzzleId] rather than in the screen that shows a date, because three things now
 * have to agree on what a day is called: the board being played, the streak counting consecutive
 * days, and the calendar drawing a month of them. Two of them computing it separately is two chances
 * to disagree by one day.
 *
 * Ordinals are readable on purpose - 20260807 is the seventh of August 2026 - which makes a wrong
 * one obvious in a log or a debugger instead of being a large opaque number.
 */
object DailyDate {
    /** Today's ordinal in the phone's own time zone: the day rolls over when the player's does. */
    fun today(): Long = ordinalOf(Calendar.getInstance())

    fun ordinalOf(calendar: Calendar): Long =
        calendar.get(Calendar.YEAR) * 10_000L +
            (calendar.get(Calendar.MONTH) + 1) * 100L +
            calendar.get(Calendar.DAY_OF_MONTH)

    /** The ordinal of the date [millis] falls on, locally. */
    fun ordinalOf(millis: Long): Long =
        ordinalOf(Calendar.getInstance().apply { timeInMillis = millis })

    /**
     * Local midnight on [ordinal], which is what a screen needs to format the date in the phone's
     * own style.
     */
    fun startOfDay(ordinal: Long): Long = calendarOf(ordinal).timeInMillis

    /**
     * [ordinal] as a calendar set to local midnight.
     *
     * Cleared first: a calendar built from "now" and then had its date set would keep the current
     * hour, and two ordinals compared through it would differ by the time of day rather than by the
     * day.
     */
    fun calendarOf(ordinal: Long): Calendar = Calendar.getInstance().apply {
        clear()
        set(year(ordinal), month(ordinal) - 1, day(ordinal))
    }

    /**
     * The ordinal [days] after [ordinal], negative to go back.
     *
     * Done through [Calendar] rather than by adding to the number itself, because the number is a
     * date and not a count: the day after 20260831 is 20260901, not 20260832.
     */
    fun shifted(ordinal: Long, days: Int): Long =
        ordinalOf(calendarOf(ordinal).apply { add(Calendar.DAY_OF_MONTH, days) })

    fun year(ordinal: Long): Int = (ordinal / 10_000).toInt()

    /** One-based, as it is written: January is 1, matching the `MM` in `yyyyMMdd`. */
    fun month(ordinal: Long): Int = (ordinal / 100 % 100).toInt()

    fun day(ordinal: Long): Int = (ordinal % 100).toInt()

    /** The ordinal of the [day]th of a month, for a calendar grid that has a month and needs cells. */
    fun of(year: Int, month: Int, day: Int): Long = year * 10_000L + month * 100L + day
}
