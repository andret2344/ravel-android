package eu.andret.ravel.generator

/**
 * Exhaustive tiling search over a lettered grid: counts the ways the free cells split into paths
 * whose lengths match the multiset shown to the player and whose letters spell dictionary words.
 *
 * This is what makes a board a puzzle rather than a word search. A correct word can still be the
 * wrong move when it leaves the rest of the grid untileable, and a board is only publishable when
 * exactly one tiling exists.
 *
 * Two tilings are the same when they group the cells the same way. A path drawn from either end
 * covers the same cells, so a word and its reverse both being valid does not make a second
 * solution - the player has grouped the board identically either way.
 */
class Solver(
    private val grid: Grid,
    private val letters: CharArray,
    dictionary: Dictionary,
) {
    private val blocked = BooleanArray(grid.cellCount)
    private val remainingCounts = IntArray(MAX_WORD_LENGTH + 1)
    private val feasibility = FeasibilityCheck(grid)
    private val trie = dictionary.trie
    private var solutionsFound = 0
    private var budget = SearchBudget(SearchBudget.UNIQUENESS_NODES)

    /**
     * One enumerator per depth. The search recurses from inside the enumeration callback, so an
     * enumerator must not be shared between depths: the nested walk would overwrite the partial
     * path its caller is still standing on.
     */
    private val enumerators = ArrayList<PathEnumerator>()

    /**
     * Number of distinct tilings, giving up once [limit] have been found. Generation calls this
     * with a limit of 2: the only question that matters is whether a second solution exists.
     *
     * Returns [UNKNOWN_SOLUTION_COUNT] when the search exceeded its node budget. That is not a
     * count and must never be read as one - it means the board's solutions were never established,
     * so a board reported this way has to be discarded rather than published.
     */
    fun countSolutions(wordLengths: List<Int>, limit: Int = 2): Int {
        require(wordLengths.sum() == grid.cellCount - letters.count { it == WALL }) {
            "Word lengths must cover every free cell exactly"
        }
        for (cell in 0 until grid.cellCount) blocked[cell] = letters[cell] == WALL
        remainingCounts.fill(0)
        wordLengths.forEach { remainingCounts[it]++ }
        solutionsFound = 0
        budget = SearchBudget(SearchBudget.UNIQUENESS_NODES)
        enumerators.clear()
        search(depth = 0, limit = limit)
        return if (budget.isExhausted) UNKNOWN_SOLUTION_COUNT else solutionsFound
    }

    private fun search(depth: Int, limit: Int) {
        if (budget.isExhausted) return
        val anchor = firstFreeCell()
        if (anchor == Grid.NO_CELL) {
            solutionsFound++
            return
        }
        if (!feasibility.isFeasible(blocked, remainingCounts)) return
        val enumerator = enumeratorAt(depth)
        for (length in MIN_WORD_LENGTH..MAX_WORD_LENGTH) {
            if (remainingCounts[length] == 0) continue
            remainingCounts[length]--
            enumerator.forEachPathThrough(anchor, length) {
                search(depth + 1, limit)
                solutionsFound >= limit
            }
            remainingCounts[length]++
            if (solutionsFound >= limit) return
        }
    }

    private fun enumeratorAt(depth: Int): PathEnumerator {
        while (enumerators.size <= depth) {
            enumerators.add(PathEnumerator(grid, blocked, letters, trie, budget = budget))
        }
        return enumerators[depth]
    }

    companion object {
        const val UNKNOWN_SOLUTION_COUNT = Int.MAX_VALUE
    }

    private fun firstFreeCell(): Int {
        for (cell in 0 until grid.cellCount) {
            if (!blocked[cell]) return cell
        }
        return Grid.NO_CELL
    }
}
