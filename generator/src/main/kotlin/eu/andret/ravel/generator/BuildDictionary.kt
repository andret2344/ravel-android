package eu.andret.ravel.generator

import java.io.File

/**
 * Builds a ranked game dictionary for one language.
 *
 * Both languages follow the same shape, because the same two questions have to be answered
 * separately: *is this a word* and *would a player know it*. One source decides admissibility, a
 * second decides frequency, and only their intersection ships.
 *
 * | | Polish | English |
 * |---|---|---|
 * | admissibility | SJP.PL word-game list (CC BY 4.0) | ENABLE (public domain) |
 * | frequency | KWJP100, IPI PAN (CC BY 4.0) | OANC (unrestricted), ARF computed here |
 *
 * Ranking is by ARF (average reduced frequency), which discounts words that look frequent only
 * because they cluster inside a single document - something raw counts reward by accident.
 *
 * A blocklist is applied last. Word-game lists are complete rather than polite, and ranking by
 * frequency actively promotes profanity, so without this step boards spell things no store rating
 * covers.
 *
 * Usage:
 *   buildDictionary pl <output> <blocklist> <removed> <sjp-slowa.txt> <kwjp100.csv>
 *   buildDictionary en <output> <blocklist> <removed> <enable1.txt> <oanc-directory>
 */
fun main(args: Array<String>) {
    require(args.size == 6) { USAGE }
    val language = Language.of(args[0])
    val outputFile = File(args[1])
    val blocklistFile = File(args[2])
    val removedFile = File(args[3])
    val admissibilitySource = File(args[4])
    val frequencySource = File(args[5])

    val admissible = readAdmissibleWords(language, admissibilitySource)
    println("${language.code}: ${admissible.size} admissible words of length $MIN_WORD_LENGTH-$MAX_WORD_LENGTH")

    val ranked = when (language) {
        Language.POLISH -> readKwjpFrequencies(frequencySource, admissible)
        Language.ENGLISH -> computeOancFrequencies(frequencySource, admissible)
    }
    println("${language.code}: ${ranked.size} words after intersecting with the frequency data")

    val removed = applyBlocklist(ranked, readBlocklist(blocklistFile))
    writeWords(removedFile, removed.map(RankedWord::word).sorted())
    println("${language.code}: blocked ${removed.size} words, listed in ${removedFile.path}")

    ranked.sortByDescending(RankedWord::averageReducedFrequency)
    writeWords(outputFile, ranked.map(RankedWord::word))
    println("${language.code}: written ${ranked.size} words to ${outputFile.path}")
    reportLengthHistogram(ranked)
}

class RankedWord(val word: String, val averageReducedFrequency: Double)

/** Words the language admits at all: right length, and nothing outside its alphabet. */
private fun readAdmissibleWords(language: Language, file: File): HashSet<String> {
    val words = HashSet<String>(1 shl 20)
    file.useLines(Charsets.UTF_8) { lines ->
        lines.forEach { line ->
            val word = line.trim().lowercase()
            if (word.length in MIN_WORD_LENGTH..MAX_WORD_LENGTH && language.alphabet.isWordValid(word)) {
                words.add(word)
            }
        }
    }
    return words
}

private fun readBlocklist(file: File): List<String> = file.readLines(Charsets.UTF_8)
    .map { it.substringBefore('#').trim() }
    .filter { it.isNotEmpty() }

/**
 * Removes every word starting with a blocked stem, in place, and returns what was removed.
 *
 * Matching is anchored at the start rather than anywhere in the word. Substring matching looked
 * right - vulgar derivation does bury stems inside words - but it cuts down innocents at a painful
 * rate: Polish *cechuje*, *wymachuje* and *dowcipach* all contain one by accident, as does English
 * *assassin* or *cockpit*. Anchoring loses little, because both languages derive by prefixing, so
 * a derived form still starts with prefix plus stem and the blocklist can name it.
 *
 * The Polish negation prefix *nie-* is the one exception worth automating: it stacks onto anything.
 */
private fun applyBlocklist(ranked: MutableList<RankedWord>, stems: List<String>): List<RankedWord> {
    val removed = ArrayList<RankedWord>()
    ranked.removeAll { entry ->
        val blocked = stems.any { entry.word.startsWith(it) || entry.word.startsWith("nie$it") }
        if (blocked) removed.add(entry)
        blocked
    }
    return removed
}

private fun writeWords(file: File, words: List<String>) {
    file.parentFile?.mkdirs()
    file.bufferedWriter(Charsets.UTF_8).use { writer ->
        words.forEach { word ->
            writer.write(word)
            writer.newLine()
        }
    }
}

private fun reportLengthHistogram(ranked: List<RankedWord>) {
    val tiers = (Difficulty.entries.map(Difficulty::maxRank) + ranked.size).distinct().sorted()
    println("rank\t" + (MIN_WORD_LENGTH..MAX_WORD_LENGTH).joinToString("\t") { "len$it" } + "\tminARF")
    tiers.filter { it <= ranked.size }.forEach { tier ->
        val head = ranked.subList(0, tier)
        val histogram = IntArray(MAX_WORD_LENGTH + 1)
        head.forEach { histogram[it.word.length]++ }
        val row = (MIN_WORD_LENGTH..MAX_WORD_LENGTH).joinToString("\t") { histogram[it].toString() }
        println("$tier\t$row\t%.1f".format(head.last().averageReducedFrequency))
    }
}

private const val USAGE = """
Usage:
  buildDictionary pl <output> <blocklist> <removed> <sjp-slowa.txt> <kwjp100.csv>
  buildDictionary en <output> <blocklist> <removed> <enable1.txt> <oanc-directory>
"""
