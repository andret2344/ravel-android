package eu.andret.ravel.game

import eu.andret.ravel.generator.Difficulty

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
) {
    val seed: Long get() = id.seed

    companion object {
        /** The spec for [id], the same on every device and every release. */
        fun of(id: PuzzleId): PuzzleSpec = PuzzleSpec(
            id = id,
            size = sizeFor(id),
            // The one axis still fixed per mode. Difficulty decides how obscure the words may be
            // and how much the paths are allowed to wind, which is a curve someone will want to
            // shape per mode later; the size no longer competes with it for that job.
            difficulty = Difficulty.HARD,
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

        /** Leaves the top 16 bits, which `ushr` keeps non-negative without any sign fiddling. */
        private const val HIGH_BITS_SHIFT = 48
    }
}
