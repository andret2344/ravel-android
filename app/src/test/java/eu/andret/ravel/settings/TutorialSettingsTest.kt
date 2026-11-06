package eu.andret.ravel.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorialSettingsTest {

    @Test
    fun `a new player is shown the tutorial`() {
        assertTrue(TutorialSettings.pendingFrom(stored = null, hasPlayed = { false }))
    }

    @Test
    fun `a player who solved something before the tutorial existed is not stopped by it`() {
        assertFalse(TutorialSettings.pendingFrom(stored = null, hasPlayed = { true }))
    }

    @Test
    fun `a stored answer wins over progress either way`() {
        assertFalse(TutorialSettings.pendingFrom(stored = true, hasPlayed = { false }))
        assertTrue(TutorialSettings.pendingFrom(stored = false, hasPlayed = { true }))
    }

    @Test
    fun `progress is not asked about once an answer is stored`() {
        TutorialSettings.pendingFrom(stored = true, hasPlayed = { error("progress was opened") })
    }
}
