package eu.andret.ravel.game

import org.junit.Assert.assertEquals
import org.junit.Test

class DailyDateTest {

    @Test
    fun `an ordinal reads as the date it names`() {
        val ordinal = DailyDate.of(2026, 8, 7)
        assertEquals(20260807L, ordinal)
        assertEquals(2026, DailyDate.year(ordinal))
        assertEquals(8, DailyDate.month(ordinal))
        assertEquals(7, DailyDate.day(ordinal))
    }

    @Test
    fun `stepping a day crosses the end of a month rather than counting past it`() {
        // The reason this goes through Calendar: the day after 20260831 is 20260901, and adding one
        // to the number gives 20260832.
        assertEquals(20260901L, DailyDate.shifted(20260831L, 1))
        assertEquals(20260831L, DailyDate.shifted(20260901L, -1))
    }

    @Test
    fun `stepping crosses the end of a year`() {
        assertEquals(20270101L, DailyDate.shifted(20261231L, 1))
        assertEquals(20261231L, DailyDate.shifted(20270101L, -1))
    }

    @Test
    fun `February has as many days as the year says it does`() {
        assertEquals(20240229L, DailyDate.shifted(20240228L, 1))
        assertEquals(20250301L, DailyDate.shifted(20250228L, 1))
    }

    @Test
    fun `midnight on a day belongs to that day`() {
        val ordinal = DailyDate.of(2026, 8, 7)
        assertEquals(ordinal, DailyDate.ordinalOf(DailyDate.startOfDay(ordinal)))
    }

    @Test
    fun `a run of days walked forwards and back arrives where it started`() {
        var day = DailyDate.of(2026, 1, 1)
        repeat(400) { day = DailyDate.shifted(day, 1) }
        repeat(400) { day = DailyDate.shifted(day, -1) }
        assertEquals(DailyDate.of(2026, 1, 1), day)
    }
}
