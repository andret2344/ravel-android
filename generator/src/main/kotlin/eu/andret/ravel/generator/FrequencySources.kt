package eu.andret.ravel.generator

import java.io.File
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * Reads the KWJP100 frequency list, which already carries an ARF column, as a form-to-ARF map.
 *
 * Every row is kept, not just the ones that could be board words. The map's job is to feed
 * [rankLemmas], and an inflected form scores the lemma it belongs to whether or not the form itself
 * could ever appear on a grid - *dokumentacjami* is fourteen letters and unplayable, and it is still
 * evidence that *dokumentacja* is a common word. Filtering here would silently dock every word
 * whose forms are long, which is exactly the words this build is trying to rank honestly.
 *
 * Rows whose ARF field does not parse are dropped, which also disposes of punctuation tokens whose
 * quoted CSV fields would break the naive split.
 */
internal fun readKwjpFormFrequencies(file: File): HashMap<String, Double> {
    val frequencies = HashMap<String, Double>(1 shl 19)
    file.useLines(Charsets.UTF_8) { lines ->
        lines.drop(1).forEach { line ->
            val firstSeparator = line.indexOf(',')
            if (firstSeparator <= 0) return@forEach
            val form = line.substring(0, firstSeparator)
            val fields = line.split(',')
            if (fields.size < KWJP_ARF_COLUMN + 1) return@forEach
            val arf = fields[KWJP_ARF_COLUMN].toDoubleOrNull() ?: return@forEach
            frequencies[form] = arf
        }
    }
    return frequencies
}

/**
 * The parts of a morphological dictionary this build needs.
 *
 * @param lemmas every lemma the language admits as a board word.
 * @param formToLemmas which of those lemmas a form inflects, for the forms the frequency data
 *   actually mentions. Restricted that way on purpose: the full map is 1.9 million entries and
 *   nothing without a frequency can contribute to a ranking, so the other 1.5 million would be
 *   carried through the build to be looked up never.
 */
internal class Morphology(
    val lemmas: Set<String>,
    val formToLemmas: Map<String, List<String>>,
)

/**
 * Reads PoliMorf, whose rows are `form`, `lemma`, `tag`, `qualifier`, tab-separated.
 *
 * Only `pospolita` rows are kept. The other qualifiers - `nazwisko`, `geograficzna`, `imię` and the
 * blank one, together about half a million rows - are proper names, and a board spelling somebody's
 * surname is not a puzzle. The admissibility list would reject most of them anyway, since it is
 * lowercase and they are not, but relying on that would leave the ones that collide with an
 * ordinary word.
 *
 * Reads gzip directly. The file is 37 MB compressed and 391 MB expanded, and it is read once per
 * dictionary build, so paying to decompress on the fly is the cheaper side of that trade.
 */
internal fun readMorphology(file: File, language: Language, knownForms: Set<String>): Morphology {
    val lemmas = HashSet<String>(1 shl 18)
    val formToLemmas = HashMap<String, MutableList<String>>(knownForms.size)
    file.openPossiblyCompressed().bufferedReader(Charsets.UTF_8).use { reader ->
        reader.lineSequence().forEach { line ->
            val fields = line.split('\t')
            if (fields.size != POLIMORF_FIELDS || fields[POLIMORF_QUALIFIER] != POLIMORF_COMMON) return@forEach
            val lemma = fields[POLIMORF_LEMMA].lowercase()
            if (!language.admits(lemma)) return@forEach
            lemmas.add(lemma)
            val form = fields[POLIMORF_FORM].lowercase()
            if (form !in knownForms) return@forEach
            val known = formToLemmas.getOrPut(form) { ArrayList(1) }
            // PoliMorf lists one row per grammatical tag, so a form repeats its lemma many times
            // over - `robić` alone arrives on dozens of rows. Without this the shares computed in
            // rankLemmas would be divided by a tag count rather than by a lemma count.
            if (lemma !in known) known.add(lemma)
        }
    }
    return Morphology(lemmas, formToLemmas)
}

/** Opens [this], transparently decompressing when it is gzipped. */
private fun File.openPossiblyCompressed(): InputStream =
    inputStream().buffered().let { if (extension == "gz") GZIPInputStream(it) else it }

/**
 * Every single-word lemma WordNet indexes, from `index.noun`, `index.verb`, `index.adj` and
 * `index.adv`.
 *
 * Read as a set of *base forms*, not as a filter on the dictionary - that distinction is the whole
 * design of [foldInflections] and is worth stating twice. WordNet covers only the four open classes,
 * so `the`, `and`, `for`, `with`, `than` and every other function word is absent from it. Keeping
 * "only WordNet lemmas" would strip the head of the English dictionary of exactly the short common
 * words that make an early board readable.
 *
 * Multi-word entries (`ice_cream`) and anything outside the alphabet are dropped: a board spells one
 * word with no spaces in it.
 */
internal fun readWordNetLemmas(directory: File, language: Language): HashSet<String> {
    require(directory.isDirectory) { "Not a directory: ${directory.path}" }
    val lemmas = HashSet<String>(1 shl 17)
    WORDNET_INDEXES.forEach { name ->
        val file = File(directory, name)
        require(file.isFile) { "Missing ${file.path} - see DICTIONARY.md for where to fetch WordNet" }
        // Latin-1: the licence header at the top of each index file is not UTF-8.
        file.forEachLine(Charsets.ISO_8859_1) { line ->
            // The licence header is indented; every data line starts with the lemma itself.
            if (line.startsWith(" ")) return@forEachLine
            val lemma = line.substringBefore(' ').lowercase()
            if (language.alphabet.isWordValid(lemma)) lemmas.add(lemma)
        }
    }
    return lemmas
}

/**
 * Folds regular inflections into the base they inflect, summing their frequency, and returns what
 * is left.
 *
 * English needs a different rule from Polish and this is it. Polish could ask "is this word a
 * lemma?", because PoliMorf knows every part of speech. WordNet knows four, so the same question
 * asked here answers "no" for *the* and *with*. The question asked instead is **"is this word a
 * regular inflection of something that is itself a word in this dictionary?"** - which a function
 * word can never be, so function words survive without anyone having to list them.
 *
 * A word WordNet indexes in its own right is left alone even when it looks derived: *stressed* is an
 * adjective, not only the past tense of *stress*, and the same goes for a plural that has taken on
 * its own sense. That is WordNet's judgement to make and it is better than a suffix rule's.
 *
 * The frequency goes with the word. Dropping *shadows* without crediting *shadow* would rank the
 * survivor by a fraction of the evidence for it, which is the distortion the Polish build corrects
 * by summing over forms - see `rankLemmas`. Same reasoning, same fix.
 */
internal fun foldInflections(ranked: List<RankedWord>, lemmas: Set<String>): ArrayList<RankedWord> {
    val frequencies = HashMap<String, Double>(ranked.size * 2)
    ranked.forEach { frequencies[it.word] = it.averageReducedFrequency }
    val folded = HashMap<String, Double>(ranked.size * 2)
    val survivors = ArrayList<String>(ranked.size)

    ranked.forEach { entry ->
        val base = entry.word
            .takeIf { it !in lemmas }
            ?.let { word -> basesOf(word).firstOrNull { it in lemmas && it in frequencies } }
        if (base == null) {
            survivors.add(entry.word)
            folded.merge(entry.word, entry.averageReducedFrequency, Double::plus)
        } else {
            folded.merge(base, entry.averageReducedFrequency, Double::plus)
        }
    }

    val result = ArrayList<RankedWord>(survivors.size)
    survivors.forEach { word -> result.add(RankedWord(word, folded.getValue(word))) }
    return result
}

/**
 * Base forms [word] could be a regular inflection of, best candidate first.
 *
 * Deliberately a small rule set rather than WordNet's full `morphy`: this only has to recognise the
 * regular endings, because an irregular form (*went*, *mice*) is indexed by WordNet in its own right
 * and never reaches here. Every candidate is checked against the lemma index before it is believed,
 * so an over-generous rule costs nothing while a missing one leaves an inflection on the board.
 *
 * A stem shorter than [MIN_WORD_LENGTH] is not proposed. It could not be a board word, so folding
 * into it would delete a playable word in favour of one the game cannot use - and it is what turned
 * *his* into the archaic verb *hie*, the single false positive this rule set ever produced.
 */
private fun basesOf(word: String): List<String> {
    val candidates = ArrayList<String>(4)
    SUFFIXES.forEach { (suffix, restorations) ->
        if (!word.endsWith(suffix)) return@forEach
        val stem = word.dropLast(suffix.length)
        if (stem.length < MIN_WORD_LENGTH) return@forEach
        restorations.forEach { restoration -> candidates.add(stem + restoration) }
        // "stopped" -> "stop": a final consonant doubled before the ending.
        if (stem.length > MIN_WORD_LENGTH && stem[stem.length - 1] == stem[stem.length - 2]) {
            candidates.add(stem.dropLast(1))
        }
    }
    return candidates
}

/**
 * Regular endings, each with what may need restoring after stripping it.
 *
 * Bare `-s` restores nothing. *hopes* already gives *hope* by plain stripping, so the `e` rule adds
 * only wrong answers - it is what proposed *hie* for *his*. The longer endings need it, because
 * *hoping* and *hoped* really do lose the `e`.
 */
private val SUFFIXES = listOf(
    "iest" to listOf("y"),
    "ies" to listOf("y"),
    "ied" to listOf("y"),
    "ier" to listOf("y"),
    "ing" to listOf("", "e"),
    "est" to listOf("", "e"),
    "es" to listOf("", "e"),
    "ed" to listOf("", "e"),
    "er" to listOf("", "e"),
    "s" to listOf(""),
)

private val WORDNET_INDEXES = listOf("index.noun", "index.verb", "index.adj", "index.adv")

/**
 * Computes ARF over the Open American National Corpus, which ships as raw text without any
 * frequency data of its own.
 *
 * ARF (Savický & Hlaváčová) rewards words that recur *throughout* a corpus rather than in bursts.
 * For a word occurring `f` times among `n` tokens, let `v = n / f` be the spacing it would have if
 * it were spread out perfectly. Summing `min(gap, v)` over its gaps and dividing by `v` gives back
 * `f` for a perfectly even word and far less for one crammed into a single document. The gaps are
 * taken cyclically, so the corpus has no privileged beginning.
 *
 * Positions are collected in one pass into a flat array and then grouped by counting sort. Storing
 * per-word position lists instead would allocate one object per distinct word for no benefit.
 */
internal fun computeOancFrequencies(corpusDirectory: File, admissible: Set<String>): ArrayList<RankedWord> {
    require(corpusDirectory.isDirectory) { "Not a directory: ${corpusDirectory.path}" }

    val wordIds = HashMap<String, Int>(admissible.size * 2)
    val words = ArrayList<String>(admissible.size)
    admissible.forEach { word ->
        wordIds[word] = words.size
        words.add(word)
    }

    val occurrenceWordId = IntArrayBuilder()
    val occurrencePosition = IntArrayBuilder()
    var tokenCount = 0

    corpusDirectory.walkTopDown()
        .filter { it.isFile && it.extension.lowercase() in CORPUS_EXTENSIONS }
        .forEach { file ->
            forEachToken(file) { token ->
                val id = wordIds[token]
                if (id != null) {
                    occurrenceWordId.add(id)
                    occurrencePosition.add(tokenCount)
                }
                tokenCount++
            }
        }
    require(tokenCount > 0) { "No tokens found under ${corpusDirectory.path}" }
    println("OANC: $tokenCount tokens, ${occurrenceWordId.size} of them admissible")

    return rankByAverageReducedFrequency(
        words = words,
        occurrenceWordId = occurrenceWordId.toIntArray(),
        occurrencePosition = occurrencePosition.toIntArray(),
        tokenCount = tokenCount,
    )
}

private fun rankByAverageReducedFrequency(
    words: List<String>,
    occurrenceWordId: IntArray,
    occurrencePosition: IntArray,
    tokenCount: Int,
): ArrayList<RankedWord> {
    // Counting sort: group occurrence positions by word without allocating a list per word.
    val counts = IntArray(words.size + 1)
    occurrenceWordId.forEach { counts[it + 1]++ }
    for (id in 1..words.size) counts[id] += counts[id - 1]
    val offsets = counts.copyOf()
    val grouped = IntArray(occurrencePosition.size)
    for (index in occurrenceWordId.indices) {
        grouped[offsets[occurrenceWordId[index]]++] = occurrencePosition[index]
    }

    val ranked = ArrayList<RankedWord>(words.size)
    for (id in words.indices) {
        val from = counts[id]
        val until = counts[id + 1]
        val frequency = until - from
        if (frequency == 0) continue
        // Positions arrive in corpus order already, so the group is sorted.
        val spacing = tokenCount.toDouble() / frequency
        var sum = 0.0
        for (index in from until until) {
            val gap = if (index == from) {
                // Cyclic gap: wrap from the last occurrence back round to the first.
                grouped[from] + tokenCount - grouped[until - 1]
            } else {
                grouped[index] - grouped[index - 1]
            }
            sum += minOf(gap.toDouble(), spacing)
        }
        ranked.add(RankedWord(words[id], sum / spacing))
    }
    return ranked
}

/** Lowercases and splits on anything that is not a letter; apostrophes therefore split contractions. */
private inline fun forEachToken(file: File, crossinline action: (String) -> Unit) {
    val token = StringBuilder(MAX_WORD_LENGTH * 2)
    file.forEachLine(Charsets.UTF_8) { line ->
        for (character in line) {
            if (character.isLetter()) {
                token.append(character.lowercaseChar())
            } else if (token.isNotEmpty()) {
                action(token.toString())
                token.setLength(0)
            }
        }
        if (token.isNotEmpty()) {
            action(token.toString())
            token.setLength(0)
        }
    }
}

/** Growable int array; [ArrayList] of boxed integers would cost several times the memory here. */
private class IntArrayBuilder(initialCapacity: Int = 1 shl 20) {
    private var values = IntArray(initialCapacity)
    var size: Int = 0
        private set

    fun add(value: Int) {
        if (size == values.size) values = values.copyOf(values.size * 2)
        values[size++] = value
    }

    fun toIntArray(): IntArray = values.copyOf(size)
}

private const val KWJP_ARF_COLUMN = 3
private val CORPUS_EXTENSIONS = setOf("txt")

private const val POLIMORF_FIELDS = 4
private const val POLIMORF_FORM = 0
private const val POLIMORF_LEMMA = 1
private const val POLIMORF_QUALIFIER = 3
private const val POLIMORF_COMMON = "pospolita"
