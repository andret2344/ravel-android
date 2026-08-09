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
        //
        // The Polish side used to list five of these - "cechuje", "wymachuje", "zakochuje",
        // "dowcipach", "nasłuchuj" - and now lists one, because every other was an inflected form
        // and the dictionary ships lemmas. The lemmas behind them do not inherit the problem:
        // "cechować" simply does not contain "chuj" the way "cechuje" does. One survivor is what
        // the language actually offers; see the scan in `no shipped dictionary carries a blocked
        // word`, which is the half of this pair that would catch an over-eager stem.
        assertSurvives(Language.POLISH, "dowcipkować")
        assertSurvives(Language.ENGLISH, "assassin", "classic", "cockpit", "title", "prickle", "arsenic", "spice")
    }

    @Test
    fun `no shipped dictionary carries a vulgar compound`() {
        // Anchored matching cannot see a stem that sits at the end of a compound, which is how
        // English builds words. "bullshit" ranked 11 358 and shipped until this was caught, so the
        // check is on the compound pattern specifically rather than on the anchored rule again.
        Language.entries.forEach { language ->
            val stems = blocklistOf(language)
            val compounds = Dictionary.loadBundled(language).words.filter { word ->
                stems.any { stem -> word != stem && word.endsWith(stem) }
            }
            // Whatever survives has to be innocent, so it is listed rather than counted: a new one
            // appearing means somebody reads it and decides, instead of a number quietly moving.
            val expected = when (language) {
                Language.POLISH -> emptyList()
                Language.ENGLISH -> listOf("grape", "therapist", "drape", "scrape", "swank", "sarape", "serape")
            }
            assertEquals(expected.toSet(), compounds.toSet(), "${language.code} compound endings")
        }
    }

    @Test
    fun `the Polish dictionary ships lemmas, not inflected forms`() {
        val polish = Dictionary.loadBundled(Language.POLISH).words.toHashSet()
        // Base forms a player would recognise on a board.
        listOf("robić", "samochód", "dokumentacja", "kobieta", "wystąpić").forEach {
            assertTrue(it in polish, "'$it' is a lemma and should ship")
        }
        // Their oblique cases and past tenses, which is what the board used to be lettered from:
        // three quarters of the old 247 012-word dictionary looked like this.
        listOf("robił", "robi", "samochodu", "dokumentację", "kobiety", "wystąpiło").forEach {
            assertTrue(it !in polish, "'$it' is an inflected form and should not ship")
        }
    }

    @Test
    fun `the English dictionary folds regular inflections into their base`() {
        val english = Dictionary.loadBundled(Language.ENGLISH).words.toHashSet()
        // Regular inflections whose base is itself a playable word. These are what padded the list.
        listOf("results", "states", "cells", "shadows", "mosquitoes", "transporting", "jumps")
            .forEach { assertTrue(it !in english, "'$it' is a regular inflection and should not ship") }
        listOf("result", "state", "cell", "shadow", "mosquito", "transport", "jump")
            .forEach { assertTrue(it in english, "'$it' is the base form and should ship") }
    }

    @Test
    fun `folding English inflections leaves the function words alone`() {
        // The trap the Polish approach would have walked into. WordNet indexes nouns, verbs,
        // adjectives and adverbs only, so "keep the lemmas" deletes every one of these - and they
        // are precisely the short common words an early board is built from.
        val english = Dictionary.loadBundled(Language.ENGLISH).words.toHashSet()
        listOf(
            "the", "and", "that", "for", "with", "was", "you", "this", "from", "they", "were",
            "has", "which", "their", "when", "been", "what", "than", "had", "would", "these",
            "because", "into", "could", "should", "his",
        ).forEach { assertTrue(it in english, "function word '$it' was folded away") }
    }

    @Test
    fun `a rank cap narrows the draw pool without touching what counts as a word`() {
        val dictionary = Dictionary.loadBundled(Language.POLISH)
        val capped = dictionary.poolTo(2_000)
        val whole = dictionary.poolTo(NO_RANK_LIMIT)

        // Every capped candidate is drawn from the head of its own bucket, in order.
        for (length in MIN_WORD_LENGTH..MAX_WORD_LENGTH) {
            val narrow = capped.ofLength(length)
            val wide = whole.ofLength(length)
            assertTrue(narrow.size <= wide.size, "length $length: cap widened the pool")
            assertContentEquals(wide.take(narrow.size), narrow, "length $length: pool is not a prefix")
            assertTrue(narrow.isNotEmpty(), "length $length: cap emptied the pool")
        }

        // A capped word is common; an uncapped one need only be a word. Both are words, which is
        // the half that must not move - the solver reads the trie, not the pool.
        assertTrue(dictionary.isWord(whole.ofLength(7).last()))
        assertTrue(capped.ofLength(7).size < whole.ofLength(7).size, "the cap did nothing")
    }

    @Test
    fun `a cap too tight for a length widens instead of starving it`() {
        val dictionary = Dictionary.loadBundled(Language.POLISH)
        // Rank 200 holds no twelve-letter word at all, and a pool of none cannot letter a board.
        val pool = dictionary.poolTo(200).ofLength(MAX_WORD_LENGTH)
        assertEquals(Dictionary.MIN_POOL, pool.size)
        assertContentEquals(
            dictionary.poolTo(NO_RANK_LIMIT).ofLength(MAX_WORD_LENGTH).take(Dictionary.MIN_POOL),
            pool,
        )
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
