package eu.andret.ravel.generator

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PartitionerTest {

    @Test
    fun `splits a grid into connected paths covering every cell once`() {
        val grid = Grid(5, 5)
        val walls = BooleanArray(grid.cellCount)
        val lengths = listOf(5, 5, 5, 5, 5)

        val paths = assertNotNull(Partitioner(grid, walls, Random(1)).partition(lengths))

        assertEquals(lengths.sorted(), paths.map { it.size }.sorted())
        val owner = IntArray(grid.cellCount) { -1 }
        paths.forEachIndexed { index, path ->
            path.forEachIndexed { position, cell ->
                assertEquals(-1, owner[cell], "cell $cell covered twice")
                owner[cell] = index
                if (position > 0) {
                    assertTrue(cell in grid.neighbours[path[position - 1]], "path $index is not connected")
                }
            }
        }
        assertTrue(owner.none { it == -1 }, "some cell was left uncovered")
    }

    @Test
    fun `respects walls`() {
        val grid = Grid(3, 3)
        val walls = BooleanArray(grid.cellCount).apply { this[4] = true }

        val paths = assertNotNull(Partitioner(grid, walls, Random(2)).partition(listOf(4, 4)))

        assertTrue(paths.none { 4 in it.toList() }, "a path ran through the wall")
    }

    @Test
    fun `gives up instead of proving an impossible layout impossible`() {
        // Five free cells cannot hold two words of three letters. Without a budget the search
        // still terminates here, but on 7x7 boards the same dead end once cost eleven minutes.
        val grid = Grid(5, 1)
        val walls = BooleanArray(grid.cellCount)

        assertNull(Partitioner(grid, walls, Random(3)).partition(listOf(3, 3)))
    }

    @Test
    fun `an exhausted budget reports no layout rather than a partial one`() {
        val grid = Grid(7, 7)
        val walls = BooleanArray(grid.cellCount)
        val budget = SearchBudget(TINY_BUDGET)

        val paths = Partitioner(grid, walls, Random(4), budget).partition(listOf(7, 7, 7, 7, 7, 7, 7))

        assertTrue(budget.isExhausted, "the budget should have run out")
        assertNull(paths, "a partial layout must never be returned as a solution")
    }

    private companion object {
        const val TINY_BUDGET = 20L
    }
}
