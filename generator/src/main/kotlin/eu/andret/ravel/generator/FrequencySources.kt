package eu.andret.ravel.generator

import java.io.File

/**
 * Reads the KWJP100 frequency list, which already carries an ARF column.
 *
 * Rows whose token is not an admissible word are dropped before parsing, which also disposes of
 * punctuation tokens whose quoted CSV fields would break the naive split.
 */
internal fun readKwjpFrequencies(file: File, admissible: Set<String>): ArrayList<RankedWord> {
    val ranked = ArrayList<RankedWord>(1 shl 18)
    file.useLines(Charsets.UTF_8) { lines ->
        lines.drop(1).forEach { line ->
            val firstSeparator = line.indexOf(',')
            if (firstSeparator <= 0) return@forEach
            val word = line.substring(0, firstSeparator)
            if (word !in admissible) return@forEach
            val fields = line.split(',')
            if (fields.size < KWJP_ARF_COLUMN + 1) return@forEach
            val arf = fields[KWJP_ARF_COLUMN].toDoubleOrNull() ?: return@forEach
            ranked.add(RankedWord(word, arf))
        }
    }
    return ranked
}

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
