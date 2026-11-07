package eu.andret.ravel.generator

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * @param minWallRatio lower bound on the share of cells left blocked. Walls are not decoration:
 *   they cut the number of alternative tilings, so a board with none is far harder to make unique.
 *   They are always symmetric, because scattered walls read as damage.
 *
 *   **18..28% up to 7x7, 8..18% above**, and the split is the same one as [maxLengthRepeats]. Up
 *   to 7x7 every length must differ, and distinct lengths inside the length band only fit a smaller
 *   free area: a 6x6 a tenth walled can hold four of them only as something like 6, 7, 8, 11, a
 *   fifth walled gives 5, 7, 8, 9. Above 7x7 lengths may repeat, so the walls have no such job and
 *   keep the share they always had.
 *
 *   **Not more.** 28..40% everywhere was tried and lasted one look at a board: walls in the
 *   middle of the grid left a third of the free cells with two free neighbours or fewer, so the
 *   corridors spelled out the paths and the letters were no longer needed to find the words.
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
 * @param maxWordRank how deep into the frequency-ranked dictionary the board's own words may reach.
 *   An axis of its own rather than part of [Difficulty], because the two are asking different
 *   questions: difficulty shapes the paths, this shapes the vocabulary, and a board can want an
 *   easy word on a hard path. Only the words written onto the board are held back - uniqueness is
 *   still proved against the whole dictionary, see [Dictionary.poolTo].
 * @param targetWordLength overrides the length the generator aims at; normally left to scale with
 *   the grid.
 */
data class BoardSpec(
    val width: Int,
    val height: Int,
    val minWallRatio: Double = when {
        width * height <= SMALL_BOARD_CELLS -> 0.08
        width * height <= DISTINCT_LENGTHS_UP_TO_CELLS -> 0.18
        else -> 0.08
    },
    val maxWallRatio: Double = when {
        width * height <= SMALL_BOARD_CELLS -> 0.16
        width * height <= DISTINCT_LENGTHS_UP_TO_CELLS -> 0.28
        else -> 0.18
    },
    val minTurnRatio: Double = 0.0,
    val maxTurnRatio: Double = 1.0,
    val maxDirectness: Double = 1.0,
    val turnBias: Double = 0.0,
    val directnessBias: Double = 0.0,
    val maxFoldRatio: Double = 1.0,
    val foldAvoidance: Double = 0.0,
    val maxWordRank: Int = NO_RANK_LIMIT,
    val targetWordLength: Double? = null,
) {
    val cellCount: Int get() = width * height

    /**
     * How many words to hide on a board of [freeCount] cells.
     *
     * Computed from word length rather than asked for: the generator aims at words averaging
     * [effectiveTargetWordLength] letters, which makes three or four words on a small grid and
     * twenty on a large one without either number being written down. Naming a count instead does
     * the opposite - it forces three-letter words onto a big grid, or twelve-letter ones onto a
     * small grid, and on extreme shapes leaves nothing that fits at all.
     */
    internal fun wordCountFor(freeCount: Int, random: Random): Int {
        // Few enough words that they all fit under [MAX_WORD_LENGTH] is not enough: one of them also
        // has to be able to sit at the floor of the length band, which is half the longest word. So
        // the fewest is the count at which one word of `MAX_WORD_LENGTH / 2` letters plus a full set
        // of longest ones still covers the grid.
        //
        // Without this the band is a promise the arithmetic cannot keep. A 10x10 leaves about eighty
        // free cells; at seven words the mean length is already twelve, every word is pinned to the
        // ceiling, and there is no spare letter anywhere for a word to be short with. Measured
        // before this line existed: a 10x10 came out as seven twelve-letter words - the band was
        // wide open and completely unreachable.
        val fewest = ceilDiv(freeCount - MAX_WORD_LENGTH / 2, MAX_WORD_LENGTH) + 1
        val most = freeCount / MIN_WORD_LENGTH
        val targeted = (freeCount / effectiveTargetWordLength).roundToInt()
        // The spread only goes down. Boards vary either way in practice, because [fewest] pushes
        // back on large grids, and an extra word is worse than one fewer: it is paid for out of
        // every other word's length, which is how a 6x6 ends up with three four-letter words.
        // None at all on a board of 5x5 or less, where one word fewer is a third of the board's
        // words: the count it falls back to is already tried by lengthsFor when the one asked for
        // has no fitting list, so the spread could only ever take a fourth word away.
        val spread = if (cellCount <= SMALL_BOARD_CELLS) 0 else maxOf(1, targeted / 8)
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
     * anywhere to go before it must double back on itself. The ratio itself steps down once above
     * 7x7; see [LARGE_BOARD_WORD_LENGTH_PER_SIDE].
     */
    internal val effectiveTargetWordLength: Double
        get() = targetWordLength ?: (lengthPerSide * (width + height) / 2.0)
            .coerceIn(MIN_WORD_LENGTH + 1.0, MAX_WORD_LENGTH - 1.0)

    /** Split at the same area as [maxLengthRepeats], where the length rules change too. */
    private val lengthPerSide: Double
        get() = if (cellCount <= DISTINCT_LENGTHS_UP_TO_CELLS) {
            WORD_LENGTH_PER_SIDE
        } else {
            LARGE_BOARD_WORD_LENGTH_PER_SIDE
        }

    /**
     * Wall counts to draw from: the preferred ratio band, narrowed to counts that leave enough
     * cells for at least one word.
     *
     * With the word count computed per board there is no shape the band cannot serve, because the
     * count adapts to whatever the walls leave behind - except a grid so small that the band's
     * lowest count already leaves less than a word. A 3x1 asked for a third walled is one wall and
     * two letters, so both ends are first capped at what still leaves a word, which there means no
     * walls at all. Under the old 8..18% band that case happened to round to zero by itself.
     */
    internal fun wallCounts(): List<Int> {
        val mostAllowed = cellCount - MIN_WORD_LENGTH
        val lowest = minOf((minWallRatio * cellCount).roundToInt(), mostAllowed)
        val highest = minOf((maxWallRatio * cellCount).roundToInt(), mostAllowed)
        return (lowest..highest).toList()
    }

    /**
     * How many times one word length may appear on the board: once up to 7x7, twice above.
     * See the rules on `BoardGenerator.lengthsFor` for why, and for why there is no third step.
     *
     * Decided by area rather than by side, because what makes distinct lengths reachable is how
     * many cells the words must fill; a 10x4 has the area of a 6x7 and the same arithmetic.
     */
    internal val maxLengthRepeats: Int
        get() = if (cellCount <= DISTINCT_LENGTHS_UP_TO_CELLS) 1 else 2

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

        /**
         * The same, above 7x7. One letter per side made a 10x10 into nine words of 7 to 12 letters,
         * mostly in pairs climbing to the ceiling (9, 9, 10, 10, 11, 11, 12, 12) - long, tiring to
         * read, and drawn from the thinnest bands of the dictionary. At 0.75 a 10x10 holds about ten
         * words averaging 8.4 letters, an 8x8 about nine averaging 6.5, and the 10x10 tail fell from
         * 1.07 s to 0.28 s on the same seeds: shorter paths partition more easily. Not lower, because
         * the length band already holds a 10x10 at 6 letters and up - more words would only repeat
         * the same lengths more often.
         */
        const val LARGE_BOARD_WORD_LENGTH_PER_SIDE = 0.75

        /**
         * The largest area, 5x5, given its own wall band and no downward spread in the word count.
         *
         * Four distinct word lengths inside the length band fit 5x5 only with 18 free cells (3, 4, 5,
         * 6) or 22 to 23 (4, 5, 6, 7 / 8). The 18-28% band of the next size up gave 4 to 6 walls, so
         * three words every time; asking for 7 walls got 6 from the orbit placement and still three.
         * 8-16% gives 2 to 4 walls, where a fourth word fits, and fewer corridor cells besides.
         */
        const val SMALL_BOARD_CELLS = 5 * 5

        /** The largest area, 7x7, on which every word length must be different. */
        const val DISTINCT_LENGTHS_UP_TO_CELLS = 7 * 7

        private fun ceilDiv(value: Int, divisor: Int) = (value + divisor - 1) / divisor

        fun forDifficulty(
            width: Int,
            height: Int,
            difficulty: Difficulty,
            // Defaults to everything the difficulty loaded, which is what a caller with no curve of
            // its own means: hold nothing back beyond the dictionary it already asked for.
            maxWordRank: Int = difficulty.maxRank,
        ) = BoardSpec(
            width = width,
            height = height,
            minTurnRatio = difficulty.minTurnRatio,
            maxTurnRatio = difficulty.maxTurnRatio,
            maxDirectness = difficulty.maxDirectness,
            turnBias = difficulty.turnBias,
            directnessBias = difficulty.directnessBias,
            maxFoldRatio = difficulty.maxFoldRatio,
            foldAvoidance = difficulty.foldAvoidance,
            maxWordRank = maxWordRank,
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
        val pool = dictionary.poolTo(spec.maxWordRank)
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
                val wordLengths = lengthsFor(wordCount, freeCount, spec) ?: continue
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

                val board = letterUntilUnique(spec, paths, walls, letters, solver, pool, stats)
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
        pool: WordPool,
        stats: GenerationStats,
    ): Board? {
        repeat(LETTERING_ATTEMPTS) {
            val words = drawWords(paths, pool) ?: return null
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
     * blobs where a third of a row could vanish, which reads as damage rather than design. Even at
     * a quarter of the grid walled, connectivity is common enough to get by redrawing.
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
        if (wallCount > 0 && !placeWallOrbits(grid, walls, wallCount)) return null
        if (grid.cellCount - walls.count { it } < MIN_WORD_LENGTH) return null
        if (!spansEveryRowAndColumn(grid, walls)) return null
        if (longestWallRun(grid, walls) > maxWallRun(grid)) return null
        return if (freeRegionIsConnected(grid, walls)) walls else null
    }

    /**
     * Fills [walls] with whole orbits of one randomly chosen symmetry, up to [wallCount] cells.
     * Returns false when it could not place a single orbit, which is the one outcome the caller
     * cannot work with: a board asked for walls and got none.
     *
     * Split out of [symmetricWalls] so that the drawing loop and the pattern's acceptance tests sit
     * at one level each. They fail for unrelated reasons - this one runs out of attempts, the tests
     * reject a pattern that was placed perfectly well - and reading either meant reading both.
     *
     * The attempt cap is needed because an orbit draw can keep colliding with what is already
     * placed, most often on the last orbit, where the remaining budget is smaller than any orbit
     * the symmetry produces. A capped loop that ends short is fine: the caller reads the achieved
     * count off the result.
     */
    private fun placeWallOrbits(grid: Grid, walls: BooleanArray, wallCount: Int): Boolean {
        val symmetry = WallSymmetry.entries.random(random)
        var placed = 0
        var attempts = 0
        while (placed < wallCount && attempts < grid.cellCount * ORBIT_ATTEMPTS_PER_CELL) {
            attempts++
            val orbit = dominoOrbit(grid, symmetry) ?: continue
            if (orbit.any { walls[it] } || placed + orbit.size > wallCount) continue
            orbit.forEach { walls[it] = true }
            placed += orbit.size
        }
        return placed > 0
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
     *
     * The reference board's longest run is two.
     *
     * Three was tried alongside a 28..40% wall share, to stop the extra orbits colliding. At the
     * 18..28% that replaced it, three bought nothing measurable over two - a 6x6 walled 18% against
     * 17%, with the same share of corridor cells - and it had cost an occasional broken column of
     * single walls, so the cap went back.
     */
    private fun maxWallRun(grid: Grid): Int = maxOf(2, minOf(grid.width, grid.height) / 3)

    /**
     * The longest unbroken line of walls along any row or column.
     *
     * Rows and columns are the same scan over a different pair of indices, so both go through
     * [longestRun] rather than being written out twice: the duplicated version differed only in
     * which loop was inner, which is exactly the kind of difference that survives a careless edit.
     */
    private fun longestWallRun(grid: Grid, walls: BooleanArray): Int = maxOf(
        longestRun(grid.height, grid.width) { row, column -> walls[row * grid.width + column] },
        longestRun(grid.width, grid.height) { column, row -> walls[row * grid.width + column] },
    )

    /**
     * The longest unbroken run of cells [blocked] reports as walls, over [lines] lines of [length]
     * cells each. The caller decides whether a line is a row or a column by how it reads its two
     * arguments; nothing here knows the difference.
     */
    private inline fun longestRun(lines: Int, length: Int, blocked: (Int, Int) -> Boolean): Int {
        var longest = 0
        for (line in 0 until lines) {
            var run = 0
            for (position in 0 until length) {
                run = if (blocked(line, position)) run + 1 else 0
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
     * Word lengths summing to [total], drawn uniformly from every list that obeys three rules:
     *
     * 1. **The band: no word shorter than half the longest.** Its floor used to be one letter
     *    below the mean, which let a 10x10 produce only 9..12 - six twelve-letter words out of
     *    eight, a list of lengths that carried no information although it is **the only clue this
     *    game gives**, drawn from the thinnest bands of the dictionary. The per-word fold gate in
     *    [PathShape.accepts] is what keeps short words from coming out as squat blocks, so a
     *    near-flat list was a second defence that cost more than it bought.
     * 2. **One word reaches [BoardSpec.requiredLongestWord]**, so something crosses the grid.
     * 3. **No length is used more than [BoardSpec.maxLengthRepeats] times** - once on boards up to
     *    7x7, twice above. The clue list is a set of keys: when every length is different, each one
     *    names exactly one word, so a word found is a word *identified*, and the list reads as a
     *    designed staircase (4, 5, 6, 7) instead of a tally (7, 7, 8, 8). Measured on 60 boards of
     *    the game this one descends from, 57 had every length different; this generator managed
     *    under a fifth of its 6x6 boards and almost none of its 7x7 ones before the rule. Above 7x7 the band leaves too few distinct
     *    lengths: 8x8 could hold only five words with a twelve-letter one on almost every board, and
     *    10x10 has no distinct list at all, since 6..12 is seven values for eight or more words.
     *
     * Enumerating every list and picking one replaced an even split followed by random swaps and a
     * donor loop that paid for the long word. That loop had to be taught the band's floor
     * separately, and broke it on a 10x4 when it was not; here a list outside the rules is never
     * built, so there is no second route for a length to escape by.
     *
     * The word count asked for is a preference, not a promise: distinct lengths in a band are a
     * narrow target, and a 6x6 that cannot hold five of them can usually hold four. Counts are
     * tried nearest first, fewer before more at equal distance, for the reason [BoardSpec.wordCountFor]
     * gives for its own spread: an extra word is paid for out of every other word. Null means no count fits at all, and the caller redraws the walls - the
     * rule is not eased, because a board that sometimes repeats a length on a 6x6 is the board
     * this replaced.
     */
    private fun lengthsFor(preferredCount: Int, total: Int, spec: BoardSpec): IntArray? {
        val counts = (1..total / MIN_WORD_LENGTH)
            .sortedWith(compareBy({ abs(it - preferredCount) }, { it }))
        for (count in counts) {
            val options = lengthListsFor(count, total, spec.requiredLongestWord, spec.maxLengthRepeats)
            if (options.isNotEmpty()) return options[random.nextInt(options.size)].copyOf()
        }
        return null
    }

    /**
     * Every descending list of [parts] lengths obeying the rules of [lengthsFor], cached because a
     * board draws many layouts with the same few totals and the lists never change. Small: the
     * widest case, a 12x12 allowing pairs, is a few thousand lists.
     */
    private fun lengthListsFor(parts: Int, total: Int, longest: Int, maxRepeats: Int): List<IntArray> =
        lengthLists.getOrPut(LengthQuery(parts, total, longest, maxRepeats)) {
            val found = ArrayList<IntArray>()
            for (top in maxOf(longest, MIN_WORD_LENGTH)..minOf(MAX_WORD_LENGTH, total)) {
                val lengths = IntArray(parts)
                lengths[0] = top
                val floor = maxOf(MIN_WORD_LENGTH, (top + 1) / 2)
                collectLengths(lengths, 1, total - top, floor, maxRepeats, found)
            }
            found
        }

    private val lengthLists = HashMap<LengthQuery, List<IntArray>>()

    private data class LengthQuery(val parts: Int, val total: Int, val longest: Int, val maxRepeats: Int)

    /**
     * Fills [lengths] from [filled] on, in descending order, so that equal lengths sit next to each
     * other and a repeat is caught by looking [maxRepeats] places back.
     */
    private fun collectLengths(
        lengths: IntArray,
        filled: Int,
        remaining: Int,
        floor: Int,
        maxRepeats: Int,
        found: MutableList<IntArray>,
    ) {
        val left = lengths.size - filled
        if (left == 0) {
            if (remaining == 0) found.add(lengths.copyOf())
            return
        }
        if (floor * left > remaining) return
        for (length in minOf(lengths[filled - 1], remaining) downTo floor) {
            // Descending, so every later length is at most this one: if this cannot reach the total
            // neither can anything smaller.
            if (length * left < remaining) break
            if (filled >= maxRepeats && lengths[filled - maxRepeats] == length) continue
            lengths[filled] = length
            collectLengths(lengths, filled + 1, remaining - length, floor, maxRepeats, found)
        }
    }

    /** One random word per path. Repeats are avoided but not forbidden - a short length may have few candidates. */
    private fun drawWords(paths: List<IntArray>, pool: WordPool): List<String>? {
        val drawn = ArrayList<String>(paths.size)
        for (path in paths) {
            val candidates = pool.ofLength(path.size)
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
