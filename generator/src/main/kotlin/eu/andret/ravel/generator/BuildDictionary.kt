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
 * | inflection | PoliMorf / SGJP (2-clause BSD) | WordNet 3.0 (Princeton licence) |
 *
 * Ranking is by ARF (average reduced frequency), which discounts words that look frequent only
 * because they cluster inside a single document - something raw counts reward by accident.
 *
 * ### Polish ships lemmas, not forms
 *
 * A word-game list is a list of *forms*, and Polish has a lot of them per word: the shipped
 * dictionary used to be 247 012 entries of which only 26% were base forms, so a board was far more
 * likely to spell *dokumentację* or *wystąpiło* than *dokumentacja* or *wystąpić*. That is a bad
 * trade in a game whose only clue is a word's length - a player recognises a word they know, and an
 * oblique case of a word they know is not the same thing.
 *
 * PoliMorf supplies the form-to-lemma map, and the build keeps only lemmas: 51 247 words.
 *
 * The map is needed for a second reason that matters more than the filter. ARF is measured per
 * form, and a lemma is often rare *as a form* - Polish writes *robi* and *robił* far more often than
 * the infinitive *robić*. Ranking surviving lemmas by their own form's ARF would therefore bury the
 * commonest verbs in the language. [rankLemmas] sums each lemma's forms instead, which puts *robić*
 * around rank 100 where it belongs rather than around 8000 where its infinitive sits alone.
 *
 * ### English asks the question the other way round
 *
 * The same problem, at 30% of the loaded dictionary rather than 74%, and it cannot be solved the
 * same way. WordNet indexes only nouns, verbs, adjectives and adverbs, so "keep the lemmas" would
 * delete *the*, *and*, *for*, *with* and *than* - the short common words an early board is built
 * from.
 *
 * So the English pass asks whether a word is a **regular inflection of something already in this
 * dictionary**, and folds it into that word if it is. A function word is not an inflection of
 * anything, so it survives with nobody having to keep a list of function words - and a list is what
 * this would otherwise need, with all the judgement calls that go in one. See [foldInflections].
 *
 * A blocklist is applied last. Word-game lists are complete rather than polite, and ranking by
 * frequency actively promotes profanity, so without this step boards spell things no store rating
 * covers.
 *
 * Usage:
 *   buildDictionary pl <output> <blocklist> <removed> <sjp-slowa.txt> <kwjp100.csv> <polimorf.tab.gz>
 *   buildDictionary en <output> <blocklist> <removed> <enable1.txt> <oanc-dir> <wordnet-dir>
 */
fun main(args: Array<String>) {
    require(args.size == 7) { USAGE }
    val language = Language.of(args[0])
    val outputFile = File(args[1])
    val blocklistFile = File(args[2])
    val removedFile = File(args[3])
    val admissibilitySource = File(args[4])
    val frequencySource = File(args[5])
    // Required rather than optional: a dictionary of inflected forms is not something this game
    // wants to be able to build by leaving an argument off.
    val inflectionSource = File(args[6])

    val admissible = readAdmissibleWords(language, admissibilitySource)
    println("${language.code}: ${admissible.size} admissible words of length $MIN_WORD_LENGTH-$MAX_WORD_LENGTH")

    val ranked = when (language) {
        Language.POLISH -> {
            val formFrequencies = readKwjpFormFrequencies(frequencySource)
            println("${language.code}: ${formFrequencies.size} forms carry a frequency")
            val morphology = readMorphology(inflectionSource, language, formFrequencies.keys)
            println(
                "${language.code}: ${morphology.lemmas.size} admissible lemmas, " +
                    "${morphology.formToLemmas.size} of the frequency list's forms inflect one"
            )
            rankLemmas(formFrequencies, morphology, admissible)
        }
        Language.ENGLISH -> {
            val forms = computeOancFrequencies(frequencySource, admissible)
            val lemmas = readWordNetLemmas(inflectionSource, language)
            println("${language.code}: ${lemmas.size} WordNet lemmas, ${forms.size} forms before folding")
            foldInflections(forms, lemmas)
        }
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

/** Whether [word] could be a board word at all: right length, and nothing outside the alphabet. */
internal fun Language.admits(word: String): Boolean =
    word.length in MIN_WORD_LENGTH..MAX_WORD_LENGTH && alphabet.isWordValid(word)

/** Words the language admits at all: right length, and nothing outside its alphabet. */
private fun readAdmissibleWords(language: Language, file: File): HashSet<String> {
    val words = HashSet<String>(1 shl 20)
    file.useLines(Charsets.UTF_8) { lines ->
        lines.forEach { line ->
            val word = line.trim().lowercase()
            if (language.admits(word)) words.add(word)
        }
    }
    return words
}

/**
 * Lemmas ranked by the summed ARF of every form they inflect, keeping only those [admissible]
 * vouches for.
 *
 * A form belonging to several lemmas - Polish *mam* is both *mama* and *mieć* - splits its ARF
 * evenly between them. Guessing which lemma a corpus occurrence meant needs a tagged corpus, which
 * KWJP's orthographic list is not; splitting evenly is at least symmetric, so no lemma is
 * systematically favoured, and the error is small next to the difference this whole step corrects.
 *
 * Summing ARF is likewise an approximation, and a conservative one: ARF discounts a word for
 * clustering inside few documents, and a lemma is spread over at least as many documents as any one
 * of its forms, so the sum understates a lemma's true dispersion rather than flattering it. It
 * understates every lemma the same way, which is all a ranking needs.
 *
 * The admissibility list still has the last word. It is a word-game dictionary, so it is the thing
 * that knows *dupa* is a word and *aids* is not one you play, and PoliMorf - a morphological
 * analyser - is not in that business.
 */
private fun rankLemmas(
    formFrequencies: Map<String, Double>,
    morphology: Morphology,
    admissible: Set<String>,
): ArrayList<RankedWord> {
    val summed = HashMap<String, Double>(morphology.lemmas.size * 2)
    formFrequencies.forEach { (form, arf) ->
        val lemmas = morphology.formToLemmas[form] ?: return@forEach
        val share = arf / lemmas.size
        lemmas.forEach { lemma -> summed.merge(lemma, share, Double::plus) }
    }
    val ranked = ArrayList<RankedWord>(summed.size)
    summed.forEach { (lemma, arf) ->
        if (lemma in admissible) ranked.add(RankedWord(lemma, arf))
    }
    return ranked
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

/**
 * Per-length pool sizes at each depth a board may draw from.
 *
 * This is the table to read before moving any rank cap. A cap is only usable when every length the
 * grid can ask for still has enough candidates behind it to letter a board without repeating
 * itself; the thin column is always the shortest words, because a language has fewer three-letter
 * words than seven-letter ones however deep you go.
 *
 * The extra tiers are the per-level caps in `PuzzleSpec.rankFor`, which :generator cannot import -
 * the curve is the app's curriculum, not the generator's business. They are listed here anyway,
 * because this is the only place the consequence of choosing them is visible.
 */
private fun reportLengthHistogram(ranked: List<RankedWord>) {
    val tiers = (Difficulty.entries.map(Difficulty::maxRank) + REPORTED_TIERS + ranked.size)
        .distinct().sorted()
    println("rank\t" + (MIN_WORD_LENGTH..MAX_WORD_LENGTH).joinToString("\t") { "len$it" } + "\tminARF")
    tiers.filter { it <= ranked.size }.forEach { tier ->
        val head = ranked.subList(0, tier)
        val histogram = IntArray(MAX_WORD_LENGTH + 1)
        head.forEach { histogram[it.word.length]++ }
        val row = (MIN_WORD_LENGTH..MAX_WORD_LENGTH).joinToString("\t") { histogram[it].toString() }
        println("$tier\t$row\t%.1f".format(head.last().averageReducedFrequency))
    }
}

/** Depths worth seeing a length histogram for; mirrors `PuzzleSpec.rankFor`. */
private val REPORTED_TIERS = listOf(1_500, 4_000, 10_000, 25_000)

private const val USAGE = """
Usage:
  buildDictionary pl <output> <blocklist> <removed> <sjp-slowa.txt> <kwjp100.csv> <polimorf.tab.gz>
  buildDictionary en <output> <blocklist> <removed> <enable1.txt> <oanc-dir> <wordnet-dir>
"""
