package eu.andret.ravel.ui.game

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the clock banks, driven by a hand-moved monotonic clock. The ticker that updates the number
 * on screen runs on a real scope and is not what is checked here: banking happens on stop, from the
 * clock alone.
 */
class SolveClockTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private var millis = 0L

    private val banked = ArrayList<Int>()

    private val clock = SolveClock(scope, banked = 0, onBank = { banked += it }, now = { millis })

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun sitting(length: Long) {
        clock.onVisible(true)
        millis += length
        clock.onVisible(false)
    }

    @Test
    fun `whole seconds are banked when the screen goes`() {
        clock.onPlayable(true)
        sitting(3_000)
        assertEquals(listOf(3), banked)
    }

    @Test
    fun `the fraction a sitting ends on counts toward the next one`() {
        clock.onPlayable(true)
        sitting(1_500)
        sitting(600)
        // 2.1 seconds in all. Rounded down per sitting it would have been one second, then none.
        assertEquals(listOf(1, 1), banked)
        assertEquals(2, clock.elapsed.value)
    }

    @Test
    fun `sittings shorter than a second add up instead of vanishing`() {
        clock.onPlayable(true)
        repeat(4) { sitting(400) }
        assertEquals(1, banked.sum())
    }

    @Test
    fun `nothing is counted while the board is not playable`() {
        sitting(5_000)
        assertEquals(emptyList<Int>(), banked)
    }

    @Test
    fun `stopping banks what the run counted`() {
        clock.onPlayable(true)
        clock.onVisible(true)
        millis += 2_700
        clock.stop()
        assertEquals(listOf(2), banked)
    }
}
