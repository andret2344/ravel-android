package eu.andret.ravel.generator

import java.io.File
import java.io.Reader

const val MIN_WORD_LENGTH = 3
const val MAX_WORD_LENGTH = 12

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
) {
    val trie: Trie = Trie(language.alphabet).apply {
        words.forEach { word ->
            add(word)
            add(word.reversed())
        }
        compact()
    }

    fun wordsOfLength(length: Int): List<String> = orderedByLength[length]

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
        fun load(language: Language, file: File, maxRank: Int = Int.MAX_VALUE): Dictionary =
            file.bufferedReader(Charsets.UTF_8).use { load(language, it, maxRank) }

        fun load(language: Language, reader: Reader, maxRank: Int = Int.MAX_VALUE): Dictionary {
            val words = ArrayList<String>(minOf(maxRank, 1 shl 17))
            reader.buffered().lineSequence().forEach { line ->
                if (words.size >= maxRank) return@forEach
                val word = line.trim()
                if (word.isNotEmpty()) words.add(word)
            }
            return of(language, words)
        }

        /**
         * Loads at most [maxRank] highest-ranked words of the dictionary shipped as a module
         * resource. A smaller dictionary means words players know and, incidentally, fewer
         * alternative tilings for the solver to rule out.
         */
        fun loadBundled(language: Language, maxRank: Int = Int.MAX_VALUE): Dictionary {
            val resource = language.dictionaryResource
            val stream = requireNotNull(Dictionary::class.java.getResourceAsStream(resource)) {
                "Missing $resource - run the buildDictionary${language.code.uppercase()} Gradle task first"
            }
            return stream.reader(Charsets.UTF_8).use { load(language, it, maxRank) }
        }

        fun loadBundled(language: Language, difficulty: Difficulty): Dictionary =
            loadBundled(language, difficulty.maxRank)

        fun of(language: Language, words: List<String>): Dictionary {
            val buckets = Array(MAX_WORD_LENGTH + 1) { ArrayList<String>() }
            for (word in words) {
                require(word.length in MIN_WORD_LENGTH..MAX_WORD_LENGTH) {
                    "Word '$word' has length ${word.length}, outside $MIN_WORD_LENGTH..$MAX_WORD_LENGTH"
                }
                require(language.alphabet.isWordValid(word)) {
                    "Word '$word' contains characters outside the ${language.code} alphabet"
                }
                buckets[word.length].add(word)
            }
            return Dictionary(
                language = language,
                words = words,
                byLength = Array(buckets.size) { buckets[it].toHashSet() },
                orderedByLength = Array(buckets.size) { buckets[it] },
            )
        }
    }
}
