package eu.andret.ravel.generator

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BoardGeneratorTest {

    private val dictionary = Dictionary.loadBundled(Language.POLISH, Difficulty.HARD)

    @Test
    fun `both languages generate boards at both difficulties`() {
        Language.entries.forEach { language ->
            Difficulty.entries.forEach { difficulty ->
                val words = Dictionary.loadBundled(language, difficulty)
                val generator = BoardGenerator(words, Random(2026))
                val board = assertNotNull(
                    generator.generate(BoardSpec(6, 6)),
                    "generation failed for ${language.code}/${difficulty.name}",
                )
                board.solution.forEach { placement ->
                    assertTrue(words.isWord(placement.word), "${language.code}: '${placement.word}'")
                }
                val solver = Solver(Grid(board.width, board.height), board.letters, words)
                assertEquals(1, solver.countSolutions(board.wordLengths, limit = 8), board.render())
            }
        }
    }

    @Test
    fun `awkward shapes still produce a board`() {
        // Shapes the preferred wall band cannot serve, or that leave the free region a thin snake.
        // Generation retries until it succeeds, so the only acceptable outcome here is a board.
        val shapes = listOf(3 to 1, 4 to 3, 8 to 5, 5 to 9, 10 to 4, 12 to 12)
        val limits = GenerationLimits(timeoutMillis = SHAPE_TIMEOUT_MILLIS)
        shapes.forEach { (width, height) ->
            Difficulty.entries.forEach { difficulty ->
                val words = Dictionary.loadBundled(Language.POLISH, difficulty)
                val spec = BoardSpec.forDifficulty(width, height, difficulty)
                val board = assertNotNull(
                    BoardGenerator(words, Random(11)).generate(spec, GenerationStats(), limits),
                    "no board for ${width}x$height at $difficulty",
                )
                assertTrue(board.solution.isNotEmpty())
                val solver = Solver(Grid(board.width, board.height), board.letters, words)
                assertEquals(1, solver.countSolutions(board.wordLengths, limit = 8), board.render())
            }
        }
    }

    @Test
    fun `the free region is always one connected piece`() {
        val spec = BoardSpec.forDifficulty(9, 9, Difficulty.HARD)
        val generator = BoardGenerator(dictionary, Random(3))
        repeat(TURN_SAMPLE) {
            val board = assertNotNull(generator.generate(spec))
            val grid = Grid(board.width, board.height)
            val free = (0 until grid.cellCount).filterNot(board::isWall)
            val reached = HashSet<Int>()
            val stack = ArrayDeque(listOf(free.first()))
            reached.add(free.first())
            while (stack.isNotEmpty()) {
                grid.neighbours[stack.removeLast()].forEach { neighbour ->
                    if (!board.isWall(neighbour) && reached.add(neighbour)) stack.addLast(neighbour)
                }
            }
            assertEquals(free.size, reached.size, "free cells split into islands\n${board.render()}")
        }
    }

    @Test
    fun `boards wind inside the band their difficulty demands`() {
        Difficulty.entries.forEach { difficulty ->
            val generator = BoardGenerator(Dictionary.loadBundled(Language.POLISH, difficulty), Random(808))
            val spec = BoardSpec.forDifficulty(6, 6, difficulty)
            repeat(TURN_SAMPLE) {
                val stats = GenerationStats()
                val board = assertNotNull(generator.generate(spec, stats), "generation failed for $difficulty")
                if (stats.layoutsUnshapeable >= BoardGenerator.LAYOUTS_BEFORE_EASING) return@repeat
                assertTrue(
                    board.turnRatio >= difficulty.minTurnRatio &&
                        board.worstTurnRatio <= difficulty.maxTurnRatio + TOLERANCE &&
                        board.worstDirectness <= difficulty.maxDirectness + TOLERANCE,
                    "$difficulty wants turns ${difficulty.minTurnRatio}..${difficulty.maxTurnRatio} " +
                        "and directness under ${difficulty.maxDirectness}, got %.2f/%.2f and %.2f\n%s"
                            .format(
                                board.turnRatio, board.worstTurnRatio,
                                board.worstDirectness, board.renderSolution(),
                            ),
                )
            }
        }
    }

    @Test
    fun `every board carries a word long enough to cross it`() {
        listOf(5 to 5, 6 to 6, 9 to 9, 8 to 5).forEach { (width, height) ->
            val spec = BoardSpec.forDifficulty(width, height, Difficulty.HARD)
            val generator = BoardGenerator(dictionary, Random(515))
            repeat(TURN_SAMPLE) {
                val board = assertNotNull(generator.generate(spec), "no board for ${width}x$height")
                assertTrue(
                    board.wordLengths.max() >= spec.requiredLongestWord,
                    "${width}x$height wants ${spec.requiredLongestWord}, got ${board.wordLengths}",
                )
            }
        }
    }

    @Test
    fun `words never run alongside themselves into clumps`() {
        Difficulty.entries.forEach { difficulty ->
            val generator = BoardGenerator(Dictionary.loadBundled(Language.POLISH, difficulty), Random(616))
            val spec = BoardSpec.forDifficulty(7, 7, difficulty)
            repeat(TURN_SAMPLE) {
                val stats = GenerationStats()
                val board = assertNotNull(generator.generate(spec, stats))
                // The limits ease on grids that cannot meet them, so a board that needed easing is
                // allowed past them. Asserting otherwise would make this test a coin flip.
                if (stats.layoutsUnshapeable >= BoardGenerator.LAYOUTS_BEFORE_EASING) return@repeat
                assertTrue(
                    board.worstFoldRatio <= difficulty.maxFoldRatio + TOLERANCE,
                    "$difficulty caps folds at ${difficulty.maxFoldRatio}, got %.2f\n%s".format(
                        board.worstFoldRatio,
                        board.renderSolution(),
                    ),
                )
            }
        }
    }

    @Test
    fun `harder boards wind more than easier ones`() {
        val averages = Difficulty.entries.associateWith { difficulty ->
            val generator = BoardGenerator(Dictionary.loadBundled(Language.POLISH, difficulty), Random(909))
            val spec = BoardSpec.forDifficulty(6, 6, difficulty)
            (1..TURN_SAMPLE).map { assertNotNull(generator.generate(spec)).turnRatio }.average()
        }
        assertTrue(
            averages.getValue(Difficulty.HARD) > averages.getValue(Difficulty.EASY),
            "turn ratios did not separate: $averages",
        )
    }

    @Test
    fun `a rank cap holds for every word on the board`() {
        val ranks = HashMap<String, Int>()
        dictionary.words.forEachIndexed { rank, word -> ranks.putIfAbsent(word, rank) }
        val pool = dictionary.poolTo(RANK_CAP)
        // MIN_POOL can widen a length the cap starves, so the ceiling per length is the cap or that
        // floor, whichever reaches further - never a word chosen from beyond both.
        val allowed = (MIN_WORD_LENGTH..MAX_WORD_LENGTH).associateWith { length ->
            maxOf(RANK_CAP, ranks.getValue(pool.ofLength(length).last()))
        }
        val generator = BoardGenerator(dictionary, Random(20260808))
        val spec = BoardSpec.forDifficulty(6, 6, Difficulty.HARD, maxWordRank = RANK_CAP)
        repeat(BOARD_SAMPLE) {
            val board = assertNotNull(generator.generate(spec), "generation failed")
            board.solution.forEach { placement ->
                val rank = assertNotNull(ranks[placement.word], "'${placement.word}' is not in the dictionary")
                assertTrue(
                    rank <= allowed.getValue(placement.word.length),
                    "'${placement.word}' ranks $rank, past the $RANK_CAP cap",
                )
            }
        }
    }

    @Test
    fun `uniqueness is proved against the whole dictionary, not the capped pool`() {
        // The point of separating the two: a board lettered from the commonest 1500 words is still
        // only publishable when no second tiling exists among all 51 000. Were the solver reading
        // the pool instead, this board would pass while a player who knows a rarer word could find
        // an answer the game never considered.
        val generator = BoardGenerator(dictionary, Random(31337))
        val spec = BoardSpec.forDifficulty(6, 6, Difficulty.HARD, maxWordRank = RANK_CAP)
        repeat(SMALL_SAMPLE) {
            val board = assertNotNull(generator.generate(spec), "generation failed")
            val solver = Solver(Grid(board.width, board.height), board.letters, dictionary)
            assertEquals(1, solver.countSolutions(board.wordLengths, limit = 8), board.render())
        }
    }

    @Test
    fun `every generated board is internally consistent`() {
        val generator = BoardGenerator(dictionary, Random(20260805))
        repeat(BOARD_SAMPLE) {
            val board = assertNotNull(generator.generate(BoardSpec(5, 5)), "generation failed")
            assertBoardIsConsistent(board)
        }
    }

    @Test
    fun `generated boards survive an independent uniqueness check`() {
        val generator = BoardGenerator(dictionary, Random(4711))
        repeat(BOARD_SAMPLE) {
            val board = assertNotNull(generator.generate(BoardSpec(5, 5)), "generation failed")
            val solver = Solver(Grid(board.width, board.height), board.letters, dictionary)
            assertEquals(1, solver.countSolutions(board.wordLengths, limit = 8), board.render())
        }
    }

    private fun assertBoardIsConsistent(board: Board) {
        val grid = Grid(board.width, board.height)
        val owner = IntArray(grid.cellCount) { UNCOVERED }

        board.solution.forEachIndexed { index, placement ->
            assertTrue(dictionary.isWord(placement.word), "'${placement.word}' is not in the dictionary")
            assertEquals(placement.word.length, placement.cells.size, "word and path length differ")
            placement.cells.forEachIndexed { position, cell ->
                assertEquals(UNCOVERED, owner[cell], "cell $cell belongs to two words")
                owner[cell] = index
                assertEquals(placement.word[position], board.letters[cell], "letter at cell $cell")
                if (position > 0) {
                    val previous = placement.cells[position - 1]
                    assertTrue(cell in grid.neighbours[previous], "cells $previous and $cell are not adjacent")
                }
            }
        }

        for (cell in 0 until grid.cellCount) {
            if (board.isWall(cell)) {
                assertEquals(UNCOVERED, owner[cell], "a word runs through the wall at cell $cell")
            } else {
                assertTrue(owner[cell] != UNCOVERED, "cell $cell is neither wall nor part of a word")
            }
        }
    }

    private companion object {
        const val BOARD_SAMPLE = 25
        const val SMALL_SAMPLE = 8

        /** The tightest cap `PuzzleSpec.rankFor` hands out, which is the one worth testing. */
        const val RANK_CAP = 1_500

        /**
         * Enough boards for the difficulties to separate reliably rather than usually.
         *
         * This is the one test here making a statistical claim instead of a deterministic one, and
         * the margin is thinner than it looks: measured over 200 boards the averages sit at 0.59
         * and 0.63, while individual boards range across 0.45 to 0.77. At twelve boards - what this
         * used to sample - the difference is about two standard errors, so the test passed most of
         * the time and flipped whenever anything upstream reshuffled the search, which a dictionary
         * rebuild does. Sixty puts it past four, and costs under a second.
         */
        const val TURN_SAMPLE = 60
        const val SHAPE_TIMEOUT_MILLIS = 60_000L

        /** Ratios are integer counts over integer lengths; the limits are the rounded decimals. */
        const val TOLERANCE = 1e-9
        const val UNCOVERED = -1
    }
}
