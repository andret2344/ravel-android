package eu.andret.ravel.tools

import eu.andret.ravel.game.PuzzleId
import eu.andret.ravel.game.PuzzleMode
import eu.andret.ravel.game.PuzzleSpec
import eu.andret.ravel.generator.BoardGenerator
import eu.andret.ravel.generator.BoardSpec
import eu.andret.ravel.generator.Dictionary
import eu.andret.ravel.generator.Difficulty
import eu.andret.ravel.generator.GenerationLimits
import eu.andret.ravel.generator.GenerationStats
import eu.andret.ravel.generator.Language
import kotlin.random.Random
import kotlin.system.exitProcess

/**
 * Times every level of the ladder, so the shape of the generator's cost is a table rather than a
 * guess.
 *
 * Usage: `./gradlew :app:sweepLevels --args="--to 300" > sweep.csv`
 *
 *     --from N             first level, 1 by default.
 *     --to N               last level, 300 by default.
 *     --levels a,b,c       an explicit list instead of a range.
 *     --language pl|en     which dictionary, Polish by default, as in the game.
 *     --timeout MS         give up on one level after this long; 20000, as [eu.andret.ravel.game.Puzzles].
 *     --repeat N           runs per level, 1 by default. See below - this measures the JVM, not the board.
 *     --warmup N           unrecorded runs before the sweep, 3 by default.
 *     --slow MS            what counts as too slow in the summary, 2000 by default.
 *
 * ## Why this exists
 *
 * The question it answers is not "how long does a board take" but "which levels are the expensive
 * ones, and by how much". Those cannot be sampled at the tier boundaries, because a level's size is
 * drawn from its seed *inside* the tier its ordinal falls in - see [PuzzleSpec] - so level 201 may
 * be a 6x6 and level 237 a 10x10. A tier is not represented by any one of its levels, only by all
 * of them.
 *
 * ## Why it goes under `Ravel`
 *
 * [eu.andret.ravel.generator.Ravel] builds a [GenerationStats], passes it in and throws it away, so
 * a caller gets a board and a stopwatch and nothing else. The stopwatch alone cannot tell a slow
 * board from a slow *phone*: twenty seconds spent proving uniqueness and twenty seconds spent
 * failing to lay out a shape have different cures, and only the breakdown separates them.
 *
 * The cost is that the four lines building the generator here mirror `Ravel.generate`, and a
 * divergence would make this tool measure something the game does not run. They are kept in the
 * same order and with the same arguments deliberately; `Ravel.generate` is the file to check against
 * when a number here stops matching the game.
 *
 * ## Why `--repeat` does not measure the board
 *
 * Generation is deterministic in its seed, so running a level twice runs the identical search over
 * the identical sequence of layout attempts. Every count in [GenerationStats] comes back the same;
 * only the milliseconds move, and they move because of the JIT and the machine rather than because
 * of the board. Repeats are for separating those two, which is why the table reports a median and a
 * spread rather than one number.
 *
 * ## Output
 *
 * CSV on stdout, summary on stderr - so `> sweep.csv` keeps the table clean and still prints the
 * summary to the terminal.
 */
fun main(args: Array<String>) {
    val options = try {
        Sweep.parse(args)
    } catch (failure: IllegalArgumentException) {
        System.err.println(failure.message)
        System.err.println(SWEEP_USAGE)
        exitProcess(2)
    }

    // Timed and reported on its own, because it is a cost the game pays once per language and the
    // player pays it in front of the very first board. On a desktop it disappears into the noise;
    // the number is here so the device measurement has something to be a multiple of.
    val loadStartedAt = System.nanoTime()
    val dictionary = Dictionary.loadBundled(options.language, Difficulty.HARD)
    val loadMillis = (System.nanoTime() - loadStartedAt) / 1_000_000

    // The first levels of a cold sweep pay for the whole generator's JIT compilation, which would
    // otherwise be reported as those levels being expensive. Unrecorded, and always level one, so
    // the warm-up is the same work whatever range is being swept.
    repeat(options.warmup) { runLevel(1L, options.language, dictionary, options.timeoutMillis) }

    println(CSV_HEADER)
    val rows = options.levels.map { level ->
        val runs = (1..options.repeat).map {
            runLevel(level, options.language, dictionary, options.timeoutMillis)
        }
        // The last run rather than the first: its counts are identical to every other run's, and its
        // milliseconds are the ones least polluted by compilation still happening underneath.
        Row(level = level, runs = runs, last = runs.last()).also { println(it.toCsv()) }
    }

    report(options, loadMillis, rows)
}

private const val SWEEP_USAGE =
    "Usage: [--from N] [--to N] [--levels a,b,c] [--language pl|en] [--timeout MS] " +
        "[--repeat N] [--warmup N] [--slow MS]"

private const val CSV_HEADER =
    "level,size,rank,seed,ok,millis,minMillis,maxMillis,words,layouts,unshapeable," +
        "tooStraight,assignments,uniquenessChecks,layoutMillis,uniquenessMillis"

/** One level's run: what the spec asked for, and what the generator did about it. */
private class Run(
    val size: Int,
    val rank: Int,
    val seed: Long,
    val found: Boolean,
    val words: Int,
    val millis: Long,
    val stats: GenerationStats,
)

/** Every run of one level, reduced to the row that goes in the table. */
private class Row(val level: Long, runs: List<Run>, val last: Run) {
    private val times = runs.map(Run::millis).sorted()

    val millis: Long = times[times.size / 2]
    val minMillis: Long = times.first()
    val maxMillis: Long = times.last()

    fun toCsv(): String = listOf(
        level, last.size, last.rank, last.seed, last.found, millis, minMillis, maxMillis,
        last.words, last.stats.layouts, last.stats.layoutsUnshapeable, last.stats.layoutsTooStraight,
        last.stats.wordAssignments, last.stats.uniquenessChecks, last.stats.layoutMillis,
        last.stats.uniquenessMillis,
    ).joinToString(",")
}

/**
 * Generates one level the way the game would, and says what it cost.
 *
 * The deadline is passed rather than left off so a level nothing can build does not stop the sweep:
 * [GenerationLimits] answers null, the row records the failure, and the next level starts. A
 * generator left unbounded here would hang the whole table on the first hopeless seed.
 */
private fun runLevel(
    level: Long,
    language: Language,
    dictionary: Dictionary,
    timeoutMillis: Long,
): Run {
    val spec = PuzzleSpec.of(PuzzleId(PuzzleMode.ENDLESS, level))
    val boardSpec = BoardSpec.forDifficulty(spec.size, spec.size, spec.difficulty, spec.maxWordRank)
    val generator = BoardGenerator(dictionary, Random(spec.seed))
    val stats = GenerationStats()

    val startedAt = System.nanoTime()
    val board = generator.generate(boardSpec, stats, GenerationLimits(timeoutMillis = timeoutMillis))
    val millis = (System.nanoTime() - startedAt) / 1_000_000

    return Run(
        size = spec.size,
        rank = spec.maxWordRank,
        seed = spec.seed,
        found = board != null,
        words = board?.solution?.size ?: 0,
        millis = millis,
        stats = stats,
    )
}

/**
 * The part a person reads: what is slow, what failed, and how the cost is distributed per grid size.
 *
 * Per size rather than per level, because size is the dial the ramp actually turns and the one a
 * finding has to be expressed in - "10x10 has a p90 of four seconds" is something [PuzzleSpec] can
 * act on, where "level 237 took four seconds" is not.
 */
private fun report(options: Sweep, loadMillis: Long, rows: List<Row>) {
    val out = System.err
    out.println()
    out.println("-- sweep --")
    out.println(
        "${rows.size} levels, ${options.language.code}, timeout ${options.timeoutMillis} ms, " +
            "${options.repeat} run(s) each"
    )
    out.println("dictionary load: $loadMillis ms")

    out.println()
    out.println("size   n   median     p90     max   failed")
    rows.groupBy { it.last.size }.toSortedMap().forEach { (size, group) ->
        val times = group.map(Row::millis).sorted()
        out.println(
            "%2dx%-2d %3d %7d %7d %7d %8d".format(
                size, size, group.size,
                times[times.size / 2],
                times[(times.size * 9 / 10).coerceAtMost(times.size - 1)],
                times.last(),
                group.count { !it.last.found },
            )
        )
    }

    val slow = rows.filter { it.millis >= options.slowMillis }
    val failed = rows.filter { !it.last.found }
    out.println()
    out.println("over ${options.slowMillis} ms: ${slow.size} of ${rows.size}")
    out.println("failed within ${options.timeoutMillis} ms: ${failed.size} of ${rows.size}")
    if (failed.isNotEmpty()) {
        out.println("  levels: ${failed.joinToString(", ") { "${it.level}" }}")
    }

    out.println()
    out.println("worst ${WORST_SHOWN}:")
    rows.sortedByDescending(Row::millis).take(WORST_SHOWN).forEach { row ->
        out.println(
            "  level %5d  %2dx%-2d  rank %6d  %6d ms  (layout %d, uniqueness %d, %d layouts)".format(
                row.level, row.last.size, row.last.size, row.last.rank, row.millis,
                row.last.stats.layoutMillis, row.last.stats.uniquenessMillis, row.last.stats.layouts,
            )
        )
    }
}

/** Enough of a tail to see whether the slow levels are a cluster or a handful of outliers. */
private const val WORST_SHOWN = 15

private class Sweep(
    val levels: List<Long>,
    val language: Language,
    val timeoutMillis: Long,
    val repeat: Int,
    val warmup: Int,
    val slowMillis: Long,
) {
    companion object {
        fun parse(args: Array<String>): Sweep {
            val values = HashMap<String, String>()
            var index = 0
            while (index < args.size) {
                val argument = args[index]
                require(argument.startsWith("--")) { "Unexpected argument '$argument'" }
                require(index + 1 < args.size) { "Missing value for $argument" }
                val name = argument.removePrefix("--")
                require(name in KNOWN) { "Unknown option '--$name'" }
                values[name] = args[index + 1]
                index += 2
            }

            val explicit = values["levels"]?.split(",")?.map { piece ->
                requireNotNull(piece.trim().toLongOrNull()?.takeIf { it >= 1 }) {
                    "Not a level: '$piece'"
                }
            }
            val from = values["from"]?.toLongOrNull() ?: 1L
            val to = values["to"]?.toLongOrNull() ?: DEFAULT_TO
            require(from >= 1) { "Levels start at 1" }
            require(to >= from) { "--to must not be below --from" }

            return Sweep(
                levels = explicit ?: (from..to).toList(),
                language = values["language"]?.let(Language::of) ?: Language.POLISH,
                timeoutMillis = values["timeout"]?.toLongOrNull() ?: DEFAULT_TIMEOUT_MILLIS,
                repeat = values["repeat"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                warmup = values["warmup"]?.toIntOrNull()?.coerceAtLeast(0) ?: DEFAULT_WARMUP,
                slowMillis = values["slow"]?.toLongOrNull() ?: DEFAULT_SLOW_MILLIS,
            )
        }

        /** Where [PuzzleSpec.SIZE_TIERS] arrives, plus enough beyond it to see the plateau. */
        private const val DEFAULT_TO = 300L

        /** The same deadline the game gives a board, so a failure here is a failure there. */
        private const val DEFAULT_TIMEOUT_MILLIS = 20_000L

        /**
         * Not the deadline: a board found in eighteen seconds is a board the player left. The
         * summary counts against what a person will sit through, and the deadline separately.
         */
        private const val DEFAULT_SLOW_MILLIS = 2_000L

        private const val DEFAULT_WARMUP = 3

        private val KNOWN =
            setOf("from", "to", "levels", "language", "timeout", "repeat", "warmup", "slow")
    }
}
