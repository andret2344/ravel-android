package eu.andret.ravel.generator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RavelTest {

    @Test
    fun `a board comes back from nothing but a width`() {
        val board = Ravel.board(width = 5)
        assertEquals(5, board.width)
        assertEquals(5, board.height)
        assertTrue(board.solution.isNotEmpty())
    }

    @Test
    fun `the same seed gives the same board`() {
        val first = Ravel.board(width = 6, seed = 20260806)
        val second = Ravel.board(width = 6, seed = 20260806)
        assertEquals(first.letters.concatToString(), second.letters.concatToString())
        assertEquals(
            first.solution.map { it.word },
            second.solution.map { it.word },
        )
    }

    @Test
    fun `every overload reaches the same generator`() {
        val board = Ravel.board(
            width = 8,
            height = 5,
            language = Language.ENGLISH,
            difficulty = Difficulty.EASY,
            seed = 4711,
        )
        assertEquals(8, board.width)
        assertEquals(5, board.height)
        val dictionary = Dictionary.loadBundled(Language.ENGLISH, Difficulty.EASY)
        board.solution.forEach { assertTrue(dictionary.isWord(it.word), it.word) }
    }

    @Test
    fun `a deadline is the only way to get nothing back`() {
        // One millisecond is not enough to draw a 12x12 layout, let alone prove it unique.
        assertNull(Ravel.boardOrNull(width = 12, seed = 1, timeoutMillis = 1))
        assertNotNull(Ravel.boardOrNull(width = 5, seed = 1, timeoutMillis = 30_000))
    }

    @Test
    fun `an impossible grid fails before anything is loaded`() {
        val failure = assertFailsWith<IllegalArgumentException> { Ravel.board(width = 1, height = 2) }
        assertTrue(failure.message.orEmpty().contains("too few for even one"), failure.message.orEmpty())
    }

    @Test
    fun `the player is told lengths and nothing else`() {
        val board = Ravel.board(width = 6, seed = 99)
        val clues = Ravel.clues(board)
        assertEquals(board.solution.size, clues.size)
        assertEquals(clues.sorted(), clues)
        assertEquals(board.solution.sumOf { it.word.length }, clues.sum())
    }

    @Test
    fun `preloading twice keeps one dictionary`() {
        Ravel.forget()
        Ravel.preload(Language.POLISH, Difficulty.HARD)
        Ravel.preload(Language.POLISH, Difficulty.HARD)
        // Nothing observable but the absence of a second load; the board still has to come out.
        assertNotNull(Ravel.board(width = 5, seed = 7))
    }
}
