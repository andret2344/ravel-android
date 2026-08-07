package eu.andret.ravel.generator

import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * The whole generator, as the app sees it.
 *
 * Everything else in this module is machinery: wall symmetries, path shapes, tries, search budgets.
 * A caller that wants a board wants a board.
 *
 *     val board = Ravel.board(width = 6)
 *     val board = Ravel.board(width = 8, height = 5, difficulty = Difficulty.EASY)
 *     val daily = Ravel.board(width = 6, seed = 20260806)
 *
 * ### This blocks, and not always briefly
 *
 * Generation is a search with random restarts. Typical boards take a few milliseconds, but the
 * distribution has a long tail: 9x9 has been measured at 1.3 seconds and 12x12 at 4.5. Call it off
 * the main thread, or call [boardOrNull] with a deadline and fall back to a smaller grid.
 *
 * ### It can be called off
 *
 * Interrupting the thread stops the search within microseconds and raises [InterruptedException].
 * A screen the player has left should do exactly that instead of paying for a board nobody will
 * see - from Kotlin, run the call inside `runInterruptible` and cancelling the coroutine handles
 * it. Dictionary loading is not interruptible, so a call interrupted during the very first load
 * still finishes that load.
 *
 * ### Dictionaries are cached
 *
 * The first call for a language and difficulty reads a bundled word list and builds a trie, which
 * costs about a tenth of a second and holds 5 MB on English easy up to 17 MB on Polish hard. Later
 * calls reuse it. [preload] moves that cost somewhere it will not be noticed; [forget] gives the
 * memory back, which is what to do from `onTrimMemory` or when the player switches language.
 */
object Ravel {
    private val dictionaries = ConcurrentHashMap<DictionaryKey, Dictionary>()

    /**
     * A board with exactly one solution, blocking until it has one.
     *
     * @param width columns.
     * @param height rows, square by default.
     * @param language which dictionary the hidden words come from.
     * @param difficulty how obscure the words may be and how much the paths wind.
     * @param seed fix it to get the same board every time, for a daily puzzle or a bug report.
     * @throws IllegalArgumentException when the grid is too small to hold a single word.
     * @throws InterruptedException when the calling thread is interrupted mid-search.
     */
    @JvmStatic
    @JvmOverloads
    @Throws(InterruptedException::class)
    fun board(
        width: Int,
        height: Int = width,
        language: Language = Language.POLISH,
        difficulty: Difficulty = Difficulty.HARD,
        seed: Long = Random.Default.nextLong(),
    ): Board = requireNotNull(generate(width, height, language, difficulty, seed, null)) {
        // Only a deadline can make the generator give up, and this overload sets none.
        "Generation returned nothing without a deadline, which should not be possible"
    }

    /**
     * The same board, or null when [timeoutMillis] runs out first.
     *
     * The deadline is the only thing that makes generation fail, so this is the overload to use on
     * a screen that has to show something within a known time. Being called off is not a failure
     * and does not come back as null - it raises [InterruptedException].
     *
     * @throws InterruptedException when the calling thread is interrupted mid-search.
     */
    @JvmStatic
    @JvmOverloads
    @Throws(InterruptedException::class)
    fun boardOrNull(
        width: Int,
        height: Int = width,
        language: Language = Language.POLISH,
        difficulty: Difficulty = Difficulty.HARD,
        seed: Long = Random.Default.nextLong(),
        timeoutMillis: Long,
    ): Board? = generate(width, height, language, difficulty, seed, timeoutMillis)

    /**
     * Loads and caches a dictionary now rather than during the first [board] call.
     *
     * Worth doing on a splash screen or in a background thread at startup: it turns the first
     * board's visible pause into no pause at all.
     */
    @JvmStatic
    @JvmOverloads
    fun preload(language: Language = Language.POLISH, difficulty: Difficulty = Difficulty.HARD) {
        dictionaryFor(language, difficulty)
    }

    /** Drops every cached dictionary. Call from `onTrimMemory` if the tries turn out to hurt. */
    @JvmStatic
    fun forget() = dictionaries.clear()

    /**
     * How the words of a finished board must be listed to the player: lengths only, sorted, with
     * repeats kept. Anything more is the answer.
     */
    @JvmStatic
    fun clues(board: Board): List<Int> = board.wordLengths

    private fun generate(
        width: Int,
        height: Int,
        language: Language,
        difficulty: Difficulty,
        seed: Long,
        timeoutMillis: Long?,
    ): Board? {
        val spec = BoardSpec.forDifficulty(width, height, difficulty)
        // Before the dictionary, so an impossible grid fails immediately instead of after a load.
        spec.validate()
        val generator = BoardGenerator(dictionaryFor(language, difficulty), Random(seed))
        return generator.generate(spec, GenerationStats(), GenerationLimits(timeoutMillis = timeoutMillis))
    }

    private fun dictionaryFor(language: Language, difficulty: Difficulty): Dictionary =
        dictionaries.computeIfAbsent(DictionaryKey(language, difficulty)) { key ->
            Dictionary.loadBundled(key.language, key.difficulty)
        }

    private data class DictionaryKey(val language: Language, val difficulty: Difficulty)
}
