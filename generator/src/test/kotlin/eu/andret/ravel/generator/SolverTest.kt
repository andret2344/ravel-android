package eu.andret.ravel.generator

import kotlin.test.Test
import kotlin.test.assertEquals

class SolverTest {

    @Test
    fun `counts the single tiling of a one word board`() {
        val solver = solverFor(width = 3, height = 1, rows = listOf("kot"), words = listOf("kot"))
        assertEquals(1, solver.countSolutions(listOf(3)))
    }

    @Test
    fun `accepts a path drawn from either end`() {
        val solver = solverFor(width = 3, height = 1, rows = listOf("tok"), words = listOf("kot"))
        assertEquals(1, solver.countSolutions(listOf(3)))
    }

    @Test
    fun `rejects a board whose letters spell nothing`() {
        val solver = solverFor(width = 3, height = 1, rows = listOf("xyz"), words = listOf("kot"))
        assertEquals(0, solver.countSolutions(listOf(3)))
    }

    @Test
    fun `finds a tiling whose path bends around the anchor cell`() {
        // The only tiling covers the top-left cell as an interior cell of an L-shaped path,
        // which is the case the enumerator handles separately from paths that start there.
        val solver = solverFor(
            width = 2,
            height = 3,
            rows = listOf("ok", "ta", "al"),
            words = listOf("kot", "ala"),
        )
        assertEquals(1, solver.countSolutions(listOf(3, 3)))
    }

    @Test
    fun `counts both tilings of an ambiguous board`() {
        // Rows spell kot and oto; so does the L-shaped split k-o-t down the left, o-t-o along the
        // right. A board like this is exactly what generation must reject.
        val solver = solverFor(
            width = 3,
            height = 2,
            rows = listOf("kot", "oto"),
            words = listOf("kot", "oto"),
        )
        assertEquals(2, solver.countSolutions(listOf(3, 3)))
    }

    @Test
    fun `stops counting at the limit`() {
        val solver = solverFor(
            width = 3,
            height = 2,
            rows = listOf("kot", "oto"),
            words = listOf("kot", "oto"),
        )
        assertEquals(1, solver.countSolutions(listOf(3, 3), limit = 1))
    }

    @Test
    fun `treats walls as cells outside every path`() {
        val solver = solverFor(
            width = 4,
            height = 1,
            rows = listOf("kot#"),
            words = listOf("kot"),
        )
        assertEquals(1, solver.countSolutions(listOf(3)))
    }

    private fun solverFor(width: Int, height: Int, rows: List<String>, words: List<String>): Solver {
        require(rows.size == height && rows.all { it.length == width })
        val letters = rows.joinToString("").toCharArray()
        return Solver(Grid(width, height), letters, Dictionary.of(Language.POLISH, words))
    }
}
