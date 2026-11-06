package eu.andret.ravel.ui.tutorial

import eu.andret.ravel.game.Puzzle
import eu.andret.ravel.game.Tutorial
import eu.andret.ravel.generator.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The lesson's progress: which step a board is at, and which first letter is outlined. */
class TutorialStepsTest {

    private val board = Tutorial.boardFor(Language.POLISH)

    private fun found(vararg words: Int): Puzzle =
        TutorialViewModel.guided(Puzzle(board, found = words.toSet()))

    private fun outlined(puzzle: Puzzle): Set<Int> = puzzle.hintedCells

    @Test
    fun `a fresh board outlines the first letter of the first word`() {
        val puzzle = found()
        assertEquals(setOf(board.solution[0].cells.first()), outlined(puzzle))
        assertEquals(TutorialStep.FIRST, TutorialViewModel.stepOf(puzzle))
    }

    @Test
    fun `finding the first word moves the outline to the second`() {
        val puzzle = found(0)
        assertEquals(setOf(board.solution[1].cells.first()), outlined(puzzle))
        assertEquals(TutorialStep.SECOND, TutorialViewModel.stepOf(puzzle))
    }

    @Test
    fun `finding the second word first still outlines the first`() {
        val puzzle = found(1)
        assertEquals(setOf(board.solution[0].cells.first()), outlined(puzzle))
        assertEquals(TutorialStep.SECOND, TutorialViewModel.stepOf(puzzle))
    }

    @Test
    fun `the last word is left without an outline`() {
        val puzzle = found(0, 1)
        assertTrue(outlined(puzzle).isEmpty())
        assertEquals(TutorialStep.LAST, TutorialViewModel.stepOf(puzzle))
    }

    @Test
    fun `a finished board is the last step`() {
        assertEquals(TutorialStep.DONE, TutorialViewModel.stepOf(found(0, 1, 2)))
    }

    @Test
    fun `tracing the outlined word through the real rules finds it`() {
        val traced = TutorialViewModel.guided(found().withPath(board.solution[0].cells.toList()))
        assertEquals(setOf(0), traced.found)
        assertEquals(TutorialStep.SECOND, TutorialViewModel.stepOf(traced))
    }
}
