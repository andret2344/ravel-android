package eu.andret.ravel.settings

import eu.andret.ravel.game.Rewards
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The daily reset, without a device.
 *
 * Only the rule is tested, not the storing of it: the storing is one preferences write, and the
 * part that can be silently wrong is what the stored pair means the next morning.
 */
class AdAllowanceTest {
    @Test
    fun `a device that has never watched an ad has the full allowance`() {
        assertEquals(
            Rewards.ADS_PER_DAY,
            AdAllowance.remainingFrom(storedDay = 0L, storedCount = 0, today = 20260809L),
        )
    }

    @Test
    fun `watches count against the day they were stored on`() {
        assertEquals(
            Rewards.ADS_PER_DAY - 1,
            AdAllowance.remainingFrom(storedDay = 20260809L, storedCount = 1, today = 20260809L),
        )
    }

    @Test
    fun `the allowance is used up once the cap is reached`() {
        assertEquals(
            0,
            AdAllowance.remainingFrom(
                storedDay = 20260809L,
                storedCount = Rewards.ADS_PER_DAY,
                today = 20260809L,
            ),
        )
    }

    @Test
    fun `yesterday's watches are not held against today`() {
        assertEquals(
            Rewards.ADS_PER_DAY,
            AdAllowance.remainingFrom(
                storedDay = 20260808L,
                storedCount = Rewards.ADS_PER_DAY,
                today = 20260809L,
            ),
        )
    }

    @Test
    fun `a day restored from a backup taken today keeps the watches already spent on it`() {
        assertEquals(
            Rewards.ADS_PER_DAY - 2,
            AdAllowance.remainingFrom(storedDay = 20260809L, storedCount = 2, today = 20260809L),
        )
    }

    @Test
    fun `a stored day in the future leaves nothing for today`() {
        assertEquals(
            0,
            AdAllowance.remainingFrom(storedDay = 20260810L, storedCount = 0, today = 20260809L),
        )
    }

    @Test
    fun `winding the clock back a month is still nothing for today`() {
        assertEquals(
            0,
            AdAllowance.remainingFrom(
                storedDay = 20260809L,
                storedCount = Rewards.ADS_PER_DAY,
                today = 20260709L,
            ),
        )
    }

    @Test
    fun `putting the clock right again restores the allowance rather than holding a grudge`() {
        assertEquals(
            Rewards.ADS_PER_DAY,
            AdAllowance.remainingFrom(
                storedDay = 20260809L,
                storedCount = Rewards.ADS_PER_DAY,
                today = 20260810L,
            ),
        )
    }

    @Test
    fun `a count above the cap reads as nothing left rather than as a debt`() {
        assertEquals(
            0,
            AdAllowance.remainingFrom(
                storedDay = 20260809L,
                storedCount = Rewards.ADS_PER_DAY + 5,
                today = 20260809L,
            ),
        )
    }
}
