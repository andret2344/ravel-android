package eu.andret.ravel.generator

import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * @param minWallRatio lower bound on the share of cells left blocked. Walls are not decoration:
 *   they cut the number of alternative tilings, so a board with none is far harder to make unique.
 *   They are kept few and always symmetric, because scattered walls read as damage.
 * @param maxWallRatio upper bound on the same share.
 * @param minTurnRatio how much the words must wind, see [turnRatioOf]. Layouts below this are
 *   discarded and redrawn.
 * @param maxTurnRatio how much any one of them may wind. A path that bends at every cell is a
 *   staircase, and a board of staircases reads as regular as a board of straight lines, so winding
 *   is a band and not a maximum. Applied per word, unlike the floor - see [SHAPED_LENGTH].
 * @param maxDirectness how straight any one word's travel may be, applied per word.
 * @param turnBias how strongly the layout search prefers winding continuations. Small on purpose:
 *   unbiased layouts already land inside the band, and a strong bias makes every board alike.
 * @param directnessBias how strongly it prefers continuations that lead away from where the path
 *   started, which is what separates a staircase from a coil.
 * @param maxFoldRatio ceiling on how much words may run alongside themselves, see [foldRatioOf].
 * @param foldAvoidance how strongly the search avoids creating folds in the first place.
 * @param targetWordLength overrides the length the generator aims at; normally left to scale with
 *   the grid.
 */
data class BoardSpec(
    val width: Int,
    val height: Int,
    val minWallRatio: Double = 0.08,
    val maxWallRatio: Double = 0.18,
    val minTurnRatio: Double = 0.0,
    val maxTurnRatio: Double = 1.0,
    val maxDirectness: Double = 1.0,
    val turnBias: Double = 0.0,
    val directnessBias: Double = 0.0,
    val maxFoldRatio: Double = 1.0,
    val foldAvoidance: Double = 0.0,
    val targetWordLength: Double? = null,
) {
    val cellCount: Int get() = width * height

    /**
     * How many words to hide on a board of [freeCount] cells.
     *
     * Computed from word length rather than asked for: the generator aims at words averaging
     * [TARGET_WORD_LENGTH] letters, which makes three or four words on a small grid and twenty on
     * a large one without either number being written down. Naming a count instead does the
     * opposite - it forces three-letter words onto a big grid, or twelve-letter ones onto a small
     * grid, and on extreme shapes leaves nothing that fits at all.
     */
    internal fun wordCountFor(freeCount: Int, random: Random): Int {
        val fewest = ceilDiv(freeCount, MAX_WORD_LENGTH)
        val most = freeCount / MIN_WORD_LENGTH
        val targeted = Math.round(freeCount / effectiveTargetWordLength).toInt()
        // The spread only goes down. Boards vary either way in practice, because [fewest] pushes
        // back on large grids, and an extra word is worse than one fewer: it is paid for out of
        // every other word's length, which is how a 6x6 ends up with three four-letter words.
        val spread = maxOf(1, targeted / 8)
        return (targeted - random.nextInt(spread + 1)).coerceIn(fewest, most)
    }

    /**
     * The length at least one word must reach: half the sum of the sides, rounded up.
     *
     * A board whose longest word still fits inside a quarter of it has no path that crosses
     * anything, and crossing the board is what makes a word findable by tracing rather than by
     * scanning a corner. On the reference board every word is exactly this long.
     */
    internal val requiredLongestWord: Int
        get() = ceilDiv(width + height, 2).coerceIn(MIN_WORD_LENGTH, MAX_WORD_LENGTH)

    /**
     * The word length aimed at, scaled to the grid unless overridden.
     *
     * A fixed length cannot serve both sizes: 5.5 letters suits a 6x6 board and turns a 9x9 into
     * seventeen four-letter words, which is neither hard nor good-looking. What has to stay
     * constant is the ratio of word length to board size - that is what decides whether a path has
     * anywhere to go before it must double back on itself.
     */
    internal val effectiveTargetWordLength: Double
        get() = targetWordLength ?: (WORD_LENGTH_PER_SIDE * (width + height) / 2.0)
            .coerceIn(MIN_WORD_LENGTH + 1.0, MAX_WORD_LENGTH - 1.0)

    /**
     * Wall counts to draw from: the preferred ratio band, narrowed to counts that leave enough
     * cells for at least one word.
     *
     * With the word count computed per board there is no shape the band cannot serve, because the
     * count adapts to whatever the walls leave behind.
     */
    internal fun wallCounts(): List<Int> {
        val lowest = (minWallRatio * cellCount).roundToInt()
        val highest = (maxWallRatio * cellCount).roundToInt()
        return (lowest..highest).filter { cellCount - it >= MIN_WORD_LENGTH }
    }

    /** @throws IllegalArgumentException when the grid is too small to hold a single word. */
    fun validate() {
        require(cellCount >= MIN_WORD_LENGTH) {
            "A ${width}x$height grid has $cellCount cells, too few for even one " +
                "$MIN_WORD_LENGTH-letter word."
        }
    }

    companion object {
        /**
         * Target word length per unit of average grid side.
         *
         * One letter per unit of average side, which is what the reference board does: a 6x6 grid
         * holding five six-letter words. It gives 5 letters on 5x5, 7 on 7x7 and 9 on 9x9, so a
         * word always has room to cross the grid and never has room to coil in a corner.
         *
         * This also makes [requiredLongestWord] the ordinary case rather than a constraint the
         * generator has to fight for.
         */
        const val WORD_LENGTH_PER_SIDE = 1.0

        private fun ceilDiv(value: Int, divisor: Int) = (value + divisor - 1) / divisor

        fun forDifficulty(width: Int, height: Int, difficulty: Difficulty) = BoardSpec(
            width = width,
            height = height,
            minTurnRatio = difficulty.minTurnRatio,
            maxTurnRatio = difficulty.maxTurnRatio,
            maxDirectness = difficulty.maxDirectness,
            turnBias = difficulty.turnBias,
            directnessBias = difficulty.directnessBias,
            maxFoldRatio = difficulty.maxFoldRatio,
            foldAvoidance = difficulty.foldAvoidance,
        )
    }
}

/**
 * When to stop trying. Both limits are off by default, because a caller normally wants a board
 * rather than a fast answer, and every attempt is an independent restart that eventually succeeds.
 *
 * @param maxLayouts hard cap on layout attempts.
 * @param timeoutMillis wall-clock budget; null means none.
 */
class GenerationLimits(
    val maxLayouts: Int = Int.MAX_VALUE,
    val timeoutMillis: Long? = null,
) {
    internal fun deadlineFrom(startedAtNanos: Long): Long =
        timeoutMillis?.let { startedAtNanos + it * 1_000_000 } ?: Long.MAX_VALUE
}

class GenerationStats(
    var layouts: Int = 0,
    var wordAssignments: Int = 0,
    var uniquenessChecks: Int = 0,
    /** Layouts thrown away for running too straight, before any word was tried on them. */
    var layoutsTooStraight: Int = 0,
    /**
     * Wall patterns no acceptably shaped split could cover, whether because every candidate path
     * staircased, clumped or ran too directly, or because the shape simply does not tile.
     */
    var layoutsUnshapeable: Int = 0,
    var elapsedMillis: Long = 0,
    /** Time spent splitting grids into paths, before any word is chosen. */
    var layoutMillis: Long = 0,
    /** Time spent proving boards have exactly one solution. */
    var uniquenessMillis: Long = 0,
)

/**
 * Builds boards backwards from a finished solution: carve walls, split the free cells into paths,
 * drop words onto those paths, then keep only the boards a full search proves to have exactly one
 * solution.
 *
 * The last step is the expensive one and it is the only one that can reject, so the loop is
 * arranged to re-letter a layout many times before paying to lay out a new one.
 *
 * Every attempt is an independent random restart, so the loop keeps going until it succeeds rather
 * than giving up after a fixed count. A caller that cannot wait forever passes a [GenerationLimits]
 * deadline; the default is to take as long as the shape needs.
 */
class BoardGenerator(
    private val dictionary: Dictionary,
    private val random: Random = Random.Default,
) {
    /**
     * A board matching [spec], or null only when [limits] ran out first. With the default limits
     * this does not return until it has a board.
     *
     * Interrupting the calling thread calls the search off: every running search abandons itself
     * through [SearchBudget], and this throws [InterruptedException] rather than returning, since
     * "no board" and "you told me to stop" are different answers and only one of them means the
     * caller should try a smaller grid. The interrupt flag is cleared on the way out, as a thread
     * that has had its interruption reported as an exception is no longer interrupted.
     */
    @Throws(InterruptedException::class)
    fun generate(
        spec: BoardSpec,
        stats: GenerationStats = GenerationStats(),
        limits: GenerationLimits = GenerationLimits(),
    ): Board? {
        val startedAt = System.nanoTime()
        spec.validate()
        val grid = Grid(spec.width, spec.height)
        val wallCounts = spec.wallCounts()
        val letters = CharArray(grid.cellCount)
        val solver = Solver(grid, letters, dictionary)
        val deadline = limits.deadlineFrom(startedAt)

        try {
            while (stats.layouts < limits.maxLayouts && System.nanoTime() < deadline) {
                // Checked once per layout attempt, which is enough: an interrupted thread makes
                // every search inside the attempt give up on its first node, so the attempt in
                // progress ends in microseconds rather than seconds.
                if (Thread.interrupted()) {
                    throw InterruptedException("Board generation was interrupted")
                }
                stats.layouts++
                val walls = symmetricWalls(grid, wallCounts.random(random)) ?: continue
                val freeCount = grid.cellCount - walls.count { it }
                val wordCount = spec.wordCountFor(freeCount, random)
                val wordLengths =
                    randomComposition(wordCount, freeCount, spec.requiredLongestWord) ?: continue
                val layoutStartedAt = System.nanoTime()
                val paths = Partitioner(
                    grid, walls, random,
                    turnBias = spec.turnBias,
                    directnessBias = spec.directnessBias,
                    foldAvoidance = spec.foldAvoidance,
                    shape = shapeFor(spec, stats),
                ).partition(wordLengths.toList())
                stats.layoutMillis += (System.nanoTime() - layoutStartedAt) / 1_000_000
                if (paths == null) {
                    stats.layoutsUnshapeable++
                    continue
                }
                // The ceilings are enforced per path during the split; only the board-wide floor
                // is left to check, since no single path can be responsible for meeting it.
                if (turnRatioOf(paths) < relaxedTurnFloor(spec, stats)) {
                    stats.layoutsTooStraight++
                    continue
                }

                val board = letterUntilUnique(spec, paths, walls, letters, solver, stats)
                if (board != null) return board
            }
            return null
        } finally {
            stats.elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000
        }
    }

    /**
     * The turn floor actually applied, eased once a board has been rejected for straightness many
     * times over.
     *
     * Some grids cannot meet the floor at all: a board one cell wide has nowhere to turn, so its
     * best possible turn ratio is zero. Holding the floor there would spin forever, and this
     * generator promises a board. Easing costs nothing on ordinary grids, which clear the floor
     * within a few dozen layouts and never reach the first step.
     */
    private fun relaxedTurnFloor(spec: BoardSpec, stats: GenerationStats): Double {
        val steps = stats.layoutsTooStraight / LAYOUTS_BEFORE_EASING
        return (spec.minTurnRatio - steps * TURN_EASING_STEP).coerceAtLeast(0.0)
    }

    /**
     * The per-path limits currently in force, eased for the same reason as [relaxedTurnFloor]:
     * some grids admit no split that meets them, and this generator promises a board.
     *
     * All three ease together, driven by one counter. They constrain the same paths, so a grid
     * that cannot satisfy one usually cannot satisfy the others either, and easing them apart
     * would let the search settle on whichever happened to be loosest.
     */
    private fun shapeFor(spec: BoardSpec, stats: GenerationStats): PathShape {
        val easing = (stats.layoutsUnshapeable / LAYOUTS_BEFORE_EASING) * CEILING_EASING_STEP
        return PathShape(
            maxTurnRatio = (spec.maxTurnRatio + easing).coerceAtMost(1.0),
            maxDirectness = (spec.maxDirectness + easing).coerceAtMost(1.0),
            maxFoldRatio = (spec.maxFoldRatio + easing).coerceAtMost(1.0),
        )
    }

    private fun letterUntilUnique(
        spec: BoardSpec,
        paths: List<IntArray>,
        walls: BooleanArray,
        letters: CharArray,
        solver: Solver,
        stats: GenerationStats,
    ): Board? {
        repeat(LETTERING_ATTEMPTS) {
            val words = drawWords(paths) ?: return null
            stats.wordAssignments++
            writeLetters(letters, walls, paths, words)
            stats.uniquenessChecks++
            val uniquenessStartedAt = System.nanoTime()
            val solutions = solver.countSolutions(words.map(String::length), limit = 2)
            stats.uniquenessMillis += (System.nanoTime() - uniquenessStartedAt) / 1_000_000
            if (solutions == 1) {
                val solution = paths.mapIndexed { index, cells -> Placement(words[index], cells) }
                return Board(spec.width, spec.height, letters.copyOf(), solution)
            }
        }
        return null
    }

    /**
     * Places walls in a symmetric pattern, aiming at [wallCount] of them.
     *
     * Walls go in whole symmetry orbits, so the pattern is symmetric by construction. Growing the
     * *free* region instead - the previous approach - guaranteed connectivity but produced ragged
     * blobs where a third of a row could vanish, which reads as damage rather than design. With
     * walls this sparse, connectivity is common enough to get by redrawing.
     *
     * The unit placed is a domino rather than a single cell, because that is what the reference
     * board uses: one horizontal pair on the mirror axis and a mirrored vertical pair either side.
     * Single cells at this density read as speckle; a pair reads as a deliberate block.
     *
     * The achieved count rarely equals [wallCount] exactly, since an orbit is two, four or eight
     * cells at a time. The caller reads the count off the result rather than assuming.
     */
    private fun symmetricWalls(grid: Grid, wallCount: Int): BooleanArray? {
        val walls = BooleanArray(grid.cellCount)
        if (wallCount > 0) {
            val symmetry = WallSymmetry.entries.random(random)
            var placed = 0
            var attempts = 0
            while (placed < wallCount && attempts < grid.cellCount * ORBIT_ATTEMPTS_PER_CELL) {
                attempts++
                val orbit = dominoOrbit(grid, symmetry) ?: continue
                if (orbit.any { walls[it] }) continue
                if (placed + orbit.size > wallCount) continue
                orbit.forEach { walls[it] = true }
                placed += orbit.size
            }
            if (placed == 0) return null
        }
        if (grid.cellCount - walls.count { it } < MIN_WORD_LENGTH) return null
        if (!spansEveryRowAndColumn(grid, walls)) return null
        if (longestWallRun(grid, walls) > maxWallRun(grid)) return null
        return if (freeRegionIsConnected(grid, walls)) walls else null
    }

    /**
     * A random pair of adjacent cells together with everything [symmetry] forces to join them, or
     * null when the pair would run off the grid.
     *
     * A pair lying on the mirror axis collapses onto itself, which is how the reference board's
     * central block arises, so orbits come out two, four or eight cells wide.
     */
    private fun dominoOrbit(grid: Grid, symmetry: WallSymmetry): IntArray? {
        val head = random.nextInt(grid.cellCount)
        val tail = if (random.nextBoolean()) grid.rightOf(head) else grid.below(head)
        if (tail == Grid.NO_CELL) return null
        return (symmetry.orbitOf(head, grid) + symmetry.orbitOf(tail, grid)).distinct().toIntArray()
    }

    /**
     * How long a run of walls may lie along one row or column.
     *
     * Symmetry alone does not save a pattern: two dominoes landing side by side blank four of a
     * row's six cells, and mirrored or not, that reads as a missing row rather than as a block.
     * The reference board's longest run is two.
     */
    private fun maxWallRun(grid: Grid): Int = maxOf(2, minOf(grid.width, grid.height) / 3)

    /** The longest unbroken line of walls along any row or column. */
    private fun longestWallRun(grid: Grid, walls: BooleanArray): Int {
        var longest = 0
        for (row in 0 until grid.height) {
            var run = 0
            for (column in 0 until grid.width) {
                run = if (walls[row * grid.width + column]) run + 1 else 0
                if (run > longest) longest = run
            }
        }
        for (column in 0 until grid.width) {
            var run = 0
            for (row in 0 until grid.height) {
                run = if (walls[row * grid.width + column]) run + 1 else 0
                if (run > longest) longest = run
            }
        }
        return longest
    }

    /**
     * Rejects wall patterns that leave a whole row or column dead. A board with an entirely blank
     * last column does not read as a 9x9 board - it reads as a 9x8 board with a stripe of
     * decoration.
     */
    private fun spansEveryRowAndColumn(grid: Grid, walls: BooleanArray): Boolean {
        val rowUsed = BooleanArray(grid.height)
        val columnUsed = BooleanArray(grid.width)
        for (cell in 0 until grid.cellCount) {
            if (walls[cell]) continue
            rowUsed[cell / grid.width] = true
            columnUsed[cell % grid.width] = true
        }
        return rowUsed.all { it } && columnUsed.all { it }
    }

    /**
     * Words on separate islands would still be solvable, but a board that reads as one region is
     * what a player expects.
     */
    private fun freeRegionIsConnected(grid: Grid, walls: BooleanArray): Boolean {
        val start = (0 until grid.cellCount).firstOrNull { !walls[it] } ?: return false
        val visited = BooleanArray(grid.cellCount)
        val stack = ArrayDeque<Int>()
        stack.addLast(start)
        visited[start] = true
        var reached = 0
        while (stack.isNotEmpty()) {
            val cell = stack.removeLast()
            reached++
            for (neighbour in grid.neighbours[cell]) {
                if (!walls[neighbour] && !visited[neighbour]) {
                    visited[neighbour] = true
                    stack.addLast(neighbour)
                }
            }
        }
        return reached == grid.cellCount - walls.count { it }
    }

    /**
     * Word lengths in `$MIN_WORD_LENGTH..$MAX_WORD_LENGTH` summing to [total], at least one of them
     * reaching [longest].
     *
     * Lengths come out near-equal rather than random, which is both what the reference board does
     * and the only way the long word survives: handing out [total] letters at random leaves a
     * couple of three-letter stubs beside it, and short words are the ones that end up as squat
     * little blocks in a corner. The wobble afterwards keeps consecutive boards from having
     * identical length lists.
     */
    private fun randomComposition(parts: Int, total: Int, longest: Int): IntArray? {
        if (parts < 1) return null
        if (total < MIN_WORD_LENGTH * parts) return null
        if (total > MAX_WORD_LENGTH * parts) return null

        val lengths = IntArray(parts) { total / parts }
        repeat(total % parts) { lengths[it]++ }
        if (lengths.any { it !in MIN_WORD_LENGTH..MAX_WORD_LENGTH }) return null

        // The long word has to be paid for out of the others, and only cells above the minimum are
        // available: below that no word exists at all.
        val target = minOf(longest, MAX_WORD_LENGTH, total - MIN_WORD_LENGTH * (parts - 1))
        if (target < MIN_WORD_LENGTH) return null
        while (lengths[0] < target) {
            val donor = (1 until parts).filter { lengths[it] > MIN_WORD_LENGTH }.randomOrNull(random)
                ?: return null
            lengths[donor]--
            lengths[0]++
        }

        // Wobble within one letter of the even split. Anything wider undoes the point of the even
        // split: the letters come off the same donor twice and leave a three-letter stub.
        val floor = maxOf(MIN_WORD_LENGTH, total / parts - 1)
        val ceiling = minOf(MAX_WORD_LENGTH, total / parts + 2)
        repeat(parts) {
            val from = random.nextInt(parts)
            val to = random.nextInt(parts)
            val keeps = if (from == 0) lengths[from] - 1 >= target else lengths[from] - 1 >= floor
            if (from != to && keeps && lengths[to] < ceiling) {
                lengths[from]--
                lengths[to]++
            }
        }
        return lengths
    }

    /** One random word per path. Repeats are avoided but not forbidden - a short length may have few candidates. */
    private fun drawWords(paths: List<IntArray>): List<String>? {
        val drawn = ArrayList<String>(paths.size)
        for (path in paths) {
            val candidates = dictionary.wordsOfLength(path.size)
            if (candidates.isEmpty()) return null
            var word = candidates[random.nextInt(candidates.size)]
            var attempt = 0
            while (word in drawn && attempt < DISTINCT_WORD_ATTEMPTS) {
                word = candidates[random.nextInt(candidates.size)]
                attempt++
            }
            drawn.add(word)
        }
        return drawn
    }

    private fun writeLetters(
        letters: CharArray,
        walls: BooleanArray,
        paths: List<IntArray>,
        words: List<String>,
    ) {
        for (cell in letters.indices) letters[cell] = if (walls[cell]) WALL else ' '
        paths.forEachIndexed { index, path ->
            val word = words[index]
            path.forEachIndexed { position, cell -> letters[cell] = word[position] }
        }
    }

    internal companion object {
        /** Re-letterings of one layout before drawing a fresh layout, which costs more. */
        const val LETTERING_ATTEMPTS = 300
        const val DISTINCT_WORD_ATTEMPTS = 16

        /** Straight-layout rejections tolerated before the turn floor eases by one step. */
        const val LAYOUTS_BEFORE_EASING = 200
        const val TURN_EASING_STEP = 0.05
        const val CEILING_EASING_STEP = 0.05

        /** Orbit draws allowed per cell before a wall pattern is abandoned as unreachable. */
        const val ORBIT_ATTEMPTS_PER_CELL = 8
    }
}
