package eu.andret.ravel.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StreakTest {

    @Test
    fun `the seventh day is the only one worth more`() {
        assertEquals(listOf(10, 10, 10, 10, 10, 10, 50), Streak.ladder)
    }

    @Test
    fun `a week of showing up stays in the same range as a week of solving`() {
        // The guard on the whole design: if the streak ever pays several times what playing pays,
        // the optimal player is one who opens the app and closes it again.
        val week = Streak.ladder.sum()
        val solving = Rewards.FOR_COMPLETING * Streak.CYCLE
        assertEquals(110, week)
        assertEquals(70, solving)
    }

    @Test
    fun `the ladder restarts on the eighth day`() {
        assertEquals(1, Streak.dayInCycle(8))
        assertEquals(7, Streak.dayInCycle(7))
        assertEquals(1, Streak.dayInCycle(15))
    }

    @Test
    fun `no streak is no rung`() {
        assertEquals(0, Streak.dayInCycle(0))
    }

    @Test
    fun `the day on offer follows the day just taken, and wraps`() {
        assertEquals(1, Streak.nextDay(0))
        assertEquals(4, Streak.nextDay(3))
        // A seventh day played leaves the player offering the first day of a new cycle, not an
        // eighth rung - which is the case the streak card cannot get from streak plus one.
        assertEquals(1, Streak.nextDay(7))
    }

    @Test
    fun `a day outside the cycle is a bug and says so`() {
        assertThrows(IllegalArgumentException::class.java) { Streak.rewardFor(0) }
        assertThrows(IllegalArgumentException::class.java) { Streak.rewardFor(Streak.CYCLE + 1) }
        assertThrows(IllegalArgumentException::class.java) { Streak.dayInCycle(-1) }
    }
}
