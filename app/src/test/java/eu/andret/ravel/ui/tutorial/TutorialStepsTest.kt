package eu.andret.ravel.ui.tutorial

import eu.andret.ravel.game.Puzzle
import eu.andret.ravel.game.Tutorial
import eu.andret.ravel.generator.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The lesson's progress: which step a board is at, which first letter is outlined, the decoy and the
 * correction.
 */
class TutorialStepsTest {

    private val board = Tutorial.boardFor(Language.POLISH)
    private val decoy = Tutorial.decoyFor(Language.POLISH)

    private fun stepOf(
        puzzle: Puzzle,
        state: DecoyState = DecoyState.WAITING,
        corrected: Boolean = false,
    ): TutorialStep = TutorialViewModel.stepOf(puzzle, decoy, state, corrected)

    private fun found(
        vararg words: Int,
        state: DecoyState = DecoyState.WAITING,
        corrected: Boolean = false,
    ): Puzzle {
        val puzzle = Puzzle(board, found = words.toSet())
        return TutorialViewModel.guided(puzzle, stepOf(puzzle, state, corrected))
    }

    private fun outlined(puzzle: Puzzle): Set<Int> = puzzle.hintedCells

    /** A lesson on [language]'s board played up to the decoy's explanation, through the real moves. */
    private fun decoyExplained(language: Language = Language.POLISH): Lesson {
        val decoy = Tutorial.decoyFor(language)
        val fresh = Lesson(Puzzle(Tutorial.boardFor(language)), decoy, DecoyState.WAITING)
        val first = play(fresh) { it.withPath(it.board.solution[0].cells.toList()) }
        return play(first) { it.withPath(decoy.placements.first()) }
    }

    /** [lesson] after one move, the way the view model applies it. */
    private fun play(lesson: Lesson, move: (Puzzle) -> Puzzle): Lesson {
        val next = TutorialViewModel.moved(lesson, move(lesson.puzzle))
        return next.copy(puzzle = TutorialViewModel.guided(next.puzzle, next.step))
    }

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
    fun `dismissing the decoy keeps it on the board and outlines the second word`() {
        val lesson = TutorialViewModel.dismissed(decoyExplained())
        assertEquals(TutorialStep.SECOND, lesson.step)
        assertEquals(decoy.placements.first(), lesson.puzzle.path)
        assertEquals(setOf(board.solution[1].cells.first()), outlined(lesson.puzzle))
    }

    @Test
    fun `cutting the decoy back moves on to the last step with the second word still outlined`() {
        val dismissed = TutorialViewModel.dismissed(decoyExplained())
        val cut = play(dismissed) { it.tapped(it.path[1]) }
        assertEquals(decoy.placements.first().take(2), cut.puzzle.path)
        assertTrue(cut.corrected)
        assertEquals(TutorialStep.LAST, cut.step)
        assertEquals(setOf(board.solution[1].cells.first()), outlined(cut.puzzle))
    }

    @Test
    fun `clearing the decoy by tapping its first letter counts as a cut`() {
        val dismissed = TutorialViewModel.dismissed(decoyExplained())
        val cleared = play(dismissed) { it.tapped(it.path.first()) }
        assertTrue(cleared.puzzle.path.isEmpty())
        assertEquals(TutorialStep.LAST, cleared.step)
    }

    @Test
    fun `the cut works on the English board too`() {
        val dismissed = TutorialViewModel.dismissed(decoyExplained(Language.ENGLISH))
        assertEquals(TutorialStep.SECOND, dismissed.step)
        val cut = play(dismissed) { it.tapped(it.path[1]) }
        assertEquals(TutorialStep.LAST, cut.step)
    }

    @Test
    fun `a new path drawn over the decoy is not a cut`() {
        val dismissed = TutorialViewModel.dismissed(decoyExplained())
        // Starts on the decoy's first letter, so the gesture picks it up, then turns elsewhere:
        // the decoy replaced, not corrected.
        val replaced = play(dismissed) { it.withPath(board.solution[1].cells.take(3)) }
        assertFalse(replaced.corrected)
        assertEquals(TutorialStep.SECOND, replaced.step)
    }

    @Test
    fun `cutting a path before the correction step counts for nothing`() {
        val explained = decoyExplained()
        val cut = play(explained) { it.tapped(it.path[1]) }
        assertFalse(cut.corrected)
        assertEquals(TutorialStep.SECOND, TutorialViewModel.dismissed(cut).step)
    }

    @Test
    fun `a word found while the decoy is explained does not skip the correction`() {
        val explained = decoyExplained()
        val second = play(explained) { it.withPath(board.solution[1].cells.toList()) }
        assertEquals(setOf(0, 1), second.puzzle.found)
        assertEquals(TutorialStep.DECOY_TRACED, second.step)
        assertEquals(TutorialStep.SECOND, TutorialViewModel.dismissed(second).step)
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
    fun `two words found without a cut still wait on the correction`() {
        assertEquals(TutorialStep.SECOND, stepOf(found(0, 2)))
        assertEquals(TutorialStep.SECOND, stepOf(found(0, 1, state = DecoyState.DISMISSED)))
    }

    @Test
    fun `the last word is left without an outline`() {
        val puzzle = found(0, 1, state = DecoyState.DISMISSED, corrected = true)
        assertTrue(outlined(puzzle).isEmpty())
        assertEquals(TutorialStep.LAST, stepOf(puzzle, DecoyState.DISMISSED, corrected = true))
    }

    @Test
    fun `a finished board is the last step whether or not anything was cut`() {
        assertEquals(TutorialStep.DONE, stepOf(found(0, 1, 2)))
        assertEquals(TutorialStep.DONE, stepOf(found(0, 1, 2), DecoyState.DISMISSED, corrected = true))
    }

    @Test
    fun `tracing the outlined word through the real rules finds it`() {
        val traced = found().withPath(board.solution[0].cells.toList())
        assertEquals(setOf(0), traced.found)
        assertEquals(TutorialStep.DECOY, stepOf(traced))
    }

    @Test
    fun `a cut is a shorter start of the path, the empty one included`() {
        val path = listOf(1, 5, 6)
        assertTrue(TutorialViewModel.cutBack(path, listOf(1, 5)))
        assertTrue(TutorialViewModel.cutBack(path, listOf(1)))
        assertTrue(TutorialViewModel.cutBack(path, emptyList()))
        assertFalse(TutorialViewModel.cutBack(path, path))
        assertFalse(TutorialViewModel.cutBack(path, listOf(1, 2)))
        assertFalse(TutorialViewModel.cutBack(path, listOf(1, 5, 6, 7)))
        assertFalse(TutorialViewModel.cutBack(emptyList(), emptyList()))
    }
}
