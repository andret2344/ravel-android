package eu.andret.ravel.notify

import eu.andret.ravel.notify.NotificationPlan.DEFAULT_MINUTE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPlanTest {
    private val today = 20261111L
    private val yesterday = 20261110L
    private val tomorrow = 20261112L

    private val on = NotificationPrefs(enabled = true)

    private fun at(hour: Int, minute: Int = 0) = hour * 60 + minute

    // The time

    @Test
    fun `the automatic time is the last solve`() {
        assertEquals(at(8, 15), NotificationPlan.dueMinute(on.copy(learnedMinute = at(8, 15))))
    }

    @Test
    fun `the automatic time starts at the default before the first solve`() {
        assertEquals(DEFAULT_MINUTE, NotificationPlan.dueMinute(on))
    }

    @Test
    fun `a solve in quiet hours sends the automatic time back to the default on either side of midnight`() {
        listOf(at(22), at(23, 40), at(0, 30), at(6), at(7, 59)).forEach { solved ->
            assertEquals(DEFAULT_MINUTE, NotificationPlan.dueMinute(on.copy(learnedMinute = solved)))
        }
        assertEquals(at(8), NotificationPlan.dueMinute(on.copy(learnedMinute = at(8))))
        assertEquals(at(21, 59), NotificationPlan.dueMinute(on.copy(learnedMinute = at(21, 59))))
    }

    @Test
    fun `a fixed time is kept however late it is`() {
        val manual = on.copy(automatic = false, manualMinute = at(23, 30), learnedMinute = at(8))
        assertEquals(at(23, 30), NotificationPlan.dueMinute(manual))
    }

    // Sending

    @Test
    fun `it goes out at its time while today is unsolved`() {
        assertTrue(send(on, now = DEFAULT_MINUTE))
    }

    @Test
    fun `nothing goes out once today is solved`() {
        assertFalse(send(on, now = DEFAULT_MINUTE, solved = true))
    }

    @Test
    fun `nothing goes out twice in a day`() {
        assertFalse(send(on.copy(sentDay = today), now = at(20)))
    }

    @Test
    fun `nothing goes out while switched off`() {
        assertFalse(send(on.copy(enabled = false), now = DEFAULT_MINUTE))
    }

    @Test
    fun `nothing goes out while the game is on screen`() {
        assertFalse(send(on, now = DEFAULT_MINUTE, onScreen = true))
    }

    @Test
    fun `nothing goes out before its time`() {
        assertFalse(send(on, now = DEFAULT_MINUTE - 1))
    }

    @Test
    fun `the automatic time sends nothing in quiet hours, a fixed one sends until midnight`() {
        assertFalse(send(on, now = at(22)))
        val manual = on.copy(automatic = false, manualMinute = at(23))
        assertTrue(send(manual, now = at(23, 59)))
    }

    // The next alarm

    @Test
    fun `the next alarm is today's time while it is ahead`() {
        assertEquals(Slot(today, DEFAULT_MINUTE), next(on, now = at(9)))
    }

    @Test
    fun `the game on screen at its time moves it an hour on`() {
        assertEquals(Slot(today, DEFAULT_MINUTE + 60), next(on, now = DEFAULT_MINUTE))
        assertEquals(Slot(today, at(20, 30)), next(on, now = at(19, 30)))
    }

    @Test
    fun `postponing stops at quiet hours for the automatic time and moves to tomorrow`() {
        assertEquals(Slot(today, at(21, 59)), next(on, now = at(20, 59)))
        assertEquals(Slot(tomorrow, DEFAULT_MINUTE), next(on, now = at(21)))
    }

    @Test
    fun `postponing a fixed time stops at midnight`() {
        val manual = on.copy(automatic = false, manualMinute = at(22))
        assertEquals(Slot(today, at(23, 30)), next(manual, now = at(22, 30)))
        assertEquals(Slot(tomorrow, at(22)), next(manual, now = at(23)))
    }

    @Test
    fun `once today is solved or has had its one the next alarm is tomorrow`() {
        assertEquals(Slot(tomorrow, DEFAULT_MINUTE), next(on, now = at(9), solved = true))
        assertEquals(Slot(tomorrow, DEFAULT_MINUTE), next(on.copy(sentDay = today), now = at(9)))
    }

    @Test
    fun `tomorrow's alarm is at the time the solve just taught`() {
        val learned = NotificationPlan.onSolved(on, at(8, 15))
        assertEquals(Slot(tomorrow, at(8, 15)), next(learned, now = at(8, 15), solved = true))
    }

    @Test
    fun `switched off there is no alarm`() {
        assertNull(next(on.copy(enabled = false), now = at(9)))
    }

    // Ignored in a row

    @Test
    fun `a notification is counted only once its day is over`() {
        val sent = on.copy(sentDay = today)
        assertEquals(sent, NotificationPlan.settle(sent, today) { false })
    }

    @Test
    fun `a notification whose day was then solved was not ignored`() {
        val settled = NotificationPlan.settle(on.copy(sentDay = yesterday, ignored = 2), today) { true }
        assertEquals(0, settled.ignored)
        assertNull(settled.sentDay)
        assertTrue(settled.enabled)
    }

    @Test
    fun `the third ignored in a row switches them off and owes one question`() {
        var prefs = on
        repeat(2) {
            prefs = NotificationPlan.settle(prefs.copy(sentDay = yesterday), today) { false }
            assertTrue(prefs.enabled)
        }
        prefs = NotificationPlan.settle(prefs.copy(sentDay = yesterday), today) { false }
        assertFalse(prefs.enabled)
        assertTrue(prefs.offerBack)
        assertEquals(0, prefs.ignored)
    }

    @Test
    fun `a solve resets the count`() {
        val solved = NotificationPlan.onSolved(on.copy(ignored = 2), at(9))
        assertEquals(0, solved.ignored)
    }

    // The questions after a solve

    @Test
    fun `the first question comes with the second solved daily, not the first`() {
        val off = NotificationPrefs()
        assertNull(NotificationPlan.offerAfterSolve(off, totalSolved = 1))
        assertEquals(NoticeOffer.FIRST, NotificationPlan.offerAfterSolve(off, totalSolved = 2))
    }

    @Test
    fun `the first question is asked once`() {
        val asked = NotificationPlan.offerShown(NotificationPrefs(), NoticeOffer.FIRST)
        assertNull(NotificationPlan.offerAfterSolve(asked, totalSolved = 10))
    }

    @Test
    fun `nobody who has them on is asked`() {
        assertNull(NotificationPlan.offerAfterSolve(on, totalSolved = 10))
    }

    @Test
    fun `the question after switching off is asked once, before the first`() {
        val owed = NotificationPrefs(offerBack = true)
        assertEquals(NoticeOffer.BACK, NotificationPlan.offerAfterSolve(owed, totalSolved = 10))
        val asked = NotificationPlan.offerShown(owed, NoticeOffer.BACK)
        assertNull(NotificationPlan.offerAfterSolve(asked.copy(offered = true), totalSolved = 11))
    }

    @Test
    fun `switching them on by hand answers the question the game owed`() {
        val owed = NotificationPrefs(offerBack = true, ignored = 2)
        val switched = NotificationPlan.switched(owed, enabled = true)
        assertFalse(switched.offerBack)
        assertEquals(0, switched.ignored)
    }

    @Test
    fun `switching them off by hand owes nothing`() {
        val switched = NotificationPlan.switched(on, enabled = false)
        assertNull(NotificationPlan.offerAfterSolve(switched.copy(offered = true), totalSolved = 10))
    }

    // What it says

    @Test
    fun `a repair worth saving comes before everything`() {
        assertEquals(NoticeKind.REPAIR, NotificationPlan.kindOf(repairStake = 3, streak = 0, remaining = 2))
    }

    @Test
    fun `a short run is not worth a streak notification`() {
        assertEquals(NoticeKind.FRESH, NotificationPlan.kindOf(repairStake = 2, streak = 2, remaining = null))
    }

    @Test
    fun `a run at stake comes before a half-done board`() {
        assertEquals(NoticeKind.STREAK, NotificationPlan.kindOf(repairStake = 0, streak = 3, remaining = 2))
    }

    @Test
    fun `a board part-way through says how much is left`() {
        assertEquals(NoticeKind.STARTED, NotificationPlan.kindOf(repairStake = 0, streak = 0, remaining = 2))
        assertEquals(NoticeKind.FRESH, NotificationPlan.kindOf(repairStake = 0, streak = 0, remaining = 0))
    }

    // The clock

    @Test
    fun `a slot turns into the wall-clock time it names`() {
        val millis = NotificationPlan.millisOf(Slot(today, at(19, 5)))
        assertEquals(at(19, 5), NotificationPlan.minuteOf(millis))
    }

    private fun send(
        prefs: NotificationPrefs,
        now: Int,
        solved: Boolean = false,
        onScreen: Boolean = false,
    ) = NotificationPlan.shouldSend(prefs, today, now, solved, onScreen)

    private fun next(prefs: NotificationPrefs, now: Int, solved: Boolean = false) =
        NotificationPlan.next(prefs, today, now, solved)
}
