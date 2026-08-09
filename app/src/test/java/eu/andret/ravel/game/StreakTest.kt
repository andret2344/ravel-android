package eu.andret.ravel.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StreakTest {

    @Test
    fun `the seventh day is the only one worth more`() {
        assertEquals(List(Streak.CYCLE - 1) { Streak.DAY } + Streak.SEVENTH, Streak.ladder)
    }

    @Test
    fun `a week of showing up stays in the same range as a week of solving`() {
        // The guard on the whole design: if the streak ever pays several times what playing pays,
        // the optimal player is one who opens the app and closes it again.
        //
        // A band rather than two figures. This test used to assert 110 and 70, which made it fail
        // the day the economy was rescaled - correctly, in the sense that something changed, and
        // uselessly, in the sense that it had nothing to say about whether the change was wrong.
        // The claim in Streak's own documentation is a relationship, so that is what is checked,
        // and it still catches the failure worth catching: a streak that quietly outgrows the game.
        val week = Streak.ladder.sum()
        val solving = Rewards.FOR_COMPLETING * Streak.CYCLE
        assertTrue(
            "A week of streak pays $week against $solving for a week of solving",
            week in solving..(solving * 2),
        )
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
