package eu.andret.ravel.generator

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Guards the heap the shipped dictionaries hold, which is a shipping constraint rather than a taste
 * one: this runs on API 24 devices, and `RavelApplication` keeps one loaded for as long as the
 * player is in a board.
 *
 * The assertion is on the word count and not on the megabytes, deliberately. Bytes are what anybody
 * actually cares about, but measuring them means leaning on `System.gc()` and a subtraction that
 * moves between runs, and a size guard that fails once a fortnight for no reason gets deleted rather
 * than investigated. The count is exact, and the mapping from count to bytes is what the printout
 * below is for - rerun this after any change to [Trie] and move the constant if the ratio moved.
 *
 * Measured 2026-08-08, one dictionary resident at a time:
 *
 * | words | Polish | English |
 * |---|---|---|
 * | 10 000 | 10.2 MB | 7.2 MB |
 * | 25 000 | 24.1 MB | 17.0 MB |
 * | 35 000 | 33.2 MB | 23.0 MB |
 * | all | 48.8 MB (51 685) | 39.5 MB (61 234) |
 */
class DictionaryFootprint {

    @Test
    fun `the shipped difficulty stays inside its heap budget`() {
        Language.entries.forEach { language ->
            Ravel.forget()
            val before = usedBytes()
            val dictionary = Dictionary.loadBundled(language, Difficulty.HARD)
            val megabytes = (usedBytes() - before) / 1024.0 / 1024.0
            println("${language.code}: ${dictionary.words.size} words, %.1f MB".format(megabytes))
            assertTrue(
                dictionary.words.size <= WORD_BUDGET,
                "${language.code} loads ${dictionary.words.size} words, past the $WORD_BUDGET budget " +
                    "(measured %.1f MB). Raising Difficulty.HARD.maxRank costs about 1 MB per ".format(megabytes) +
                    "thousand Polish words, and the words down there are the ones worth losing.",
            )
        }
    }

    private fun usedBytes(): Long {
        val runtime = Runtime.getRuntime()
        repeat(4) {
            System.gc()
            Thread.sleep(60)
        }
        return runtime.totalMemory() - runtime.freeMemory()
    }

    private companion object {
        /** [Difficulty.HARD]'s own cap. Stated separately so raising that has to be deliberate. */
        const val WORD_BUDGET = 35_000
    }
}
