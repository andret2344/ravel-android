package eu.andret.ravel.generator

import kotlin.random.Random

/**
 * Enumerates simple orthogonal paths of a given length that pass through an anchor cell.
 *
 * The anchor is always the first free cell in row-major order, which collapses the enumeration
 * into two cases: everything above and to the left of the anchor is already blocked, so the
 * anchor's only free neighbours are the one to its right and the one below it. Therefore the
 * anchor is either an endpoint of its path, or an interior cell whose two path neighbours are
 * exactly those two. Anchoring this way also means each set of cells is produced exactly once -
 * a path and its reverse are never both enumerated - so callers can count tilings without
 * deduplicating.
 *
 * When [trie] and [letters] are supplied, only paths spelling a dictionary word are reported.
 * The trie holds every word and its reverse, so a single forward walk prunes both directions.
 *
 * When a [shape] is supplied, only paths inside its limits are reported. The solver deliberately
 * passes none: a board is unique when its cell partition is, whatever shape the rival tilings take.
 *
 * @param blocked shared with the caller: cells of the path being built are marked blocked while
 *   `visit` runs, so a recursing caller sees the board state it expects.
 */
internal class PathEnumerator(
    private val grid: Grid,
    private val blocked: BooleanArray,
    private val letters: CharArray? = null,
    private val trie: Trie? = null,
    private val random: Random? = null,
    private val budget: SearchBudget? = null,
    private val turnBias: Double = 0.0,
    private val directnessBias: Double = 0.0,
    private val foldAvoidance: Double = 0.0,
    private val shape: PathShape? = null,
) {
    private val path = IntArray(MAX_WORD_LENGTH)
    private val trieNodes = IntArray(MAX_WORD_LENGTH)
    private var prefixPruning = false

    /**
     * Calls [visit] for every path of [length] cells through [anchor]. The array handed to [visit]
     * is reused, so it must be copied to be kept. Returning true from [visit] stops the
     * enumeration; the return value says whether it did.
     *
     * An exhausted budget also reports a stop, so a caller that supplies one must check
     * [SearchBudget.isExhausted] before reading anything into that stop.
     */
    fun forEachPathThrough(anchor: Int, length: Int, visit: (IntArray) -> Boolean): Boolean {
        if (anchorIsEndpoint(anchor, length, visit)) return true
        return anchorIsCorner(anchor, length, visit)
    }

    private fun anchorIsEndpoint(anchor: Int, length: Int, visit: (IntArray) -> Boolean): Boolean {
        prefixPruning = trie != null
        return extendForward(0, anchor, length, visit)
    }

    private fun anchorIsCorner(anchor: Int, length: Int, visit: (IntArray) -> Boolean): Boolean {
        if (length < 3) return false
        val right = grid.rightOf(anchor)
        val below = grid.below(anchor)
        if (right == Grid.NO_CELL || below == Grid.NO_CELL) return false
        if (blocked[right] || blocked[below]) return false

        // The arm towards `right` is built back to front, so its letters do not form a prefix and
        // cannot be pruned; the word check happens once the whole path is known.
        prefixPruning = false
        blocked[anchor] = true
        var stopped = false
        for (armLength in 1..length - 2) {
            path[armLength] = anchor
            if (extendBackward(armLength - 1, right, length, armLength, below, visit)) {
                stopped = true
                break
            }
        }
        blocked[anchor] = false
        return stopped
    }

    /** Fills [index] and every following position, walking outwards from [cell]. */
    private fun extendForward(index: Int, cell: Int, length: Int, visit: (IntArray) -> Boolean): Boolean {
        if (budget != null && !budget.consume()) return true
        if (prefixPruning) {
            val parent = if (index == 0) Trie.ROOT else trieNodes[index - 1]
            val node = trie!!.step(parent, letters!![cell])
            if (node == Trie.EMPTY) return false
            trieNodes[index] = node
        }
        path[index] = cell
        blocked[cell] = true
        var stopped = false
        if (index == length - 1) {
            stopped = spellsWord(length) &&
                (shape == null || shape.accepts(path, length, grid)) &&
                visit(path)
        } else {
            val previousCell = if (index > 0) path[index - 1] else Grid.NO_CELL
            val startCell = if (index > 0) path[0] else Grid.NO_CELL
            for (neighbour in neighboursOf(cell, previousCell, startCell, index)) {
                if (blocked[neighbour]) continue
                if (extendForward(index + 1, neighbour, length, visit)) {
                    stopped = true
                    break
                }
            }
        }
        blocked[cell] = false
        return stopped
    }

    /** Fills [index] and every preceding position, walking outwards from [cell]. */
    private fun extendBackward(
        index: Int,
        cell: Int,
        length: Int,
        anchorIndex: Int,
        below: Int,
        visit: (IntArray) -> Boolean,
    ): Boolean {
        if (budget != null && !budget.consume()) return true
        path[index] = cell
        blocked[cell] = true
        var stopped = false
        if (index == 0) {
            stopped = extendForward(anchorIndex + 1, below, length, visit)
        } else {
            // Walking backwards, the step already taken is the one from the next position, and
            // the path's start is not known yet - only the turn preference applies here.
            val previousCell = path[index + 1]
            for (neighbour in neighboursOf(cell, previousCell, Grid.NO_CELL, placedUpTo = -1)) {
                if (blocked[neighbour]) continue
                if (extendBackward(index - 1, neighbour, length, anchorIndex, below, visit)) {
                    stopped = true
                    break
                }
            }
        }
        blocked[cell] = false
        return stopped
    }

    /**
     * True when the completed path spells a dictionary word in either direction. With prefix
     * pruning the answer is already in the trie node; otherwise the path is walked from the root.
     */
    private fun spellsWord(length: Int): Boolean {
        val trie = this.trie ?: return true
        if (prefixPruning) return trie.isWord(trieNodes[length - 1])
        var node = Trie.ROOT
        for (index in 0 until length) {
            node = trie.step(node, letters!![path[index]])
            if (node == Trie.EMPTY) return false
        }
        return trie.isWord(node)
    }

    /**
     * Candidate next cells, shuffled when this enumerator is generating rather than solving.
     *
     * With a [turnBias], cells that continue in the same direction as the step into [cell] are
     * moved to the back that often, so the search meets the winding continuations first. This is a
     * bias, not a constraint: straight continuations stay reachable through backtracking, which
     * matters because some layouts have no winding solution at all.
     *
     * With a [directnessBias], cells further from where the path started are preferred, which is
     * a different thing entirely: a staircase turns at every step yet always makes progress, while
     * a loop turns just as often and comes back to where it began. Turning alone does not tell the
     * two apart, so without this the generator produced words that coil around themselves.
     *
     * Biasing here rather than filtering afterwards is the difference between finding a twisty
     * layout and rejecting straight ones until a twisty one turns up by chance - at a floor of
     * 0.85 the latter costs over a second per board.
     */
    private fun neighboursOf(cell: Int, previousCell: Int, startCell: Int, placedUpTo: Int): IntArray {
        val neighbours = grid.neighbours[cell]
        val random = this.random ?: return neighbours
        var ordered = neighbours.copyOf()
        for (index in ordered.size - 1 downTo 1) {
            val target = random.nextInt(index + 1)
            val swap = ordered[index]
            ordered[index] = ordered[target]
            ordered[target] = swap
        }
        // Distance first, turns second: sorting is stable, so the turn pass keeps the distance
        // order among equals and turning still wins outright.
        if (startCell != Grid.NO_CELL && directnessBias > 0.0 && random.nextDouble() < directnessBias) {
            ordered = ordered.sortedByDescending { distanceBetween(startCell, it) }.toIntArray()
        }
        if (previousCell != Grid.NO_CELL && turnBias > 0.0 && random.nextDouble() < turnBias) {
            val straightAhead = cell + (cell - previousCell)
            val turning = IntArray(ordered.size)
            var head = 0
            var tail = ordered.size
            ordered.forEach { neighbour ->
                if (neighbour == straightAhead) turning[--tail] = neighbour else turning[head++] = neighbour
            }
            ordered = turning
        }
        // Applied last so it outranks the other two: a fold is a visible defect, while a straight
        // step or a step back towards the start is only a missed preference.
        if (placedUpTo > 0 && foldAvoidance > 0.0 && random.nextDouble() < foldAvoidance) {
            ordered = ordered.sortedBy { foldsCreatedBy(it, placedUpTo) }.toIntArray()
        }
        return ordered
    }

    /**
     * How many cells already on this path [candidate] would touch, not counting the cell it is
     * being reached from. Anything above zero means the path doubles back against itself, which is
     * what turns a word into a solid clump instead of a ribbon.
     */
    private fun foldsCreatedBy(candidate: Int, placedUpTo: Int): Int {
        var folds = 0
        for (position in 0 until placedUpTo) {
            if (candidate in grid.neighbours[path[position]]) folds++
        }
        return folds
    }

    private fun distanceBetween(from: Int, to: Int): Int =
        Math.abs(from / grid.width - to / grid.width) + Math.abs(from % grid.width - to % grid.width)
}
