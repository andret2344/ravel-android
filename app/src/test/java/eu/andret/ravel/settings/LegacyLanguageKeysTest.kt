package eu.andret.ravel.settings

import eu.andret.ravel.generator.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The rename of the bare `pl` / `en` keys, and the alias that keeps reading the old codes. */
class LegacyLanguageKeysTest {

    @Test
    fun `a per-language level key moves to the new code`() {
        assertEquals("en-US.completed", LegacyLanguageKeys.levelKey("en.completed"))
        assertEquals("pl-PL.board", LegacyLanguageKeys.levelKey("pl.board"))
    }

    @Test
    fun `level keys already new or not per-language are left alone`() {
        assertNull(LegacyLanguageKeys.levelKey("en-US.completed"))
        assertNull(LegacyLanguageKeys.levelKey("seconds"))
        assertNull(LegacyLanguageKeys.levelKey("en"))
    }

    @Test
    fun `a shelved board key moves to the new code and keeps its mode and ordinal`() {
        assertEquals("d.en-US.20261112", LegacyLanguageKeys.boardKey("d.en.20261112"))
        assertEquals("e.pl-PL.17", LegacyLanguageKeys.boardKey("e.pl.17"))
    }

    @Test
    fun `board keys already new or malformed are left alone`() {
        assertNull(LegacyLanguageKeys.boardKey("d.en-US.20261112"))
        assertNull(LegacyLanguageKeys.boardKey("d.en"))
        assertNull(LegacyLanguageKeys.boardKey("d.de.1"))
    }

    @Test
    fun `the old bare codes still read as their languages`() {
        assertEquals(Language.ENGLISH, LanguageSettings.byCode("en"))
        assertEquals(Language.POLISH, LanguageSettings.byCode("pl"))
        assertEquals(Language.ENGLISH, LanguageSettings.byCode("en-US"))
        assertEquals(Language.POLISH, LanguageSettings.byCode("pl-PL"))
        assertNull(LanguageSettings.byCode("de"))
    }

    @Test
    fun `any region of a language is that language`() {
        assertEquals(Language.ENGLISH, LanguageSettings.byLanguage("en"))
        assertNull(LanguageSettings.byLanguage("en-GB"))
    }
}
