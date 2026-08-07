package eu.andret.ravel.generator

import kotlin.random.Random

/**
 * Splits the free cells of a grid into disjoint paths of the requested lengths, ignoring letters.
 *
 * This is the board's solution, built before any word is chosen. Going the other way round -
 * picking words first and then trying to lay them out - means backtracking over a dictionary
 * instead of over a handful of shapes, and does not scale.
 *
 * Uses the same anchor rule as [Solver], so the layout it produces is one the solver will find.
 */
internal class Partitioner(
    private val grid: Grid,
    private val walls: BooleanArray,
    private val random: Random,
    private val budget: SearchBudget = SearchBudget(SearchBudget.LAYOUT_NODES),
    private val turnBias: Double = 0.0,
    private val directnessBias: Double = 0.0,
    private val foldAvoidance: Double = 0.0,
    private val shape: PathShape? = null,
) {
    private val blocked = BooleanArray(grid.cellCount)
    private val remainingCounts = IntArray(MAX_WORD_LENGTH + 1)
    private val feasibility = FeasibilityCheck(grid)
    private val placed = ArrayList<IntArray>()

    /** One enumerator per depth, for the same reason as in [Solver]: the search recurses inside the callback. */
    private val enumerators = ArrayList<PathEnumerator>()

    /**
     * Paths covering every free cell exactly once, or null when no such split exists - or when
     * the search ran out of budget before deciding, which for the caller amounts to the same
     * thing: try another wall layout.
     */
    fun partition(wordLengths: List<Int>): List<IntArray>? {
        walls.copyInto(blocked)
        remainingCounts.fill(0)
        wordLengths.forEach { remainingCounts[it]++ }
        placed.clear()
        val completed = search(depth = 0)
        return if (completed && !budget.isExhausted) ArrayList(placed) else null
    }

    private fun search(depth: Int): Boolean {
        if (budget.isExhausted) return true
        val anchor = firstFreeCell()
        if (anchor == Grid.NO_CELL) return true
        if (!feasibility.isFeasible(blocked, remainingCounts)) return false
        val enumerator = enumeratorAt(depth)
        for (length in shuffledRemainingLengths()) {
            remainingCounts[length]--
            val solved = enumerator.forEachPathThrough(anchor, length) { path ->
                placed.add(path.copyOf(length))
                // removeAt, not removeLast: on a JDK 21 classpath the latter binds to
                // java.util.List.removeLast, which does not exist on Android below API 35.
                if (search(depth + 1)) true else { placed.removeAt(placed.lastIndex); false }
            }
            remainingCounts[length]++
            if (solved) return true
        }
        return false
    }

    private fun enumeratorAt(depth: Int): PathEnumerator {
        while (enumerators.size <= depth) {
            enumerators.add(
                PathEnumerator(
                    grid, blocked,
                    random = random,
                    budget = budget,
                    turnBias = turnBias,
                    directnessBias = directnessBias,
                    foldAvoidance = foldAvoidance,
                    shape = shape,
                )
            )
        }
        return enumerators[depth]
    }

    private fun shuffledRemainingLengths(): List<Int> =
        (MIN_WORD_LENGTH..MAX_WORD_LENGTH).filter { remainingCounts[it] > 0 }.shuffled(random)

    private fun firstFreeCell(): Int {
        for (cell in 0 until grid.cellCount) {
            if (!blocked[cell]) return cell
        }
        return Grid.NO_CELL
    }
}
