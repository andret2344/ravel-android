package eu.andret.ravel.ui.tutorial

import eu.andret.ravel.game.Puzzle
import eu.andret.ravel.game.Tutorial
import eu.andret.ravel.generator.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The lesson's progress: which step a board is at, which first letter is outlined, and the decoy. */
class TutorialStepsTest {

    private val board = Tutorial.boardFor(Language.POLISH)
    private val decoy = Tutorial.decoyFor(Language.POLISH)

    private fun stepOf(puzzle: Puzzle, state: DecoyState = DecoyState.WAITING): TutorialStep =
        TutorialViewModel.stepOf(puzzle, decoy, state)

    private fun found(vararg words: Int, state: DecoyState = DecoyState.WAITING): Puzzle {
        val puzzle = Puzzle(board, found = words.toSet())
        return TutorialViewModel.guided(puzzle, stepOf(puzzle, state))
    }

    private fun outlined(puzzle: Puzzle): Set<Int> = puzzle.hintedCells

    @Test
    fun `a fresh board outlines the first letter of the first word`() {
        val puzzle = found()
        assertEquals(setOf(board.solution[0].cells.first()), outlined(puzzle))
        assertEquals(TutorialStep.FIRST, stepOf(puzzle))
    }

    @Test
    fun `finding the first word asks for the decoy with nothing outlined`() {
        val puzzle = found(0)
        assertEquals(TutorialStep.DECOY, stepOf(puzzle))
        assertTrue(outlined(puzzle).isEmpty())
    }

    @Test
    fun `tracing the decoy is refused and moves the lesson on`() {
        val traced = found(0).withPath(decoy.placements.first())
        assertEquals(setOf(0), traced.found)
        val state = TutorialViewModel.traced(DecoyState.WAITING, decoy, traced)
        assertEquals(DecoyState.TRACED, state)
        assertEquals(TutorialStep.DECOY_TRACED, stepOf(traced, state))
    }

    @Test
    fun `any placement of the decoy counts`() {
        decoy.placements.forEach { cells ->
            val traced = found(0).withPath(cells)
            assertEquals(cells.toString(), DecoyState.TRACED, TutorialViewModel.traced(DecoyState.WAITING, decoy, traced))
        }
    }

    @Test
    fun `the decoy traced before the first word teaches nothing yet`() {
        val traced = found().withPath(decoy.placements.first())
        assertEquals(DecoyState.WAITING, TutorialViewModel.traced(DecoyState.WAITING, decoy, traced))
    }

    @Test
    fun `dismissing the decoy outlines the second word`() {
        val puzzle = found(0, state = DecoyState.DISMISSED)
        assertEquals(setOf(board.solution[1].cells.first()), outlined(puzzle))
        assertEquals(TutorialStep.SECOND, stepOf(puzzle, DecoyState.DISMISSED))
    }

    @Test
    fun `finding the second word first skips a decoy it has run through`() {
        // Every placement of the Polish decoy starts on the second word's first letter.
        val puzzle = found(1)
        assertEquals(setOf(board.solution[0].cells.first()), outlined(puzzle))
        assertEquals(TutorialStep.SECOND, stepOf(puzzle))
    }

    @Test
    fun `finding the word of the decoy's length skips the decoy`() {
        val puzzle = found(2)
        assertEquals(TutorialStep.SECOND, stepOf(puzzle))
    }

    @Test
    fun `the last word is left without an outline`() {
        val puzzle = found(0, 1, state = DecoyState.DISMISSED)
        assertTrue(outlined(puzzle).isEmpty())
        assertEquals(TutorialStep.LAST, stepOf(puzzle, DecoyState.DISMISSED))
    }

    @Test
    fun `a finished board is the last step`() {
        assertEquals(TutorialStep.DONE, stepOf(found(0, 1, 2)))
    }

    @Test
    fun `tracing the outlined word through the real rules finds it`() {
        val traced = found().withPath(board.solution[0].cells.toList())
        assertEquals(setOf(0), traced.found)
        assertEquals(TutorialStep.DECOY, stepOf(traced))
    }
}
