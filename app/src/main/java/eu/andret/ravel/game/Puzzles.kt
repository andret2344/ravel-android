package eu.andret.ravel.game

import eu.andret.ravel.generator.Language
import eu.andret.ravel.generator.Ravel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

/**
 * Builds the board a [PuzzleId] names.
 *
 * One place for it because every mode needs the same three things and gets them wrong differently:
 * generation off the main thread, a deadline, and above all a seed that never varies. A retry that
 * reached for a fresh seed would quietly hand the player a different level 40 from the one everyone
 * else has - so there is no seed here to reach for, only [PuzzleSpec.of].
 */
object Puzzles {
    /**
     * The puzzle for [id] in [language], or null when generation ran out of time.
     *
     * Cancelling the calling coroutine interrupts the search rather than merely abandoning it, so
     * leaving a screen stops the work instead of paying for a board nobody will see.
     *
     * The dispatcher is named rather than taken as a parameter, which Sonar asks for and which would
     * be wrong twice here. A test dispatcher does not run on a thread that can be interrupted, so
     * the seam would quietly trade away the cancellation this method's whole contract is about; and
     * there is nothing to gain by moving the work elsewhere anyway, since a board is a fixed amount
     * of CPU and the seed already makes it deterministic.
     */
    @Suppress("kotlin:S6310")
    suspend fun generate(
        id: PuzzleId,
        language: Language,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    ): Puzzle? {
        val spec = PuzzleSpec.of(id)
        val board = runInterruptible(Dispatchers.Default) {
            Ravel.boardOrNull(
                width = spec.size,
                height = spec.size,
                language = language,
                difficulty = spec.difficulty,
                seed = spec.seed,
                timeoutMillis = timeoutMillis,
                maxWordRank = spec.maxWordRank,
            )
        }
        return board?.let(::Puzzle)
    }

    /**
     * Generous, because the cost being waited on is mostly the one-off dictionary load rather than
     * the search itself. Boards of the sizes [PuzzleSpec] hands out are found in milliseconds once
     * the trie is warm.
     */
    const val DEFAULT_TIMEOUT_MILLIS = 20_000L
}
