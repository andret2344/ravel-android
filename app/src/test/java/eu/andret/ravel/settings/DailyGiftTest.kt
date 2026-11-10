package eu.andret.ravel.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The gift's day rule, without a device - see [AdAllowanceTest] for why only the rule. */
class DailyGiftTest {
    @Test
    fun `a gift never claimed is there to take`() {
        assertTrue(DailyGift.availableFrom(claimedDay = 0L, today = 20261110L))
    }

    @Test
    fun `a gift claimed today is gone until tomorrow`() {
        assertFalse(DailyGift.availableFrom(claimedDay = 20261110L, today = 20261110L))
        assertTrue(DailyGift.availableFrom(claimedDay = 20261110L, today = 20261111L))
    }

    @Test
    fun `a clock wound back below the last claim gets nothing`() {
        // Claimed on the 11th, clock set back to the 10th: not a new day, a moved clock.
        assertFalse(DailyGift.availableFrom(claimedDay = 20261111L, today = 20261110L))
    }
}
