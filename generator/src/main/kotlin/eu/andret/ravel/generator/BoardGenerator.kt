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
    val minWallRatio: Double = 0.08,
    val maxWallRatio: Double = 0.18,
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
     * The reference board's longest run is two.
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
     * Word lengths in `$MIN_WORD_LENGTH..$MAX_WORD_LENGTH` summing to [total], at least one of them
     * reaching [longest].
     *
     * Lengths start from an even split and then spread over a band, rather than being handed out at
     * random: an unconstrained split leaves three-letter stubs next to the long word whatever the
     * grid, and the long word is the one that has to survive.
     *
     * **The band is `half the longest .. the longest`, and the floor used to be one letter below
     * the mean.** That old floor was written when short words were the problem - they came out as
     * squat little blocks in a corner - and it stopped being the thing protecting against that: the
     * per-word fold gate does, at every length, and [PathShape.accepts] says so in as many words
     * about the 2x2 block a four-cell word makes. A near-flat length list was the second layer of a
     * defence that only ever needed one, and on a large grid it cost more than it bought:
     *
     * - a 10x10 could only produce lengths 9..12, so six of its eight words were often twelve
     *   letters and the list of lengths - **the only clue this game gives** - carried almost no
     *   information. A list that repeats one number is not a clue.
     * - those are the thinnest bands of the dictionary. Within rank 4000 Polish has 574 five-letter
     *   lemmas and 93 twelve-letter ones, so the biggest boards drew from the narrowest pool while
     *   the most recognisable vocabulary in the language never appeared on them at all.
     *
     * The mean is untouched, and so is the word count that comes from it: this widens what the
     * lengths may be, not how many there are. [BoardSpec.requiredLongestWord] still forces one word
     * to cross the grid, which is the guarantee the flat list was standing in for.
     */
    private fun randomComposition(parts: Int, total: Int, longest: Int): IntArray? {
        if (parts < 1) return null
        if (total < MIN_WORD_LENGTH * parts) return null
        if (total > MAX_WORD_LENGTH * parts) return null

        val lengths = IntArray(parts) { total / parts }
        repeat(total % parts) { lengths[it]++ }
        if (lengths.any { it !in MIN_WORD_LENGTH..MAX_WORD_LENGTH }) return null

        // The band the lengths may spread over: no word shorter than half the longest one.
        //
        // The floor is taken from the ceiling rather than from the longest length the board actually
        // ends up with, because that one is not known until this function has finished. The ceiling
        // is an upper bound on it, so a floor derived from the ceiling satisfies the rule whatever
        // the board settles on - it can only ever be stricter than needed, never looser.
        //
        // The rule scales by itself and that is why it is a ratio rather than a constant. The
        // longest word grows with the grid, so the floor does too: a 5x5 keeps the 4..7 band it
        // already had, an 8x8 opens from 7..10 to 5..10, and a 10x10 from 9..12 to 6..12. There is
        // no size threshold to justify anywhere, and nothing that plays noticeably differently on
        // either side of one.
        //
        // The even split can never start below the floor: with a mean of `m` the ceiling is at most
        // `m + 2`, so the floor is at most `(m + 3) / 2`, which is `<= m` for every `m` from
        // [MIN_WORD_LENGTH] up. The check above has already established that much.
        val ceiling = minOf(MAX_WORD_LENGTH, total / parts + 2)
        val floor = maxOf(MIN_WORD_LENGTH, (ceiling + 1) / 2)

        // The long word has to be paid for out of the others, and only cells above the *floor* are
        // available - not, as this used to say, cells above [MIN_WORD_LENGTH]. Taking a donor below
        // the floor would break the band by the one route that does not go through the wobble below,
        // and it is the route the awkward shapes take: a 10x4 grid wants a seven-letter word out of
        // a mean of six, so somebody has to pay for it. Paying down to three would put a
        // three-letter word next to a seven-letter one, which is the shape of list this band exists
        // to rule out.
        //
        // When the floor leaves nobody able to pay, this returns null and the caller draws a fresh
        // layout - walls, word count and all. That is cheap and already how an impossible split is
        // handled; it is not a reason to quietly break the rule instead.
        val target = minOf(longest, MAX_WORD_LENGTH, total - floor * (parts - 1))
        if (target < MIN_WORD_LENGTH) return null
        while (lengths[0] < target) {
            val donor = (1 until parts).filter { lengths[it] > floor }.randomOrNull(random)
                ?: return null
            lengths[donor]--
            lengths[0]++
        }

        // Enough swaps to actually reach the ends of the band. The old loop moved one letter per
        // iteration and ran `parts` times, which was sized for a band one letter wide; against a
        // band six wide it would leave the lengths sitting where the even split put them and the
        // widening would be invisible. Proportional to the area to be filled, and cheap - this is
        // integer arithmetic over an array of about ten.
        repeat(parts * maxOf(1, ceiling - floor)) {
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
