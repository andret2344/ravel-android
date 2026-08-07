package eu.andret.ravel.generator

import java.io.File
import kotlin.random.Random
import kotlin.system.exitProcess

/**
 * Command line entry point.
 *
 * Usage: generator [--language pl|en] [--difficulty easy|hard] [--width N] [--height N]
 *                  [--count N] [--seed N] [--max-rank N] [--dictionary PATH]
 *                  [--timeout SECONDS] [--min-turns R] [--max-turns R] [--turn-bias R]
 *                  [--min-walls R] [--max-walls R] [--target-length R] [--quiet]
 *
 * Difficulty sets how far down the frequency-ranked dictionary boards may reach and how much the
 * words must wind; the individual overrides exist for experiments.
 *
 * Generation keeps retrying until it succeeds. `--timeout` puts a wall-clock limit on that, which
 * is the only way this command reports a failure rather than a board.
 */
fun main(args: Array<String>) {
    val options = try {
        Options.parse(args)
    } catch (failure: IllegalArgumentException) {
        System.err.println(failure.message)
        exitProcess(2)
    }

    val spec = BoardSpec.forDifficulty(options.width, options.height, options.difficulty)
        .let { base ->
            base.copy(
                minTurnRatio = options.minTurnRatio ?: base.minTurnRatio,
                maxTurnRatio = options.maxTurnRatio ?: base.maxTurnRatio,
                maxDirectness = options.maxDirectness ?: base.maxDirectness,
                turnBias = options.turnBias ?: base.turnBias,
                minWallRatio = options.minWallRatio ?: base.minWallRatio,
                maxWallRatio = options.maxWallRatio ?: base.maxWallRatio,
                targetWordLength = options.targetWordLength ?: base.targetWordLength,
            )
        }

    // An impossible board shape is a usage mistake, not a crash: report it like one, and report it
    // before spending a tenth of a second loading a dictionary that will not be used.
    try {
        spec.validate()
    } catch (failure: IllegalArgumentException) {
        System.err.println(failure.message)
        exitProcess(2)
    }

    val loadStartedAt = System.nanoTime()
    val dictionary = options.dictionaryFile
        ?.let { Dictionary.load(options.language, it, options.maxRank) }
        ?: Dictionary.loadBundled(options.language, options.maxRank)
    val loadMillis = (System.nanoTime() - loadStartedAt) / 1_000_000
    if (!options.quiet) {
        println(
            "Dictionary ${options.language.code} (${options.difficulty.name.lowercase()}): " +
                "${dictionary.words.size} words, loaded in $loadMillis ms"
        )
    }

    val generator = BoardGenerator(dictionary, Random(options.seed))
    var generated = 0
    var totalMillis = 0L

    val limits = GenerationLimits(timeoutMillis = options.timeoutSeconds?.let { it * 1000 })
    repeat(options.count) { index ->
        val stats = GenerationStats()
        val board = generator.generate(spec, stats, limits)
        totalMillis += stats.elapsedMillis
        if (board == null) {
            println("Board ${index + 1}: FAILED after ${stats.layouts} layouts, ${stats.elapsedMillis} ms")
            return@repeat
        }
        generated++
        printBoard(index + 1, board, stats, options.quiet)
    }

    println()
    println(
        "Generated $generated/${options.count} boards, " +
            "$totalMillis ms total, ${totalMillis / options.count} ms per attempt"
    )
    if (generated < options.count) exitProcess(1)
}

private fun printBoard(number: Int, board: Board, stats: GenerationStats, quiet: Boolean) {
    println()
    println(
        "Board $number: ${board.width}x${board.height}, lengths ${board.wordLengths}, " +
            "turns %.2f/%.2f, directness %.2f/%.2f, folds %.2f/%.2f, walls ${board.wallCount}, "
                .format(
                    board.turnRatio, board.worstTurnRatio,
                    board.directness, board.worstDirectness,
                    board.foldRatio, board.worstFoldRatio,
                ) +
            "${stats.elapsedMillis} ms (layout ${stats.layoutMillis} ms, uniqueness ${stats.uniquenessMillis} ms), " +
            "${stats.layouts} layouts (${stats.layoutsTooStraight} too straight, " +
            "${stats.layoutsUnshapeable} unshapeable), " +
            "${stats.uniquenessChecks} uniqueness checks"
    )
    print(board.render())
    if (quiet) return
    println("-- solution --")
    print(board.renderSolution())
    println(board.solution.joinToString(", ") { it.word })
}

private class Options(
    val language: Language,
    val difficulty: Difficulty,
    val width: Int,
    val height: Int,
    val maxRank: Int,
    val count: Int,
    val seed: Long,
    val dictionaryFile: File?,
    val quiet: Boolean,
    val timeoutSeconds: Long?,
    val minTurnRatio: Double?,
    val maxTurnRatio: Double?,
    val maxDirectness: Double?,
    val turnBias: Double?,
    val minWallRatio: Double?,
    val maxWallRatio: Double?,
    val targetWordLength: Double?,
) {
    companion object {
        private val KNOWN_OPTIONS = setOf(
            "language", "difficulty", "width", "height", "count", "seed", "max-rank",
            "dictionary", "timeout", "min-turns", "max-turns", "max-directness", "turn-bias",
            "min-walls", "max-walls", "target-length",
        )

        fun parse(args: Array<String>): Options {
            val values = HashMap<String, String>()
            var quiet = false
            var index = 0
            while (index < args.size) {
                val argument = args[index]
                require(argument.startsWith("--")) { "Unexpected argument '$argument'" }
                if (argument == "--quiet") {
                    quiet = true
                    index++
                    continue
                }
                require(index + 1 < args.size) { "Missing value for $argument" }
                val name = argument.removePrefix("--")
                // An unknown option is almost always a stale flag or a typo. Silently ignoring it
                // would let a command look like it did what was asked while doing something else.
                require(name in KNOWN_OPTIONS) {
                    val hint = if (name == "words") {
                        " The word count is computed from the grid, so this option no longer exists."
                    } else {
                        ""
                    }
                    "Unknown option '--$name'.$hint Known options: " +
                        KNOWN_OPTIONS.sorted().joinToString { "--$it" } + ", --quiet"
                }
                values[name] = args[index + 1]
                index += 2
            }
            val width = values["width"]?.toInt() ?: 5
            val difficulty = values["difficulty"]?.let(Difficulty::of) ?: Difficulty.HARD
            return Options(
                language = values["language"]?.let(Language::of) ?: Language.POLISH,
                difficulty = difficulty,
                width = width,
                height = values["height"]?.toInt() ?: width,
                maxRank = values["max-rank"]?.toInt() ?: difficulty.maxRank,
                count = values["count"]?.toInt() ?: 1,
                seed = values["seed"]?.toLong() ?: Random.nextLong(),
                dictionaryFile = values["dictionary"]?.let(::File),
                quiet = quiet,
                timeoutSeconds = values["timeout"]?.toLong(),
                minTurnRatio = values["min-turns"]?.toDouble(),
                maxTurnRatio = values["max-turns"]?.toDouble(),
                maxDirectness = values["max-directness"]?.toDouble(),
                turnBias = values["turn-bias"]?.toDouble(),
                minWallRatio = values["min-walls"]?.toDouble(),
                maxWallRatio = values["max-walls"]?.toDouble(),
                targetWordLength = values["target-length"]?.toDouble(),
            )
        }
    }
}
