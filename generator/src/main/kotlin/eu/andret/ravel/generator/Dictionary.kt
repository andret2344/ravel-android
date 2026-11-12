package eu.andret.ravel.generator

import java.io.File
import java.io.Reader

const val MIN_WORD_LENGTH = 3
const val MAX_WORD_LENGTH = 12

/** Rank cap meaning "the whole dictionary", for callers that do not want words held back. */
const val NO_RANK_LIMIT = Int.MAX_VALUE

/**
 * One generation run's view of a [Dictionary]: the candidate words for each length, already cut to
 * the rank cap that run was given. See [Dictionary.poolTo] for why the cut happens here rather than
 * at each draw.
 */
class WordPool internal constructor(private val byLength: Array<List<String>>) {
    fun ofLength(length: Int): List<String> = byLength[length]
}

/**
 * Game dictionary: words ordered by descending corpus frequency, so a word's position in the
 * source file is its difficulty rank.
 *
 * [trie] holds every word **and its reverse**. A path drawn on the board is just a sequence of
 * cells, and the player may draw it from either end, so a cell sequence counts as a word when
 * the string reads as one in either direction. Storing reversals lets the solver prune with a
 * single trie walk instead of two.
 */
class Dictionary private constructor(
    val language: Language,
    val words: List<String>,
    private val byLength: Array<Set<String>>,
    private val orderedByLength: Array<List<String>>,
    private val ranksByLength: Array<IntArray>,
) {
    val trie: Trie = Trie(language.alphabet).apply {
        words.forEach { word ->
            add(word)
            add(word.reversed())
        }
        compact()
    }

    /**
     * The words a board may be *lettered* from when it may only reach [maxRank] deep.
     *
     * Deliberately narrower than [trie], which always holds the whole loaded dictionary. The two
     * answer different questions: the pool decides what the player is asked to find, while the trie
     * decides what counts as a word when [Solver] hunts for a second solution. Narrowing the pool
     * therefore makes a board easier to *read* without making it easier to be unique - uniqueness is
     * still proved against every word the dictionary knows, which is the stricter claim and the one
     * that has to stay true.
     *
     * The same split is why excluded answers ([Language.excludedAnswersResource]) are cut here and
     * nowhere else. *zgwałcić* must never be a word the player is asked for, but it is still a word:
     * a player who sees it spelled across a board will trace it whatever the game thinks. Dropping
     * it from the trie as well would let a board through whose second solution runs through it, and
     * the board would then refuse a trace the player can see is right. Kept in the trie, it can only
     * make such a board fail the uniqueness check, which is the outcome wanted.
     *
     * An excluded word keeps its rank - ranks are positions in the file - so a cap of 8000 offers
     * the 8000 most common words minus the excluded ones, not 8000 words reaching a little deeper.
     *
     * Computed once per generation rather than per draw: the lettering loop asks for candidates
     * a few hundred times per layout, and none of those calls has anything new to work out.
     */
    fun poolTo(maxRank: Int): WordPool = WordPool(
        Array(orderedByLength.size) { length -> wordsOfLength(length, maxRank) }
    )

    /**
     * The most common words of [length], down to rank [maxRank] but never fewer than [MIN_POOL].
     *
     * The floor is what keeps a narrow cap honest. Word counts per length are wildly uneven - the
     * top 1500 Polish lemmas hold 273 five-letter words but only 13 twelve-letter ones - so a cap
     * chosen to suit the lengths a small grid asks for would starve the lengths a large one does.
     * Where the cap would leave too few, the pool widens instead of the board repeating itself.
     */
    private fun wordsOfLength(length: Int, maxRank: Int): List<String> {
        val all = orderedByLength[length]
        if (maxRank >= words.size || all.isEmpty()) return all
        val found = ranksByLength[length].binarySearch(maxRank)
        // Ranks ascend and never repeat, so a miss returns the insertion point and both cases give
        // the number of words ranked above the cap.
        val below = if (found >= 0) found else -found - 1
        return all.subList(0, below.coerceIn(minOf(MIN_POOL, all.size), all.size))
    }

    fun isWord(word: String): Boolean = word.length <= MAX_WORD_LENGTH && word in byLength[word.length]

    /**
     * The dictionary word spelled by [candidate] read in either direction, or null when neither
     * direction is a word. Ties (both directions valid) resolve to [candidate] itself.
     */
    fun resolve(candidate: String): String? = when {
        isWord(candidate) -> candidate
        else -> candidate.reversed().takeIf(::isWord)
    }

    companion object {
        /**
         * Loads a dictionary file with nothing excluded from its pools - the CLI's way of trying
         * out a word list that is not the shipped one. Pass [excludedAnswers] to apply a filter.
         */
        fun load(
            language: Language,
            file: File,
            maxRank: Int = Int.MAX_VALUE,
            excludedAnswers: Set<String> = emptySet(),
        ): Dictionary = file.bufferedReader(Charsets.UTF_8).use { load(language, it, maxRank, excludedAnswers) }

        fun load(
            language: Language,
            reader: Reader,
            maxRank: Int = Int.MAX_VALUE,
            excludedAnswers: Set<String> = emptySet(),
        ): Dictionary {
            val words = ArrayList<String>(minOf(maxRank, 1 shl 17))
            reader.buffered().lineSequence().forEach { line ->
                if (words.size >= maxRank) return@forEach
                val word = line.trim()
                if (word.isNotEmpty()) words.add(word)
            }
            return of(language, words, excludedAnswers)
        }

        /**
         * Loads at most [maxRank] highest-ranked words of the dictionary shipped as a module
         * resource, with the shipped [excludedAnswers] kept out of its pools. A smaller dictionary
         * means words players know and, incidentally, fewer alternative tilings for the solver to
         * rule out.
         */
        fun loadBundled(language: Language, maxRank: Int = Int.MAX_VALUE): Dictionary {
            val resource = language.dictionaryResource
            val stream = requireNotNull(Dictionary::class.java.getResourceAsStream(resource)) {
                "Missing $resource - run the ${language.buildTask} Gradle task first"
            }
            return stream.reader(Charsets.UTF_8).use { load(language, it, maxRank, excludedAnswers(language)) }
        }

        fun loadBundled(language: Language, difficulty: Difficulty): Dictionary =
            loadBundled(language, difficulty.maxRank)

        /**
         * The words no [language] board may ask for, read from [Language.excludedAnswersResource]:
         * one word per line, `#` starting a comment.
         *
         * Public so that the app's tests can hold its own hand-made boards to the same list.
         */
        fun excludedAnswers(language: Language): Set<String> {
            val resource = language.excludedAnswersResource
            val stream = requireNotNull(Dictionary::class.java.getResourceAsStream(resource)) {
                "Missing $resource"
            }
            return stream.reader(Charsets.UTF_8).use { reader ->
                reader.readLines()
                    .map { it.substringBefore('#').trim() }
                    .filter { it.isNotEmpty() }
                    .toHashSet()
            }
        }

        /**
         * Builds a dictionary from [words] in rank order. Every word counts for [isWord] and the
         * trie; [excludedAnswers] are left out of the pools only - see [poolTo].
         */
        fun of(language: Language, words: List<String>, excludedAnswers: Set<String> = emptySet()): Dictionary {
            val buckets = Array(MAX_WORD_LENGTH + 1) { ArrayList<String>() }
            val answerBuckets = Array(MAX_WORD_LENGTH + 1) { ArrayList<String>() }
            // A word's position in [words] is its rank, since the file is written in frequency
            // order. Recording it per bucket is what lets a bucket be cut at a rank later: the
            // bucket inherits the file's order, so its ranks ascend and can be searched. Skipping
            // an excluded word leaves a gap in a bucket's ranks, which a binary search does not mind.
            val rankBuckets = Array(MAX_WORD_LENGTH + 1) { ArrayList<Int>() }
            for ((rank, word) in words.withIndex()) {
                require(word.length in MIN_WORD_LENGTH..MAX_WORD_LENGTH) {
                    "Word '$word' has length ${word.length}, outside $MIN_WORD_LENGTH..$MAX_WORD_LENGTH"
                }
                require(language.alphabet.isWordValid(word)) {
                    "Word '$word' contains characters outside the ${language.code} alphabet"
                }
                buckets[word.length].add(word)
                if (word !in excludedAnswers) {
                    answerBuckets[word.length].add(word)
                    rankBuckets[word.length].add(rank)
                }
            }
            return Dictionary(
                language = language,
                words = words,
                byLength = Array(buckets.size) { buckets[it].toHashSet() },
                orderedByLength = Array(answerBuckets.size) { answerBuckets[it] },
                ranksByLength = Array(rankBuckets.size) { rankBuckets[it].toIntArray() },
            )
        }

        /**
         * Candidates any one length keeps however tight the rank cap is.
         *
         * A board can carry several paths of the same length, and each redraw picks uniformly, so
         * the pool has to be comfortably wider than the number of words drawn from it or
         * [BoardGenerator.DISTINCT_WORD_ATTEMPTS] spends its retries colliding. Sixty-four against
         * a worst case of about twenty words leaves that margin, and it is small enough that the
         * floor only ever binds on the longest words, where a tight cap genuinely has nothing to
         * offer.
         */
        const val MIN_POOL = 64
    }
}
