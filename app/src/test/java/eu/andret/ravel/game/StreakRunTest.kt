package eu.andret.ravel.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreakRunTest {

    private val today = DailyDate.of(2026, 11, 20)

    /** [count] consecutive days ending [daysAgo] before today. */
    private fun run(count: Int, daysAgo: Int): Set<Long> =
        (daysAgo until daysAgo + count).map { DailyDate.shifted(today, -it) }.toSet()

    private fun ago(days: Int): Long = DailyDate.shifted(today, -days)

    @Test
    fun `a run ending yesterday is waiting, not broken`() {
        assertEquals(4, StreakRun.length(run(4, daysAgo = 1), emptySet(), today))
    }

    @Test
    fun `a missed yesterday ends the run`() {
        assertEquals(0, StreakRun.length(run(4, daysAgo = 2), emptySet(), today))
    }

    @Test
    fun `a repaired day bridges the run without counting in it`() {
        // Three solved, the gap bought back, two more solved: five days in a row, not six.
        val solved = run(2, daysAgo = 0) + run(3, daysAgo = 3)
        val repaired = setOf(ago(2))
        assertEquals(5, StreakRun.length(solved, repaired, today))
        assertTrue(StreakRun.includesRepair(solved, repaired, today))
    }

    @Test
    fun `a run with no repair in it is not marked`() {
        assertFalse(StreakRun.includesRepair(run(3, daysAgo = 0), emptySet(), today))
    }

    @Test
    fun `the longest run counts across a repair too`() {
        val solved = run(2, daysAgo = 0) + run(3, daysAgo = 3) + run(1, daysAgo = 10)
        assertEquals(5, StreakRun.longest(solved, setOf(ago(2))))
    }

    @Test
    fun `yesterday can be repaired when the day before it was solved`() {
        val solved = run(4, daysAgo = 2)
        assertEquals(ago(1), StreakRun.repairable(solved, emptySet(), declined = null, today = today))
        assertEquals(4, StreakRun.stake(solved, emptySet(), declined = null, today = today))
    }

    @Test
    fun `two missed days cannot be repaired`() {
        assertNull(StreakRun.repairable(run(4, daysAgo = 3), emptySet(), declined = null, today = today))
    }

    @Test
    fun `a gap left past the next midnight stays a gap`() {
        // Missed two days ago, came back yesterday and solved it without repairing: the old gap is
        // two days back now and not on offer, and yesterday was solved, so nothing is.
        val solved = run(3, daysAgo = 3) + setOf(ago(1))
        assertNull(StreakRun.repairable(solved, emptySet(), declined = null, today = today))
        assertEquals(1, StreakRun.length(solved, emptySet(), today))
    }

    @Test
    fun `solving today first leaves the repair on offer`() {
        val solved = run(4, daysAgo = 2) + setOf(today)
        assertEquals(ago(1), StreakRun.repairable(solved, emptySet(), declined = null, today = today))
        assertEquals(1, StreakRun.length(solved, emptySet(), today))
        assertEquals(5, StreakRun.length(solved, setOf(ago(1)), today))
    }

    @Test
    fun `a declined gap is not offered again`() {
        assertNull(StreakRun.repairable(run(4, daysAgo = 2), emptySet(), declined = ago(1), today = today))
    }

    @Test
    fun `a repaired gap is not offered again`() {
        val solved = run(4, daysAgo = 2)
        assertNull(StreakRun.repairable(solved, setOf(ago(1)), declined = null, today = today))
    }

    @Test
    fun `one repair per cycle`() {
        // Days 1-2, a repair, days 3-4, and now a second gap: day 5 would be in the same cycle as
        // the first repair, so it is refused.
        val solved = run(2, daysAgo = 2) + run(2, daysAgo = 5)
        val repaired = setOf(ago(4))
        assertNull(StreakRun.repairable(solved, repaired, declined = null, today = today))
        assertEquals(0, StreakRun.stake(solved, repaired, declined = null, today = today))
    }

    @Test
    fun `a new cycle gets a new repair`() {
        // Days 1-5, a repair, days 6-7 - the cycle closed with its seventh day - and now a gap
        // before day 8, which belongs to the next cycle.
        val solved = run(2, daysAgo = 2) + run(5, daysAgo = 5)
        val repaired = setOf(ago(4))
        assertEquals(ago(1), StreakRun.repairable(solved, repaired, declined = null, today = today))
        assertEquals(7, StreakRun.stake(solved, repaired, declined = null, today = today))
    }

    @Test
    fun `a repair at the turn of a cycle belongs to the cycle after it`() {
        // Seven days, a repair before day 8, days 8-9, and a gap before day 10: same cycle as the
        // repair, so refused - the turn of the cycle is not a way to fit two into one.
        val solved = run(2, daysAgo = 2) + run(7, daysAgo = 5)
        val repaired = setOf(ago(4))
        assertNull(StreakRun.repairable(solved, repaired, declined = null, today = today))
    }
}
