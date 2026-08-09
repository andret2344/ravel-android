package eu.andret.ravel.game

import eu.andret.ravel.generator.Board
import eu.andret.ravel.generator.Placement
import eu.andret.ravel.generator.WALL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The board below is hand-built rather than generated: these rules are about paths and taps, and a
 * fixed 4x3 grid states exactly which cells the assertions mean.
 *
 * ```
 *  0  1  2  3      K  O  T  A
 *  4  5  6  7      #  S  E  R
 *  8  9 10 11      P  I  K  A
 * ```
 * The letters are decoration - nothing here reads them - so the three placements simply carve the
 * free cells up: one across the top, one down the right and along the bottom, one for the rest.
 */
class PuzzleTest {

    private val kot = intArrayOf(0, 1, 2)
    private val raki = intArrayOf(3, 7, 11, 10)
    private val ser = intArrayOf(5, 6, 9, 8)

    private val board = Board(
        width = 4,
        height = 3,
        letters = charArrayOf('K', 'O', 'T', 'A', WALL, 'S', 'E', 'R', 'P', 'I', 'K', 'A'),
        solution = listOf(Placement("kot", kot), Placement("raki", raki), Placement("ser", ser)),
    )

    private val fresh = Puzzle(board)

    @Test
    fun `tracing a word finds it and clears the path`() {
        val state = fresh.withPath(listOf(0, 1, 2))
        assertEquals(setOf(0), state.found)
        assertEquals(emptyList<Int>(), state.path)
        assertEquals(setOf(0, 1, 2), state.solvedCells)
    }

    @Test
    fun `a word traced from the far end is not the word`() {
        // Those cells drawn that way round spell the word backwards, so nothing is found and the
        // path simply stays marked for the player to correct.
        val state = fresh.withPath(listOf(2, 1, 0))
        assertTrue(state.found.isEmpty())
        assertEquals(listOf(2, 1, 0), state.path)
    }

    @Test
    fun `an unfinished path stays marked`() {
        val state = fresh.withPath(listOf(0, 1))
        assertTrue(state.found.isEmpty())
        assertEquals(listOf(0, 1), state.path)
    }

    @Test
    fun `tapping the first marked cell clears the path`() {
        assertEquals(emptyList<Int>(), fresh.withPath(listOf(0, 1, 5)).tapped(0).path)
    }

    @Test
    fun `tapping a marked cell cuts the path back to it`() {
        val state = fresh.withPath(listOf(3, 7, 11, 10, 9)).tapped(7)
        assertEquals(listOf(3, 7), state.path)
    }

    @Test
    fun `tapping next to the end of the path extends it`() {
        assertEquals(listOf(0, 1), fresh.withPath(listOf(0)).tapped(1).path)
    }

    @Test
    fun `tapping away from the path starts a new one`() {
        assertEquals(listOf(9), fresh.withPath(listOf(0, 1)).tapped(9).path)
    }

    @Test
    fun `tapping the cell that completes a word finds it`() {
        val state = fresh.withPath(listOf(0, 1)).tapped(2)
        assertEquals(setOf(0), state.found)
        assertEquals(emptyList<Int>(), state.path)
    }

    @Test
    fun `walls and solved cells ignore taps`() {
        val solved = fresh.withPath(listOf(0, 1, 2))
        assertEquals(emptyList<Int>(), solved.tapped(4).path)
        assertEquals(emptyList<Int>(), solved.tapped(1).path)
    }

    @Test
    fun `the first hint opens the word earliest on the board`() {
        val state = fresh.hinted(Hint.LETTER)
        // Nothing is under way, so every word is equally advanced and position decides: the top
        // word starts at cell 0, before the others at 3 and 5.
        assertEquals(listOf(0), state.path)
        assertEquals(setOf(0), state.hintedCells)
    }

    @Test
    fun `hints continue the word already under way`() {
        // Two cells of the third word are marked, so it is further along than the untouched words
        // even though it starts later on the board.
        assertEquals(listOf(5, 6, 9), fresh.withPath(listOf(5, 6)).hinted(Hint.LETTER).path)
    }

    @Test
    fun `a hint cuts a path back to the part that was right`() {
        // Right for two cells, then off into a cell that word never visits.
        val state = fresh.withPath(listOf(5, 6, 7)).hinted(Hint.LETTER)
        assertEquals(listOf(5, 6, 9), state.path)
        assertFalse(7 in state.path)
    }

    @Test
    fun `a path running the wrong way counts for nothing`() {
        // Traced from a word's far end. Those cells are not a partly-right answer - a word only
        // reads one way - so the hint treats the word as untouched and opens it at its beginning,
        // replacing what was marked.
        assertEquals(listOf(0), fresh.withPath(listOf(10, 11)).hinted(Hint.LETTER).path)
    }

    @Test
    fun `a revealed cell survives clearing the path`() {
        val hinted = fresh.hinted(Hint.LETTER)
        val cleared = hinted.tapped(hinted.path.first())
        assertEquals(emptyList<Int>(), cleared.path)
        assertEquals(setOf(0), cleared.hintedCells)
        // And the next hint carries on from what was paid for rather than repeating it.
        assertEquals(listOf(0, 1), cleared.hinted(Hint.LETTER).path)
    }

    @Test
    fun `revealing the last cell of a word finds it`() {
        val state = fresh.withPath(listOf(0, 1)).hinted(Hint.LETTER)
        assertEquals(setOf(0), state.found)
        assertEquals(emptyList<Int>(), state.path)
        // A found word's hints stop being drawn - the whole word is drawn as found instead.
        assertTrue(state.hintedCells.isEmpty())
    }

    @Test
    fun `hints move on once a word is found`() {
        assertEquals(listOf(3), fresh.withPath(listOf(0, 1, 2)).hinted(Hint.LETTER).path)
    }

    @Test
    fun `nothing is left to hint once every word is found`() {
        val complete = fresh
            .withPath(kot.toList())
            .withPath(raki.toList())
            .withPath(ser.toList())
        assertTrue(complete.isComplete)
        assertEquals(complete, complete.hinted(Hint.LETTER))
    }

    @Test
    fun `a word hint finishes the word outright`() {
        val state = fresh.hinted(Hint.WORD)
        assertEquals(setOf(0), state.found)
        assertEquals(emptyList<Int>(), state.path)
        // Nothing is left half-revealed: the word is drawn as found, not as hinted.
        assertTrue(state.hintedCells.isEmpty())
    }

    @Test
    fun `both hints act on the same word`() {
        // Whichever the player buys, it is the word they are closest to finishing - here the third
        // one, two cells in, rather than the one that starts earliest on the board.
        val started = fresh.withPath(listOf(5, 6))
        assertEquals(listOf(5, 6, 9), started.hinted(Hint.LETTER).path)
        assertEquals(setOf(2), started.hinted(Hint.WORD).found)
    }

    @Test
    fun `a word hint replaces a path that went wrong`() {
        val state = fresh.withPath(listOf(5, 6, 7)).hinted(Hint.WORD)
        assertEquals(setOf(2), state.found)
        assertEquals(emptyList<Int>(), state.path)
    }

    @Test
    fun `word hints clear the board one word at a time`() {
        val complete = generateSequence(fresh) { it.hinted(Hint.WORD) }
            .take(board.solution.size + 1)
            .last()
        assertTrue(complete.isComplete)
        assertEquals(complete, complete.hinted(Hint.WORD))
    }

    @Test
    fun `solving pays far less than buying the answer`() {
        // The hole this closes: if a board paid more than the hints that finish it, the fastest way
        // to earn would be to buy words. The cheapest board here is three words, so 300 coins for a
        // reward of 25.
        //
        // An inequality and not a figure, on purpose. This is the rule the prices have to keep, and
        // it has now survived them being cut to a quarter without a line of it being edited - which
        // is exactly what a test of a rule should do when a number underneath it moves.
        val cheapestBoardByHints = board.solution.size * Hint.WORD.cost
        assertTrue(Rewards.FOR_COMPLETING < cheapestBoardByHints)
    }

    @Test
    fun `a wallet cannot be spent below zero`() {
        assertEquals(210, Rewards.afterSpending(250, Hint.LETTER.cost))
        assertEquals(0, Rewards.afterSpending(Hint.WORD.cost, Hint.WORD.cost))
        assertEquals(null, Rewards.afterSpending(Hint.LETTER.cost - 1, Hint.LETTER.cost))
    }

    @Test
    fun `a word costs two and a half letters`() {
        // The prices are a game decision, not an implementation detail: changing them is a change
        // to what the game charges, and it should not be possible to do by accident.
        assertEquals(40, Hint.LETTER.cost)
        assertEquals(100, Hint.WORD.cost)

        // The ratio, which is the part that is a rule rather than a number - see Hint. It puts the
        // break-even at two letters, and that is asserted rather than described: two letters have to
        // stay cheaper than the word and three have to stay dearer, or the two hints stop being a
        // choice about the board and become a sum the player is expected to do.
        assertTrue(2 * Hint.LETTER.cost < Hint.WORD.cost)
        assertTrue(3 * Hint.LETTER.cost > Hint.WORD.cost)
    }

    @Test
    fun `clues carry which lengths are done without reordering`() {
        val state = fresh.withPath(listOf(0, 1, 2))
        assertEquals(listOf(3, 3, 4), state.clues.map(Clue::length))
        assertEquals(listOf(true, false, false), state.clues.map(Clue::found))
    }

    @Test
    fun `each kind of hint is counted on its own`() {
        val state = fresh.hinted(Hint.LETTER).hinted(Hint.LETTER).hinted(Hint.WORD)
        assertEquals(2, state.boughtOf(Hint.LETTER))
        assertEquals(1, state.boughtOf(Hint.WORD))
    }

    @Test
    fun `a word bought outright is not the same as its letters bought one by one`() {
        // Why the counts exist at all. "kot" is three cells, so three letters and one word leave the
        // board looking identical - and they are not the same admission by the player.
        val byLetter = generateSequence(fresh) { it.hinted(Hint.LETTER) }.take(4).last()
        val byWord = fresh.hinted(Hint.WORD)
        assertEquals(byLetter.solvedCells, byWord.solvedCells)
        assertEquals(3, byLetter.boughtOf(Hint.LETTER))
        assertEquals(0, byLetter.boughtOf(Hint.WORD))
        assertEquals(0, byWord.boughtOf(Hint.LETTER))
        assertEquals(1, byWord.boughtOf(Hint.WORD))
    }

    @Test
    fun `a hint with nothing left to reveal is not counted`() {
        // It is not charged for either - see DailyViewModel - so counting it would put a purchase on
        // the record that the player never made.
        val complete = generateSequence(fresh) { it.hinted(Hint.WORD) }
            .take(board.solution.size + 1)
            .last()
        assertEquals(board.solution.size, complete.boughtOf(Hint.WORD))
        assertEquals(board.solution.size, complete.hinted(Hint.LETTER).boughtOf(Hint.WORD))
        assertEquals(0, complete.hinted(Hint.LETTER).boughtOf(Hint.LETTER))
    }

    @Test
    fun `counts survive the tidying up that clears a path`() {
        val hinted = fresh.hinted(Hint.LETTER)
        assertEquals(1, hinted.tapped(hinted.path.first()).boughtOf(Hint.LETTER))
    }
}
