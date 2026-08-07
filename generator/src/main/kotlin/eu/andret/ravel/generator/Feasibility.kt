package eu.andret.ravel.generator

/**
 * Prunes dead branches of the tiling search.
 *
 * A partial tiling can only be completed when every connected region of still-free cells can be
 * filled exactly by some sub-multiset of the words that are left. Checking that each region size
 * is a reachable subset sum is cheap and kills the overwhelming majority of branches - without it
 * the search wanders into regions it can never fill and only notices cell by cell.
 *
 * The condition is necessary but not sufficient (it ignores the shape of a region and lets two
 * regions claim the same word), which is fine: it is a prune, not a decision procedure.
 */
internal class FeasibilityCheck(private val grid: Grid) {
    private val visited = BooleanArray(grid.cellCount)
    private val stack = IntArray(grid.cellCount)
    private val reachableSum = BooleanArray(grid.cellCount + 1)

    fun isFeasible(blocked: BooleanArray, remainingCounts: IntArray): Boolean {
        computeReachableSums(remainingCounts)
        visited.fill(false)
        for (start in 0 until grid.cellCount) {
            if (blocked[start] || visited[start]) continue
            if (!reachableSum[regionSize(start, blocked)]) return false
        }
        return true
    }

    private fun regionSize(start: Int, blocked: BooleanArray): Int {
        var top = 0
        stack[top++] = start
        visited[start] = true
        var size = 0
        while (top > 0) {
            val cell = stack[--top]
            size++
            for (neighbour in grid.neighbours[cell]) {
                if (!blocked[neighbour] && !visited[neighbour]) {
                    visited[neighbour] = true
                    stack[top++] = neighbour
                }
            }
        }
        return size
    }

    /** Bounded knapsack over the remaining word lengths. */
    private fun computeReachableSums(remainingCounts: IntArray) {
        reachableSum.fill(false)
        reachableSum[0] = true
        var reached = 0
        for (length in MIN_WORD_LENGTH..MAX_WORD_LENGTH) {
            repeat(remainingCounts[length]) {
                val ceiling = minOf(reached + length, grid.cellCount)
                for (sum in ceiling downTo length) {
                    if (reachableSum[sum - length]) reachableSum[sum] = true
                }
                reached = ceiling
            }
        }
    }
}
