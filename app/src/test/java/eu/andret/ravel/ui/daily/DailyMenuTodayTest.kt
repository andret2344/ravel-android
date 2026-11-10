package eu.andret.ravel.ui.daily

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the daily menu does when the date changes under it, and what a board opened on a day that is
 * over is allowed to do. Both halves of the fix for a menu that went stale overnight - see
 * [DailyMenuViewModel].
 */
class DailyMenuTodayTest {

    private val august = MonthGrid.containing(AUG_31)
    private val september = MonthGrid.containing(SEP_1)

    @Test
    fun `a calendar on today follows today into the next month`() {
        val focus = CalendarFocus(august, AUG_31).afterDayChange(from = AUG_31, to = SEP_1)
        assertEquals(CalendarFocus(september, SEP_1), focus)
    }

    @Test
    fun `a calendar on today follows today within the month`() {
        val focus = CalendarFocus(august, AUG_30).afterDayChange(from = AUG_30, to = AUG_31)
        assertEquals(CalendarFocus(august, AUG_31), focus)
    }

    @Test
    fun `a day the player picked stays picked`() {
        val focus = CalendarFocus(august, AUG_12).afterDayChange(from = AUG_30, to = AUG_31)
        assertEquals(CalendarFocus(august, AUG_12), focus)
    }

    @Test
    fun `a day picked in today's old month goes when the month does`() {
        // The month follows today because it was showing today's month; the twelfth of August has
        // no cell in September, so the card stops describing it.
        val focus = CalendarFocus(august, AUG_12).afterDayChange(from = AUG_31, to = SEP_1)
        assertEquals(CalendarFocus(september, null), focus)
    }

    @Test
    fun `a player reading an older month is left reading it`() {
        val july = august.shifted(-1)
        val focus = CalendarFocus(july, JUL_14).afterDayChange(from = AUG_31, to = SEP_1)
        assertEquals(CalendarFocus(july, JUL_14), focus)
    }

    @Test
    fun `a clock wound back never leaves a day in the future selected or on screen`() {
        val focus = CalendarFocus(september, SEP_1).afterDayChange(from = SEP_1, to = AUG_31)
        assertEquals(CalendarFocus(august, AUG_31), focus)

        val picked = CalendarFocus(august, AUG_31).afterDayChange(from = SEP_1, to = AUG_30)
        assertNull(picked.selected)
    }

    @Test
    fun `nothing moves when the day has not changed`() {
        val focus = CalendarFocus(august, AUG_12)
        assertEquals(focus, focus.afterDayChange(from = AUG_31, to = AUG_31))
    }

    @Test
    fun `only today's board opens to be played`() {
        assertFalse(
            DailyRoute.opensForReview(reviewRequested = false, ordinal = SEP_1, today = SEP_1, solved = false),
        )
        assertTrue(
            DailyRoute.opensForReview(reviewRequested = true, ordinal = SEP_1, today = SEP_1, solved = false),
        )
    }

    @Test
    fun `yesterday's board asked for as a live one opens as a review`() {
        // The stale-menu morning: the route says play, the day is over.
        assertTrue(
            DailyRoute.opensForReview(reviewRequested = false, ordinal = AUG_31, today = SEP_1, solved = false),
        )
    }

    @Test
    fun `a board from a later day than the clock opens as a review`() {
        // A clock wound back under a restored route. Not playable either: it is not today.
        assertTrue(
            DailyRoute.opensForReview(reviewRequested = false, ordinal = SEP_1, today = AUG_31, solved = false),
        )
    }

    @Test
    fun `today's board asked for as a live one opens as a review once it is solved`() {
        // A route restored after the process died, on a day finished before it died.
        assertTrue(
            DailyRoute.opensForReview(reviewRequested = false, ordinal = SEP_1, today = SEP_1, solved = true),
        )
    }

    private companion object {
        const val JUL_14 = 20260714L
        const val AUG_12 = 20260812L
        const val AUG_30 = 20260830L
        const val AUG_31 = 20260831L
        const val SEP_1 = 20260901L
    }
}
