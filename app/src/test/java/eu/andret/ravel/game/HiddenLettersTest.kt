package eu.andret.ravel.game

import eu.andret.ravel.generator.Board
import eu.andret.ravel.generator.Placement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HiddenLettersTest {

    /**
     * A board of words of [lengths], laid end to end along one row. The rule reads nothing but the
     * placements, so the letters and the shape are filler.
     */
    private fun boardOf(vararg lengths: Int): Board {
        var next = 0
        val placements = lengths.map { length ->
            Placement("x".repeat(length), IntArray(length) { next++ })
        }
        return Board(width = next, height = 1, letters = CharArray(next) { 'X' }, solution = placements)
    }

    private val boards = listOf(
        boardOf(3, 4, 5),
        boardOf(4, 4, 5, 6),
        boardOf(5, 6, 6, 7, 8, 8, 9, 10),
        boardOf(4, 5, 5, 6, 6, 6, 7, 7, 7, 8, 8, 8, 9, 9, 10),
    )

    private fun level(ordinal: Long) = PuzzleId(PuzzleMode.ENDLESS, ordinal)

    @Test
    fun `the same level always covers the same cells`() {
        // A star that moved between visits would land under a half-solved word.
        boards.forEach { board ->
            (1L..50L).forEach { ordinal ->
                assertEquals(
                    HiddenLetters.chooseFor(board, level(ordinal)),
                    HiddenLetters.chooseFor(board, level(ordinal)),
                )
            }
        }
    }

    @Test
    fun `different levels cover different cells`() {
        val board = boards.last()
        val drawn = (1L..50L).map { HiddenLetters.chooseFor(board, level(it)) }.toSet()
        assertTrue("fifty levels drew only ${drawn.size} arrangements", drawn.size > 40)
    }

    @Test
    fun `the count runs from half the words, at least three, to one per word`() {
        boards.forEach { board ->
            val words = board.solution.size
            val least = minOf(words, maxOf(HiddenLetters.MINIMUM, (words + 1) / 2))
            val counts = (1L..300L).map { HiddenLetters.chooseFor(board, level(it)).size }.toSet()
            assertEquals("words=$words", (least..words).toSet(), counts)
        }
    }

    @Test
    fun `no first letter, no two stars side by side, no more than one per three letters`() {
        boards.forEach { board ->
            (1L..300L).forEach { ordinal ->
                val stars = HiddenLetters.chooseFor(board, level(ordinal))
                board.solution.forEach { placement ->
                    val positions = placement.cells.indices.filter { placement.cells[it] in stars }
                    assertFalse("first letter of ${placement.cells.size}", 0 in positions)
                    assertTrue(positions.size <= HiddenLetters.capOf(placement.cells.size))
                    positions.zipWithNext().forEach { (a, b) -> assertTrue("adjacent $a, $b", b - a > 1) }
                }
            }
        }
    }

    @Test
    fun `every word has room for a star whatever its length`() {
        (2..12).forEach { assertTrue(HiddenLetters.capOf(it) >= 1) }
        assertEquals(1, HiddenLetters.capOf(5))
        assertEquals(2, HiddenLetters.capOf(6))
        assertEquals(2, HiddenLetters.capOf(8))
        assertEquals(3, HiddenLetters.capOf(9))
    }
}
