package eu.andret.ravel.game

import eu.andret.ravel.generator.Board
import eu.andret.ravel.generator.Placement
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A hand-built 3x3 whose three words have three different lengths, and whose solution is
 * deliberately **not** in length order.
 *
 * ```
 *  0  1  2      A  A  C
 *  3  4  5      B  B  C
 *  6  7  8      B  B  C
 * ```
 *
 * The solution is listed longest-first, so every assertion about position here would pass by luck if
 * the ordering were dropped and the solution's own order used instead. That is the point: the order
 * is the only thing making these results comparable between two players, and nothing else in the app
 * would notice it changing.
 */
class WordMarkTest {

    private val long = intArrayOf(3, 4, 7, 6)
    private val middle = intArrayOf(2, 5, 8)
    private val short = intArrayOf(0, 1)

    private val board = Board(
        width = 3,
        height = 3,
        letters = charArrayOf('A', 'A', 'C', 'B', 'B', 'C', 'B', 'B', 'C'),
        solution = listOf(
            Placement("bbbb", long),
            Placement("aa", short),
            Placement("ccc", middle),
        ),
    )

    private val fresh = Puzzle(board)

    @Test
    fun `a board finished with nothing given away is unaided throughout`() {
        assertEquals(
            listOf(
                WordResult(2, WordMark.UNAIDED),
                WordResult(3, WordMark.UNAIDED),
                WordResult(4, WordMark.UNAIDED),
            ),
            fresh.wordResults(),
        )
    }

    @Test
    fun `results are ordered by word length and not by solution order`() {
        // The four-letter word is index 0 in the solution and last in the lengths the player sees.
        val state = fresh.copy(hints = mapOf(0 to 4))
        assertEquals(
            listOf(WordMark.UNAIDED, WordMark.UNAIDED, WordMark.REVEALED),
            state.wordResults().map(WordResult::mark),
        )
        assertEquals(listOf(2, 3, 4), state.wordResults().map(WordResult::length))
    }

    @Test
    fun `that order is the one the clues are shown in`() {
        assertEquals(fresh.clues.map(Clue::length), fresh.wordResults().map(WordResult::length))
    }

    @Test
    fun `a word with some of it showing is partly revealed`() {
        // The two-letter word is index 1 in the solution and first in the lengths.
        val state = fresh.copy(hints = mapOf(1 to 1))
        assertEquals(
            listOf(WordMark.PARTLY_REVEALED, WordMark.UNAIDED, WordMark.UNAIDED),
            state.wordResults().map(WordResult::mark),
        )
    }

    @Test
    fun `a word revealed to its last cell is revealed however it was paid for`() {
        // Both routes are played out rather than asserted about, because the claim is about what
        // Puzzle.hinted leaves behind and not about what this test can construct. Two letters bought
        // one at a time and the word bought outright cost the player different amounts and leave the
        // same cells showing, and WordMark says the second of those is the part it reports on.
        val boughtOutright = fresh.hinted(Hint.WORD)
        val letterByLetter = fresh.hinted(Hint.LETTER).hinted(Hint.LETTER)
        // Both hints land on the two-letter word: it is the one nearest the top left, which is how
        // Puzzle.target breaks a tie between words nobody has started.
        assertEquals(2, boughtOutright.hints[1])
        assertEquals(2, letterByLetter.hints[1])
        assertEquals(WordMark.REVEALED, boughtOutright.wordResults().first().mark)
        assertEquals(boughtOutright.wordResults(), letterByLetter.wordResults())
    }

    @Test
    fun `a hint on a word the player had nearly traced still marks it revealed`() {
        // Puzzle.hinted counts from however far along the player already was, so a letter bought one
        // cell from the end reveals the whole word. The mark describes the board, not the purchase.
        val state = fresh.withPath(listOf(3, 4, 7)).hinted(Hint.LETTER)
        assertEquals(4, state.hints[0])
        assertEquals(WordMark.REVEALED, state.wordResults().last().mark)
    }

    @Test
    fun `every mark survives a round trip through its code`() {
        WordMark.entries.forEach { mark ->
            assertEquals(mark, WordMark.byCode(mark.code))
        }
    }

    @Test
    fun `a code no release wrote is not a mark`() {
        assertEquals(null, WordMark.byCode('?'))
    }
}
