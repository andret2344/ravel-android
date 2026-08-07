package eu.andret.ravel.generator

const val WALL = '#'

/** One hidden word together with the cells its path covers, in reading order. */
class Placement(val word: String, val cells: IntArray)

/**
 * A generated board. [letters] is indexed row-major, holding [WALL] on blocked cells.
 *
 * The player is shown only the letters and the multiset of word lengths; [solution] is the
 * single tiling that satisfies them, verified by [Solver].
 */
class Board(
    val width: Int,
    val height: Int,
    val letters: CharArray,
    val solution: List<Placement>,
) {
    val wordLengths: List<Int> get() = solution.map { it.word.length }.sorted()

    /**
     * How much the words wind, from 0 when every path runs dead straight to 1 when every path
     * changes direction at every opportunity.
     *
     * Counted over interior cells only, since a path's two endpoints have no direction to change.
     * A straight word is easy to spot at a glance; a winding one has to be traced, which is where
     * the puzzle actually lives.
     */
    val turnRatio: Double get() = turnRatioOf(solution.map(Placement::cells))

    /** See [foldRatioOf]: 0 means no word ever runs alongside itself. */
    val foldRatio: Double get() = foldRatioOf(solution.map(Placement::cells), Grid(width, height))

    /**
     * How directly the words travel, from 0 when a path ends where it started to 1 when it runs
     * straight from end to end.
     *
     * Measured as the distance between a path's two endpoints over the steps it took to get there.
     * A path can turn constantly and still travel directly - a staircase does - so this is
     * independent of [turnRatio]: it separates a word that makes progress from one that doubles
     * back on itself.
     */
    val directness: Double
        get() {
            var total = 0.0
            solution.forEach { placement ->
                val cells = placement.cells
                if (cells.size < 2) return@forEach
                val first = cells.first()
                val last = cells.last()
                val distance = Math.abs(first / width - last / width) + Math.abs(first % width - last % width)
                total += distance.toDouble() / (cells.size - 1)
            }
            return if (solution.isEmpty()) 0.0 else total / solution.size
        }

    /** The same three measures taken on the worst single word, which is what the eye notices. */
    val worstTurnRatio: Double get() = worstTurnRatioOf(solution.map(Placement::cells))
    val worstDirectness: Double
        get() = worstDirectnessOf(solution.map(Placement::cells), Grid(width, height))
    val worstFoldRatio: Double
        get() = worstFoldRatioOf(solution.map(Placement::cells), Grid(width, height))

    val wallCount: Int get() = letters.count { it == WALL }

    fun isWall(cell: Int): Boolean = letters[cell] == WALL

    fun render(): String = buildString {
        for (row in 0 until height) {
            for (column in 0 until width) {
                append(letters[row * width + column])
                if (column < width - 1) append(' ')
            }
            appendLine()
        }
    }

    /** Same grid with each cell replaced by the index of the word covering it, walls stay [WALL]. */
    fun renderSolution(): String {
        val owner = CharArray(width * height) { WALL }
        solution.forEachIndexed { index, placement ->
            placement.cells.forEach { cell -> owner[cell] = ('a' + index) }
        }
        return buildString {
            for (row in 0 until height) {
                for (column in 0 until width) {
                    append(owner[row * width + column])
                    if (column < width - 1) append(' ')
                }
                appendLine()
            }
        }
    }
}

/**
 * How much the paths run alongside themselves, counted as pairs of cells in one word that touch on
 * the grid without being consecutive in the path, divided by the cells covered.
 *
 * This is what separates a ribbon from a blob. A staircase scores zero however much it turns; a
 * word filling a 3x3 square scores about 0.44 whichever way it is threaded. Bounding-box fill does
 * not see the difference - a blob and a staircase across the same corners can fill their boxes
 * alike - which is why an earlier version of this generator produced solid clumps while measuring
 * as compact.
 */
internal fun foldRatioOf(paths: List<IntArray>, grid: Grid): Double {
    var folds = 0
    var cells = 0
    paths.forEach { path ->
        cells += path.size
        for (first in path.indices) {
            for (second in first + 2 until path.size) {
                if (path[second] in grid.neighbours[path[first]]) folds++
            }
        }
    }
    return if (cells == 0) 0.0 else folds.toDouble() / cells
}

/**
 * The shape gates are applied to the worst word on a board, not to its average.
 *
 * Averaging hides exactly the defect worth catching. A board of five well-threaded words plus one
 * that fills a 2x2 block averages a fold ratio of 0.03 and looks fine on paper, while the player
 * sees the block. The same goes the other way: one word climbing the grid as a staircase averages
 * away against five that do not.
 *
 * Words shorter than [SHAPED_LENGTH] are exempt. A three-letter path has one interior cell, so its
 * turn ratio is 0 or 1 with nothing in between, and no ratio measured over one or two cells says
 * anything about shape.
 */
const val SHAPED_LENGTH = 5

/**
 * The per-word shape limits, applied while paths are enumerated rather than to a finished board.
 *
 * Checking afterwards is what made large boards slow: a 9x9 board carries eight or nine words, so
 * one badly shaped path threw away the eight good ones with it, and nine tenths of the layouts on
 * a 9x9 were discarded that way. Enforced here, the search backtracks over the one bad path
 * instead of over the whole grid.
 */
internal class PathShape(
    val maxTurnRatio: Double,
    val maxDirectness: Double,
    val maxFoldRatio: Double,
) {
    /** True when a path of [length] cells held in [path] stays inside every limit. */
    fun accepts(path: IntArray, length: Int, grid: Grid): Boolean {
        // Folds are checked at every length. The exemption for short words is about ratios that
        // mean nothing over one or two interior cells, and a four-cell word doubling back is not
        // that - it is a 2x2 block, the most visible clump on the board.
        var folds = 0
        for (index in 0 until length) {
            for (other in index + 2 until length) {
                if (path[other] in grid.neighbours[path[index]]) folds++
            }
        }
        if (folds > maxFoldRatio * length + TOLERANCE) return false
        if (length < SHAPED_LENGTH) return true

        var turns = 0
        for (index in 1 until length - 1) {
            if (path[index] - path[index - 1] != path[index + 1] - path[index]) turns++
        }
        if (turns > maxTurnRatio * (length - 2) + TOLERANCE) return false

        val first = path[0]
        val last = path[length - 1]
        val distance = Math.abs(first / grid.width - last / grid.width) +
            Math.abs(first % grid.width - last % grid.width)
        return distance <= maxDirectness * (length - 1) + TOLERANCE
    }

    private companion object {
        /** Ratios are compared against integer counts, so a limit of 0.2 must admit exactly 1 in 5. */
        const val TOLERANCE = 1e-9
    }
}

/** The largest share of folds any single word carries, see [foldRatioOf]. */
internal fun worstFoldRatioOf(paths: List<IntArray>, grid: Grid): Double =
    paths.maxOfOrNull { foldRatioOf(listOf(it), grid) } ?: 0.0

/** The largest share of turns any single word of at least [SHAPED_LENGTH] cells carries. */
internal fun worstTurnRatioOf(paths: List<IntArray>): Double =
    paths.filter { it.size >= SHAPED_LENGTH }.maxOfOrNull { turnRatioOf(listOf(it)) } ?: 0.0

/**
 * The straightest travel any single word of at least [SHAPED_LENGTH] cells makes, measured as in
 * [Board.directness].
 *
 * Capped rather than maximised, which is the correction to an earlier version of this generator: a
 * path that travels perfectly directly while turning constantly is a staircase, and a staircase is
 * as dull to trace as a straight line. The reference board measures 0.60 on every word.
 */
internal fun worstDirectnessOf(paths: List<IntArray>, grid: Grid): Double =
    paths.filter { it.size >= SHAPED_LENGTH }.maxOfOrNull { cells ->
        val first = cells.first()
        val last = cells.last()
        val distance = Math.abs(first / grid.width - last / grid.width) +
            Math.abs(first % grid.width - last % grid.width)
        distance.toDouble() / (cells.size - 1)
    } ?: 0.0

/**
 * How much a set of paths winds, from 0 when every path runs dead straight to 1 when every path
 * changes direction at every opportunity.
 *
 * Counted over interior cells only, since a path's two endpoints have no direction to change.
 * Cells are row-major indices, so a step is a constant delta and a turn is simply two consecutive
 * steps that differ.
 */
internal fun turnRatioOf(paths: List<IntArray>): Double {
    var turns = 0
    var interior = 0
    paths.forEach { cells ->
        for (index in 1 until cells.size - 1) {
            if (cells[index] - cells[index - 1] != cells[index + 1] - cells[index]) turns++
            interior++
        }
    }
    return if (interior == 0) 0.0 else turns.toDouble() / interior
}

/**
 * The symmetry a board's walls obey.
 *
 * Scattered walls read as damage; the same number of walls placed symmetrically reads as a design.
 * Crosswords have used the rotational case for a century for exactly this reason.
 *
 * Every wall is placed together with its whole orbit under the chosen symmetry, so the pattern
 * holds by construction rather than by inspection afterwards.
 */
enum class WallSymmetry {
    /** Half-turn about the centre: what newspaper crosswords use. */
    ROTATIONAL,

    /** Mirrored left to right. */
    VERTICAL_AXIS,

    /** Mirrored top to bottom. */
    HORIZONTAL_AXIS,

    /** Mirrored both ways at once, so walls come in fours. */
    BOTH_AXES,
    ;

    /** Every cell that must be a wall if [cell] is, this one included. */
    fun orbitOf(cell: Int, grid: Grid): IntArray {
        val row = cell / grid.width
        val column = cell % grid.width
        val mirroredRow = grid.height - 1 - row
        val mirroredColumn = grid.width - 1 - column
        val orbit = when (this) {
            ROTATIONAL -> intArrayOf(cell, mirroredRow * grid.width + mirroredColumn)
            VERTICAL_AXIS -> intArrayOf(cell, row * grid.width + mirroredColumn)
            HORIZONTAL_AXIS -> intArrayOf(cell, mirroredRow * grid.width + column)
            BOTH_AXES -> intArrayOf(
                cell,
                row * grid.width + mirroredColumn,
                mirroredRow * grid.width + column,
                mirroredRow * grid.width + mirroredColumn,
            )
        }
        return orbit.distinct().toIntArray()
    }
}

/** Row-major neighbour indices for every cell of a [width] x [height] grid. */
class Grid(val width: Int, val height: Int) {
    val cellCount: Int = width * height
    val neighbours: Array<IntArray> = Array(cellCount) { cell ->
        val row = cell / width
        val column = cell % width
        buildList(4) {
            if (row > 0) add(cell - width)
            if (column > 0) add(cell - 1)
            if (column < width - 1) add(cell + 1)
            if (row < height - 1) add(cell + width)
        }.toIntArray()
    }

    fun rightOf(cell: Int): Int = if (cell % width < width - 1) cell + 1 else NO_CELL

    fun below(cell: Int): Int = if (cell / width < height - 1) cell + width else NO_CELL

    companion object {
        const val NO_CELL = -1
    }
}
