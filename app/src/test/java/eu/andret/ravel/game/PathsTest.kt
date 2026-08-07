package eu.andret.ravel.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PathsTest {

    private val placement = intArrayOf(0, 1, 2, 8, 14)

    @Test
    fun `the path as hidden matches`() {
        assertTrue(matchesPlacement(placement, listOf(0, 1, 2, 8, 14)))
    }

    @Test
    fun `the same path drawn backwards does not match`() {
        // Backwards, the letters spell the word backwards, and that is not the word.
        assertFalse(matchesPlacement(placement, listOf(14, 8, 2, 1, 0)))
    }

    @Test
    fun `the same cells in another order do not match`() {
        // A path is a route, not a set: these cells spell something else in this order.
        assertFalse(matchesPlacement(placement, listOf(0, 1, 2, 14, 8)))
    }

    @Test
    fun `a prefix of the path does not match`() {
        assertFalse(matchesPlacement(placement, listOf(0, 1, 2, 8)))
    }

    @Test
    fun `a longer path over the same start does not match`() {
        assertFalse(matchesPlacement(placement, listOf(0, 1, 2, 8, 14, 20)))
    }

    @Test
    fun `an empty trace matches nothing`() {
        assertFalse(matchesPlacement(placement, emptyList()))
    }

    @Test
    fun `a common prefix stops at the first disagreement`() {
        assertEquals(3, commonPrefixLength(listOf(0, 1, 2, 8, 14), listOf(0, 1, 2, 9)))
        assertEquals(0, commonPrefixLength(listOf(0, 1), listOf(5, 1)))
        assertEquals(0, commonPrefixLength(listOf(0, 1), emptyList()))
    }

    @Test
    fun `a common prefix never runs past the shorter list`() {
        assertEquals(2, commonPrefixLength(listOf(0, 1), listOf(0, 1, 2, 3)))
    }

    @Test
    fun `neighbours are the four cells sharing an edge`() {
        // On a board four wide: 5 sits in the middle of the second row.
        assertTrue(areAdjacent(4, 5, 1))
        assertTrue(areAdjacent(4, 5, 4))
        assertTrue(areAdjacent(4, 5, 6))
        assertTrue(areAdjacent(4, 5, 9))
    }

    @Test
    fun `diagonals, jumps and a cell itself are not steps`() {
        assertFalse(areAdjacent(4, 5, 0))
        assertFalse(areAdjacent(4, 5, 7))
        assertFalse(areAdjacent(4, 5, 5))
    }

    @Test
    fun `a row's end does not wrap onto the next row`() {
        // 3 and 4 are consecutive indices but opposite edges of a board four wide.
        assertFalse(areAdjacent(4, 3, 4))
    }
}
