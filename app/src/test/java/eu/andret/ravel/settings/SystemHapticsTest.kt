package eu.andret.ravel.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The settings tab greys the haptics switch out on these answers, so a wrong one either locks a
 * player out of a switch that works or leaves them flipping one that cannot. Only a definite "off"
 * and a missing vibrator may lock it - see [SystemHaptics].
 */
class SystemHapticsTest {

    @Test
    fun `touch feedback on leaves the switch usable`() {
        assertEquals(SystemHaptics.AVAILABLE, SystemHaptics.of(hasVibrator = true, touchFeedback = 1))
    }

    @Test
    fun `touch feedback off is reported as off`() {
        assertEquals(SystemHaptics.OFF, SystemHaptics.of(hasVibrator = true, touchFeedback = 0))
    }

    @Test
    fun `a setting the phone never wrote counts as on`() {
        // The cautious side: a warning that is wrong sends a player looking for a setting that is
        // already on, where silence costs nothing the game did not already cost them.
        assertEquals(
            SystemHaptics.AVAILABLE,
            SystemHaptics.of(hasVibrator = true, touchFeedback = SystemHaptics.UNKNOWN),
        )
    }

    @Test
    fun `no vibrator wins over whatever the setting says`() {
        assertEquals(SystemHaptics.ABSENT, SystemHaptics.of(hasVibrator = false, touchFeedback = 1))
        assertEquals(SystemHaptics.ABSENT, SystemHaptics.of(hasVibrator = false, touchFeedback = 0))
    }
}
