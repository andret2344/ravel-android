package eu.andret.ravel.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeChoiceTest {

    @Test
    fun `every choice reads back from its own code`() {
        ThemeChoice.entries.forEach { choice ->
            assertEquals(choice, ThemeChoice.byCode(choice.code))
        }
    }

    @Test
    fun `nothing stored and an unknown code both follow the phone`() {
        assertEquals(ThemeChoice.SYSTEM, ThemeChoice.byCode(null))
        assertEquals(ThemeChoice.SYSTEM, ThemeChoice.byCode("sepia"))
    }
}
