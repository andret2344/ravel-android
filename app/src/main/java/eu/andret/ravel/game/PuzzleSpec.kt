package eu.andret.ravel.game

import eu.andret.ravel.generator.Difficulty
import eu.andret.ravel.generator.NO_RANK_LIMIT

/**
 * Everything about a puzzle that is not the player's own setting: how big it is, how hard, and
 * which seed builds it.
 *
 * All of it derived from a [PuzzleId] and none of it accepted from a caller. A seed picks one board
 * out of the generator's space *for a given size* - hand the same seed a 6x6 and a 9x9 and two
 * different boards come back - so a size chosen at the call site would mean an id no longer names
 * one board. Deriving it is what makes the id a name.
 *
 * Boards are square. A rectangle would make "size" two numbers, and two numbers cannot be recovered
 * from an id without inventing a rule about which is which; a square has one number and no rule.
 *
 * Language is deliberately absent. It is the player's setting, not the puzzle's: level 40 is the
 * same level in Polish and in English, the same size and the same difficulty, and the dictionary
 * behind it makes the boards differ without the identity differing.
 *
 * That is about a puzzle's *name* and not about the player's progress, which is the opposite way
 * round: the boards differ, so the ladders are walked separately and a player can be on level twenty
 * of one and level five of another. See [eu.andret.ravel.settings.LevelProgressState]. What this
 * paragraph promises is only that both of those level fives are the same size and difficulty.
 */
data class PuzzleSpec(
    val id: PuzzleId,
    val size: Int,
    val difficulty: Difficulty,
    val maxWordRank: Int,
) {
    val seed: Long get() = id.seed

    companion object {
        /** The spec for [id], the same on every device and every release. */
        fun of(id: PuzzleId): PuzzleSpec = PuzzleSpec(
            id = id,
            size = sizeFor(id),
            // The one axis still fixed per mode. Difficulty decides how much the paths are allowed
            // to wind, which is a curve someone will want to shape per mode later; the size no
            // longer competes with it for that job.
            difficulty = Difficulty.HARD,
            maxWordRank = rankFor(id),
        )

        /**
         * A size out of the range [id]'s mode allows at that point in its stream.
         *
         * Only [PuzzleMode.ENDLESS] narrows the range, because it is the only mode a player walks
         * through in order: level one is the first board anybody sees, and a 10x10 there is not a
         * hard first level so much as an unreadable one. Every other mode hands out one board at a
         * time with nothing before it to have learned from, so it draws from the whole range.
         */
        private fun sizeFor(id: PuzzleId): Int = when (id.mode) {
            PuzzleMode.ENDLESS -> sizeIn(id.seed, rampFor(id.ordinal))
            else -> sizeIn(id.seed, MIN_SIZE..MAX_SIZE)
        }

        /**
         * How deep into the frequency-ranked dictionary [id]'s words may reach.
         *
         * Ramped for the same reason as the size and for the same mode only, but **not** on the same
         * tiers, and the difference is the point. Size stops mattering early: by level twenty-six a
         * player knows what a 10x10 costs them, so there is nothing left to teach and the range
         * opens. Vocabulary never stops mattering, because it is not a skill the player acquires by
         * playing - either they know *drogowiec* or they do not, and no number of boards changes
         * that. So this curve is measured in thousands of levels where [rampFor] is measured in tens.
         *
         * The tiers are deliberately long and get longer, because the interesting part of the
         * dictionary is its head. Going from rank 1500 to 4000 is a bigger change in what a board
         * asks of a player than going from 25 000 to 30 000, so the early tiers are short and the
         * late ones are not. Past the last one the cap comes off and the board draws from everything
         * the dictionary holds - which is [Difficulty.HARD]'s own limit, not the whole word list.
         * The deepest entries are cut at load time rather than here, because they are cut for heap
         * as much as for taste; see [Difficulty].
         *
         * **This curve escalates to about level 2500 and then stops.** That is a property of the
         * dictionary, not a number to raise: 35 000 words is what fits in the heap, and the steps up
         * there are already thin - rank 25 000 is *czyściutki*, 30 000 is *rudzik*, 35 000 is
         * *płaszczka*, and a player is unlikely to feel the difference. Making levels past 2500
         * harder means a second axis, not a deeper cap. The path-shape dials are the obvious
         * candidate and are measured: `minTurnRatio` can reach 0.62 with `maxDirectness` 0.68 on a
         * 7x7 for about 470 ms, but the same band times out on a 9x9, so shape and size cannot both
         * be pushed on the same board.
         *
         * The dictionary behind these numbers is lemmas rather than inflected forms, so they buy
         * more than they look like they should: rank 4000 is *cierpienie* and *naprawa*, rank 10 000
         * is *kredens* and *ślubny*, and even the 25 000 mark is still ordinary Polish. See
         * `generator/DICTIONARY.md`, and the length histogram `buildDictionaryPl` prints - a cap is
         * only movable as far as the pools behind the *shortest* words allow, which is always the
         * binding constraint.
         *
         * Every mode but [PuzzleMode.ENDLESS] draws from the whole dictionary. A daily puzzle has no
         * ordinal to ramp along: it is one board, and the player who solves it today is not the same
         * player who started yesterday.
         */
        private fun rankFor(id: PuzzleId): Int = when (id.mode) {
            PuzzleMode.ENDLESS ->
                RANK_TIERS.firstOrNull { (lastLevel, _) -> id.ordinal <= lastLevel }?.second
                    ?: NO_RANK_LIMIT
            else -> NO_RANK_LIMIT
        }

        /**
         * How wide a board level [level] is allowed to be.
         *
         * Three tiers and then the whole range, which is a teaching curve and not a difficulty one:
         *
         * - the first five levels are as small as the game goes, because a player meeting the rules
         *   for the first time is reading a grid, not solving one.
         * - up to fifteen it opens to seven, which is still a grid that can be taken in at a glance.
         * - to twenty-five the floor drops back to five and the ceiling goes to eight, so the sizes
         *   start varying rather than only growing. A run of boards that each look bigger than the
         *   last reads as a difficulty slider; the point here is that size is not one.
         * - after that, everything. By level twenty-six the player has seen enough boards to know
         *   what a big one costs them, and a 10x10 is a change of pace rather than a wall.
         *
         * Sizes are still drawn from the seed inside the tier, so neighbouring levels do not march
         * upwards one square at a time - see [PuzzleId].
         */
        private fun rampFor(level: Long): IntRange = when {
            level <= FIRST_TIER -> MIN_SIZE..6
            level <= SECOND_TIER -> 6..7
            level <= THIRD_TIER -> MIN_SIZE..8
            else -> MIN_SIZE..MAX_SIZE
        }

        /**
         * A size in [range], taken from the seed's high bits.
         *
         * High bits because the whole seed goes on to drive the generator's own random stream, and
         * two things reading the same number should at least not read the same end of it. The seed
         * is a hash, so any slice of it is as evenly spread as any other.
         *
         * [MIN_SIZE]..[MAX_SIZE] is what stays playable at both ends. Below five there is no room
         * for a word to wind - the generator needs a path that crosses the grid - and above ten the
         * search runs into its own long tail: a 9x9 board has been measured at 1.3 seconds and a
         * 12x12 at 4.5, so ten is about as far as a player can be asked to wait between levels.
         */
        private fun sizeIn(seed: Long, range: IntRange): Int {
            val count = (range.last - range.first + 1).toLong()
            return range.first + ((seed ushr HIGH_BITS_SHIFT) % count).toInt()
        }

        const val MIN_SIZE = 5
        const val MAX_SIZE = 10

        /** The last level of each tier of [rampFor]. */
        const val FIRST_TIER = 5L
        const val SECOND_TIER = 15L
        const val THIRD_TIER = 25L

        /**
         * The vocabulary curve of [rankFor]: the last level of each tier, and the rank cap that
         * tier allows. Ordered, and read in order. A level past the last entry has no cap.
         *
         * A table rather than a chain of named constants because there are eight boundaries and
         * they are only meaningful next to each other - the shape of the curve is the thing being
         * decided, and nine `SEVENTH_RANK_TIER` constants hide it. Tests read this too, so moving a
         * tier means moving one number in one place.
         */
        val RANK_TIERS: List<Pair<Long, Int>> = listOf(
            15L to 1_500,
            40L to 4_000,
            100L to 8_000,
            250L to 12_000,
            500L to 16_000,
            900L to 20_000,
            1_500L to 25_000,
            2_500L to 30_000,
        )

        /** Leaves the top 16 bits, which `ushr` keeps non-negative without any sign fiddling. */
        private const val HIGH_BITS_SHIFT = 48
    }
}
