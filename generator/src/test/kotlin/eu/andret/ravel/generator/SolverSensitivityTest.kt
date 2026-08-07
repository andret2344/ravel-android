package eu.andret.ravel.generator

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Guards against the failure mode that would make every other test pass for the wrong reason: a
 * solver that silently misses tilings would report every board as unique, and generation would
 * happily ship ambiguous puzzles.
 *
 * Boards are generated against a small dictionary and then re-counted against the full one. The
 * extra words open up alternative tilings, so a working solver must find a second solution on a
 * decent share of those boards.
 */
class SolverSensitivityTest {

    @Test
    fun `boards unique in a small dictionary turn ambiguous in the full one`() {
        val small = Dictionary.loadBundled(Language.POLISH, maxRank = 5_000)
        val full = Dictionary.loadBundled(Language.POLISH)
        val generator = BoardGenerator(small, Random(31337))

        var ambiguous = 0
        repeat(SAMPLE) {
            // Short words leave the most room for competing tilings, which is what this test needs
            // to observe; a board of few long words is constrained enough to stay unique even when
            // the dictionary grows thirtyfold. Hence the low target word length.
            val spec = BoardSpec(5, 5, minWallRatio = 0.0, maxWallRatio = 0.08)
            val board = assertNotNull(generator.generate(spec), "generation failed")
            val solver = Solver(Grid(board.width, board.height), board.letters, full)
            if (solver.countSolutions(board.wordLengths, limit = 2) > 1) ambiguous++
        }

        assertTrue(
            ambiguous > 0,
            "The full dictionary produced no extra tilings in $SAMPLE boards, " +
                "which means the solver is not exploring them",
        )
        println("Ambiguous under the full dictionary: $ambiguous/$SAMPLE")
    }

    private companion object {
        const val SAMPLE = 40
    }
}
