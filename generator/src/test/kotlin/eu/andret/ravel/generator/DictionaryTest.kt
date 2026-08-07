package eu.andret.ravel.generator

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DictionaryTest {

    @Test
    fun `no shipped dictionary carries a blocked word`() {
        Language.entries.forEach { language ->
            val stems = blocklistOf(language)
            val offenders = Dictionary.loadBundled(language).words.filter { word ->
                stems.any { word.startsWith(it) || word.startsWith("nie$it") }
            }
            assertContentEquals(
                emptyList(),
                offenders,
                "rebuild it: ./gradlew :generator:buildDictionary${language.code.replaceFirstChar(Char::uppercase)}",
            )
        }
    }

    @Test
    fun `blocking is anchored so ordinary words survive`() {
        // Each of these contains a blocked stem somewhere past the first letter. Substring matching,
        // which is what this project tried first, threw every one of them out.
        assertSurvives(Language.POLISH, "cechuje", "wymachuje", "zakochuje", "dowcipach", "nasłuchuj")
        assertSurvives(Language.ENGLISH, "assassin", "classic", "cockpit", "title", "prickle", "arsenic", "spice")
    }

    @Test
    fun `words are ranked by descending frequency`() {
        val polish = Dictionary.loadBundled(Language.POLISH, maxRank = 200).words
        val english = Dictionary.loadBundled(Language.ENGLISH, maxRank = 200).words
        assertEquals(200, polish.size)
        assertEquals(200, english.size)
        // The head of a balanced corpus is function words. Anything else there would mean the
        // frequency column was misread, or the ARF computation ran on the wrong axis.
        assertTrue(polish.take(50).any { it == "nie" || it == "jest" || it == "tego" }, polish.take(10).toString())
        assertTrue(english.take(50).any { it == "the" || it == "and" || it == "that" }, english.take(10).toString())
    }

    @Test
    fun `every word fits its own alphabet and the grid`() {
        Language.entries.forEach { language ->
            Dictionary.loadBundled(language).words.forEach { word ->
                assertTrue(word.length in MIN_WORD_LENGTH..MAX_WORD_LENGTH, "$language: $word")
                assertTrue(language.alphabet.isWordValid(word), "$language: $word")
            }
        }
    }

    @Test
    fun `alphabets stay separate`() {
        assertTrue(Alphabet.POLISH.contains('ż'))
        assertTrue(!Alphabet.ENGLISH.contains('ż'))
        assertTrue(Alphabet.ENGLISH.contains('x') && Alphabet.POLISH.contains('x'))
    }

    private fun assertSurvives(language: Language, vararg words: String) {
        val dictionary = Dictionary.loadBundled(language).words.toHashSet()
        val expected = words.toList()
        assertContentEquals(expected, expected.filter { it in dictionary }, "missing from ${language.code}")
    }

    private fun blocklistOf(language: Language): List<String> {
        val stems = File(language.blocklistFile).readLines(Charsets.UTF_8)
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }
        assertTrue(stems.isNotEmpty(), "${language.blocklistFile} is empty or missing")
        return stems
    }
}
