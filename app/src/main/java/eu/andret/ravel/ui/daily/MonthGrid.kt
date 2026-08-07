package eu.andret.ravel.ui.daily

import eu.andret.ravel.game.DailyDate
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale

/**
 * One month arranged into the seven-column grid a calendar is drawn on.
 *
 * The arithmetic is here rather than inside the composable because it is the part that can be wrong
 * in ways nobody sees until February: the number of days in a month, the leap year, and above all
 * where the first of the month falls in a week whose first day is Monday in Warsaw and Sunday in
 * Chicago. A composable that recomputed all of that on every recomposition would also be doing it
 * dozens of times per scroll.
 *
 * @param leading blank cells before the first of the month, so the first lands under its own
 *   weekday. Never a negative modulo - the locale's first day can be later in the week than the
 *   first of the month is.
 * @param length days in the month, from the calendar rather than a table, because the table is
 *   wrong every fourth February.
 */
data class MonthGrid(
    val year: Int,
    val month: Int,
    val leading: Int,
    val length: Int,
) {
    /** Whole weeks, so the grid keeps its shape and does not jump height between months. */
    val cells: Int = ((leading + length + WEEK - 1) / WEEK) * WEEK

    /** The day at [index] in the grid, or null for the padding at either end. */
    fun ordinalAt(index: Int): Long? {
        val day = index - leading + 1
        return if (day in 1..length) DailyDate.of(year, month, day) else null
    }

    /** Local midnight on the first, which is what a screen needs to name the month in its own style. */
    val startMillis: Long get() = DailyDate.startOfDay(DailyDate.of(year, month, 1))

    fun shifted(months: Int): MonthGrid {
        val total = year * 12 + (month - 1) + months
        return of(total / 12, total % 12 + 1)
    }

    /** True when this month is later than the one [ordinal] falls in: a month with no history yet. */
    fun isAfter(ordinal: Long): Boolean =
        year * 100 + month > DailyDate.year(ordinal) * 100 + DailyDate.month(ordinal)

    /** True when [ordinal] is one of this month's own days, and so has a cell in this grid. */
    fun contains(ordinal: Long): Boolean =
        DailyDate.year(ordinal) == year && DailyDate.month(ordinal) == month

    companion object {
        const val WEEK = 7

        fun of(year: Int, month: Int): MonthGrid {
            val calendar = Calendar.getInstance().apply {
                clear()
                set(year, month - 1, 1)
            }
            return MonthGrid(
                year = year,
                month = month,
                // Modulo of a difference that can go either way, brought back into 0..6 by adding a
                // whole week before taking it.
                leading = (calendar.get(Calendar.DAY_OF_WEEK) - calendar.firstDayOfWeek + WEEK) % WEEK,
                length = calendar.getActualMaximum(Calendar.DAY_OF_MONTH),
            )
        }

        /** The month [ordinal] falls in. */
        fun containing(ordinal: Long): MonthGrid =
            of(DailyDate.year(ordinal), DailyDate.month(ordinal))

        /**
         * The column headings, rotated to start on the locale's own first day.
         *
         * Taken from the platform rather than written into `strings.xml`: every locale the app is
         * ever translated into already has these, correctly abbreviated, and a hand-written set
         * would be one more thing to translate and one more thing to get wrong.
         */
        fun weekdayLabels(locale: Locale = Locale.getDefault()): List<String> {
            // Indexed by Calendar.SUNDAY..SATURDAY, which start at 1, so element zero is empty.
            val names = DateFormatSymbols.getInstance(locale).shortWeekdays
            val first = Calendar.getInstance(locale).firstDayOfWeek
            return (0 until WEEK).map { names[(first - 1 + it) % WEEK + 1] }
        }
    }
}
