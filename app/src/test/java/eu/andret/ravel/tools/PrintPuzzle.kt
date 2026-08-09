package eu.andret.ravel.tools

import eu.andret.ravel.game.DailyDate
import eu.andret.ravel.game.PuzzleId
import eu.andret.ravel.game.PuzzleMode
import eu.andret.ravel.game.PuzzleSpec
import eu.andret.ravel.generator.Language
import eu.andret.ravel.generator.Ravel
import kotlin.system.exitProcess

/**
 * Prints the board a level or a date actually gets, from a terminal.
 *
 * Usage: `./gradlew :app:printPuzzle --args="--level 12"`
 *
 *     --level N            the main mode's level N.
 *     --daily YYYYMMDD     one day's board; "today" for today's.
 *     --language pl|en     which dictionary, Polish by default, as in the game.
 *     --quiet              board only, no solution.
 *
 * This is not `:generator:run` with a seed typed in. That command takes a width and a seed and
 * hands back *a* board; this one asks the same question the game asks - "what is level twelve" -
 * and goes through [PuzzleSpec], so the size, the difficulty and the seed are the ones the player
 * will see. A seed copied by hand would answer a different question every time the ramp changed.
 *
 * It lives in the test source set because that is the one part of the app that is never packaged,
 * and a developer tool has no business inside an APK. Everything it touches - the ids, the ramp,
 * the generator - is plain Kotlin, so a desktop JVM runs it exactly as the unit tests run.
 */
fun main(args: Array<String>) {
    val options = try {
        Options.parse(args)
    } catch (failure: IllegalArgumentException) {
        System.err.println(failure.message)
        System.err.println(USAGE)
        exitProcess(2)
    }

    val spec = PuzzleSpec.of(options.id)
    println(
        "${options.name} · ${options.id.mode.name.lowercase()} · ${spec.size}x${spec.size} · " +
            "${spec.difficulty.name.lowercase()} · rank ≤${spec.maxWordRank} · " +
            "${options.language.code} · seed ${spec.seed}"
    )

    val startedAt = System.nanoTime()
    val board = Ravel.board(
        width = spec.size,
        height = spec.size,
        language = options.language,
        difficulty = spec.difficulty,
        seed = spec.seed,
        maxWordRank = spec.maxWordRank,
    )
    val millis = (System.nanoTime() - startedAt) / 1_000_000

    println("${board.wordLengths.size} words, lengths ${board.wordLengths}, built in $millis ms")
    println()
    print(board.render())
    if (options.quiet) return
    println()
    println("-- solution --")
    print(board.renderSolution())
    println(board.solution.joinToString(", ") { it.word })
}

private const val USAGE =
    "Usage: --level N | --daily YYYYMMDD|today [--language pl|en] [--quiet]"

private class Options(
    val id: PuzzleId,
    val name: String,
    val language: Language,
    val quiet: Boolean,
) {
    companion object {
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
                require(name in KNOWN) { "Unknown option '--$name'" }
                values[name] = args[index + 1]
                index += 2
            }

            val level = values["level"]?.toLongOrNull()
            val daily = values["daily"]
            // One or the other, never both: they are two different streams of boards, and a command
            // given both would have to pick one and ignore the other in silence.
            require((level == null) != (daily == null)) { "Give exactly one of --level or --daily" }

            val id = when {
                level != null -> {
                    require(level >= 1) { "Levels start at 1" }
                    PuzzleId(PuzzleMode.ENDLESS, level)
                }

                else -> PuzzleId(PuzzleMode.DAILY, ordinalOf(daily!!))
            }
            return Options(
                id = id,
                name = level?.let { "Level $it" } ?: "Day ${id.ordinal}",
                language = values["language"]?.let(Language::of) ?: Language.POLISH,
                quiet = quiet,
            )
        }

        /** A date as the daily mode names it, or today's, which is the one worth typing a word for. */
        private fun ordinalOf(value: String): Long = when (value) {
            "today" -> DailyDate.today()
            else -> requireNotNull(value.toLongOrNull()?.takeIf { it > 10_000_000 }) {
                "A day is 'today' or a date as yyyyMMdd, not '$value'"
            }
        }

        private val KNOWN = setOf("level", "daily", "language")
    }
}
