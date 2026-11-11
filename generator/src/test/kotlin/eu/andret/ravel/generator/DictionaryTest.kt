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
    fun `every excluded answer is a word of the shipped dictionary`() {
        // An entry that is not a word excludes nothing: a typo, or a word a dictionary rebuild has
        // dropped. Either way the list no longer says what it was reviewed to say.
        Language.entries.forEach { language ->
            val dictionary = Dictionary.loadBundled(language)
            val strays = Dictionary.excludedAnswers(language).filterNot(dictionary::isWord).sorted()
            assertContentEquals(emptyList(), strays, "${language.code}: not in the dictionary")
        }
    }

    @Test
    fun `an excluded answer never reaches a pool but stays a word`() {
        Language.entries.forEach { language ->
            val dictionary = Dictionary.loadBundled(language)
            val excluded = Dictionary.excludedAnswers(language)
            // The uncapped pool and a cap tight enough for the MIN_POOL floor to widen it, which
            // are the two ways a bucket is cut.
            listOf(NO_RANK_LIMIT, 200).forEach { cap ->
                val pool = dictionary.poolTo(cap)
                val leaked = (MIN_WORD_LENGTH..MAX_WORD_LENGTH).flatMap(pool::ofLength).filter { it in excluded }
                assertContentEquals(emptyList(), leaked, "${language.code}, cap $cap: excluded words in the pool")
            }
            // The other half, and the reason they are excluded here rather than at build time: the
            // solver must still see them, or a board with a second solution through one gets out.
            excluded.forEach { word -> assertTrue(dictionary.isWord(word), "${language.code}: '$word' lost") }
        }
    }

    @Test
    fun `excluding a word leaves the ranks of the others alone`() {
        // A hundred three-letter words in rank order, enough that a cap of 70 sits above the
        // MIN_POOL floor and the cap itself decides the cut.
        val words = (0 until 100).map { "${'a' + it / 10}${'a' + it % 10}k" }
        val excluded = words[10]
        val dictionary = Dictionary.of(Language.POLISH, words, excludedAnswers = setOf(excluded))

        // The cap counts ranks, and the excluded word keeps its rank: a cap of 70 offers the 70
        // most common words minus one, and does not reach down to words[70] to make up the number.
        val pool = dictionary.poolTo(70).ofLength(3)
        assertContentEquals(words.take(70) - excluded, pool)
        assertTrue(excluded !in dictionary.poolTo(NO_RANK_LIMIT).ofLength(3))

        // Still a word, both for isWord and for the trie the solver walks.
        assertTrue(dictionary.isWord(excluded))
        val node = excluded.fold(Trie.ROOT) { at, letter -> if (at == Trie.EMPTY) at else dictionary.trie.step(at, letter) }
        assertTrue(dictionary.trie.isWord(node))
    }

    @Test
    fun `a word on a sensitive stem is either excluded or known to be innocent`() {
        // The excluded answers are whole words, which suits a lemma dictionary, but a rebuild can
        // bring in a lemma nobody reviewed - a "gwałcicielka" next to "gwałciciel". These stems are
        // the alarm for that: every dictionary word starting with one must be on the excluded list
        // or below, and a new one failing here is somebody reading it and deciding. Stems that match
        // nothing today are kept on purpose; they are there for the next rebuild.
        Language.entries.forEach { language ->
            val excluded = Dictionary.excludedAnswers(language)
            val (stems, innocent) = when (language) {
                Language.POLISH -> SENSITIVE_STEMS_PL to INNOCENT_ON_STEM_PL
                Language.ENGLISH -> SENSITIVE_STEMS_EN to INNOCENT_ON_STEM_EN
            }
            val unreviewed = Dictionary.loadBundled(language).words
                .filter { word -> stems.any(word::startsWith) && word !in excluded }
            assertEquals(innocent.toSet(), unreviewed.toSet(), "${language.code}: words on a sensitive stem")
        }
    }

    @Test
    fun `the Polish dictionary ships lemmas, not inflected forms`() {
        val polish = Dictionary.loadBundled(Language.POLISH).words.toHashSet()
        // Base forms a player would recognize on a board.
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

    private companion object {
        val SENSITIVE_STEMS_PL = listOf(
            "gwałc", "zgwałc", "gwałt", "molest", "pedofil", "kazirod", "zoofil", "nekrofil", "zboczeń",
            "zboczen", "lubież", "zabój", "morder", "mordow", "samobój", "ludobój", "holokaust",
            "holocaust", "nazis", "nazizm", "hitler", "faszys", "terror", "tortur", "egzekuc", "seks",
            "erot", "porn", "orgazm", "masturb", "onan", "prostytu", "narkot", "narkom", "kokain",
            "heroin", "amfetamin", "marihuan", "ćpa", "ćpun", "pedał", "pederast", "ciot", "cwel",
            "murzyn", "czarnuch", "cygan", "żyd", "gej", "lesb", "debil", "kretyn", "idiot", "imbecyl",
            "pijan", "pijak", "alkoholi", "dziwk", "zdzir", "szmat", "gówn", "dup", "rzyg",
        )

        /** Reviewed on 2026-11-08 and kept: each starts with a stem above and is not what it names. */
        val INNOCENT_ON_STEM_PL = listOf(
            "gwałtowny", "gwałtownie", "gwałtowność", // sudden, violent in the weather sense
            "ciotka", "cioteczny", "cioteczka", "ciotunia", "ciotczyny", // aunt
            "szmat", "szmatka", "szmatek", "szmateks", // a stretch of land; a cloth; a charity shop
            "gejsza", "gejzer", "cyganeria", "duplikat",
            "samobój", // an own goal
            "sekstet", "sekstant", "sekstans",
        )

        val SENSITIVE_STEMS_EN = listOf(
            "kill", "murder", "suicid", "genocid", "holocaust", "nazi", "fascis", "terror", "tortur",
            "molest", "pedophil", "incest", "pervert", "sadis", "sex", "porn", "erotic", "orgasm",
            "masturb", "prostitut", "drug", "cocaine", "heroin", "narcot", "marijuana", "cannabis", "gay",
            "lesbian", "homosex", "bisex", "jew", "retard", "idiot", "moron", "imbecil", "drunk",
            "alcoholi", "whor", "slut", "bitch", "bastard",
        )

        /** Reviewed on 2026-11-08 and kept: each starts with a stem above and is not what it names. */
        val INNOCENT_ON_STEM_EN = listOf(
            "jewel", "jewelry", "jeweler", "jeweled", "jewelled", "jewellery", "jewellike", "jewellers",
            "heroine", "killjoy", "retardant", "whorls", "whortleberry",
            "sexton", "sextant", "sextet", "sexto", "sextuplets",
        )
    }
}
