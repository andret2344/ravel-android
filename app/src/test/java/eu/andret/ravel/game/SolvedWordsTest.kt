package eu.andret.ravel.game

import eu.andret.ravel.generator.Board
import eu.andret.ravel.generator.Language
import eu.andret.ravel.generator.Placement
import eu.andret.ravel.settings.ShareCard
import eu.andret.ravel.settings.describes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The same hand-built 3x3 as `WordMarkTest`, solution listed longest-first, so an order that merely
 * followed the solution would fail here rather than pass by luck.
 *
 * ```
 *  0  1  2      A  A  C
 *  3  4  5      B  B  C
 *  6  7  8      B  B  C
 * ```
 */
class SolvedWordsTest {

    private fun cardOf(puzzle: Puzzle) =
        ShareCard(ordinal = 1, language = Language.POLISH, size = 3, words = puzzle.wordResults())

    private val board = Board(
        width = 3,
        height = 3,
        letters = charArrayOf('A', 'A', 'C', 'B', 'B', 'C', 'B', 'B', 'C'),
        solution = listOf(
            Placement("bbbb", intArrayOf(3, 4, 7, 6)),
            Placement("aa", intArrayOf(0, 1)),
            Placement("ccc", intArrayOf(2, 5, 8)),
        ),
    )

    @Test
    fun `the words come in the order the lengths are shown in`() {
        val puzzle = Puzzle(board)
        assertEquals(listOf("aa", "ccc", "bbbb"), puzzle.solvedWords(Language.POLISH).words)
        assertEquals(
            puzzle.clues.map(Clue::length),
            puzzle.solvedWords(Language.POLISH).words.map(String::length),
        )
    }

    @Test
    fun `the words and the share card's entries describe the same words`() {
        val puzzle = Puzzle(board)
        assertEquals(
            puzzle.wordResults().map(WordResult::length),
            puzzle.solvedWords(Language.ENGLISH).words.map(String::length),
        )
    }

    @Test
    fun `the language is the one the board was built from`() {
        assertEquals(Language.ENGLISH, Puzzle(board).solvedWords(Language.ENGLISH).language)
    }

    @Test
    fun `a Polish word opens sjp_pl with its letters percent-encoded`() {
        assertEquals("https://sjp.pl/dzierga%C4%87", definitionUrl("dziergać", Language.POLISH))
    }

    @Test
    fun `an English word opens its English entry on Wiktionary`() {
        assertEquals(
            "https://en.wiktionary.org/wiki/ravel#English",
            definitionUrl("ravel", Language.ENGLISH),
        )
    }

    @Test
    fun `a space is encoded for a path and not for a query`() {
        assertEquals("https://sjp.pl/a%20b", definitionUrl("a b", Language.POLISH))
    }

    @Test
    fun `the words describe the card built from the same board`() {
        val puzzle = Puzzle(board)
        val card = cardOf(puzzle)
        assertTrue(puzzle.solvedWords(Language.POLISH).describes(card))
    }

    @Test
    fun `words from another dictionary do not describe the card`() {
        val puzzle = Puzzle(board)
        val card = cardOf(puzzle)
        assertFalse(puzzle.solvedWords(Language.ENGLISH).describes(card))
    }

    @Test
    fun `words in another order do not describe the card`() {
        val puzzle = Puzzle(board)
        val card = cardOf(puzzle)
        assertFalse(SolvedWords(Language.POLISH, listOf("bbbb", "ccc", "aa")).describes(card))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a board with no words is refused`() {
        SolvedWords(Language.POLISH, emptyList())
    }
}
