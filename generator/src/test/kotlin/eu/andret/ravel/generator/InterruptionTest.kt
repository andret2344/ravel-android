package eu.andret.ravel.generator

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Interruption is what a screen the player has left uses to stop paying for a board nobody will
 * see, so these tests are about the mechanism rather than about any one board.
 *
 * Nothing here races a single generation against a clock: a 12x12 board takes anywhere from
 * milliseconds to seconds, so a test that interrupts one "mid-search" is really a test of whether
 * the machine was busy that second. The timing test below keeps a worker generating in a loop
 * instead, which cannot finish early.
 */
class InterruptionTest {

    private val dictionary = Dictionary.loadBundled(Language.POLISH, Difficulty.HARD)

    @Test
    fun `a thread already interrupted never starts searching`() {
        Thread.currentThread().interrupt()
        try {
            var reported = false
            try {
                BoardGenerator(dictionary, Random(1)).generate(BoardSpec(6, 6))
            } catch (interrupted: InterruptedException) {
                reported = true
            }
            assertTrue(reported, "generation ran despite an interrupted thread")
            assertFalse(
                Thread.currentThread().isInterrupted,
                "reporting the interruption as an exception must clear the flag",
            )
        } finally {
            // Whatever happened above, this thread goes back to the runner clean.
            Thread.interrupted()
        }
    }

    @Test
    fun `an interrupted uniqueness check reports no answer rather than a count`() {
        val board = BoardGenerator(dictionary, Random(3)).generate(BoardSpec(6, 6))!!
        val solver = Solver(Grid(board.width, board.height), board.letters, dictionary)
        Thread.currentThread().interrupt()
        try {
            // The danger being ruled out: an abandoned search reporting "one solution" and a board
            // with several going out as unique.
            assertEquals(
                Solver.UNKNOWN_SOLUTION_COUNT,
                solver.countSolutions(board.wordLengths, limit = 2),
                "an interrupted search must not be read as a count",
            )
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun `interrupting a running generator stops it promptly`() {
        val started = CountDownLatch(1)
        var reported = false

        // Generating in a loop, so the worker is certainly still busy when the interrupt lands
        // whatever the machine's speed. Which generation gets interrupted does not matter.
        val worker = Thread {
            val generator = BoardGenerator(dictionary, Random(7))
            started.countDown()
            try {
                while (true) generator.generate(BoardSpec(9, 9))
            } catch (interrupted: InterruptedException) {
                reported = true
            }
        }
        worker.start()
        assertTrue(started.await(5, TimeUnit.SECONDS), "worker never started")
        Thread.sleep(SEARCH_WARMUP_MILLIS)

        val interruptedAt = System.nanoTime()
        worker.interrupt()
        worker.join(JOIN_TIMEOUT_MILLIS)
        val reactionMillis = (System.nanoTime() - interruptedAt) / 1_000_000

        assertFalse(worker.isAlive, "generator kept running ${reactionMillis}ms after the interrupt")
        assertTrue(reported, "generator stopped without reporting the interruption")
        assertTrue(
            reactionMillis < REACTION_LIMIT_MILLIS,
            "took ${reactionMillis}ms to stop, expected under $REACTION_LIMIT_MILLIS",
        )
    }

    private companion object {
        /** Long enough for the worker to be somewhere inside a search rather than starting one. */
        const val SEARCH_WARMUP_MILLIS = 50L

        /** Generous against a loaded machine; the mechanism reacts within one node-budget slice. */
        const val REACTION_LIMIT_MILLIS = 2_000L
        const val JOIN_TIMEOUT_MILLIS = 10_000L
    }
}
