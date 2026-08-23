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
            difficulty = DIFFICULTY,
            maxWordRank = rankFor(id),
        )

        /**
         * The difficulty every board is built at.
         *
         * Named rather than left inside [of] because there is now a second caller, and it is one
         * that has no puzzle to ask about: dictionaries are cached per (language, difficulty), so
         * anything warming that cache before a board is wanted - see [Puzzles.preload] - has to name
         * the same difficulty [of] will, or it spends a second building a trie nothing will read.
         *
         * A single constant is honest only while difficulty is the one axis that does not vary. When
         * it becomes a curve per mode, which [of] above says somebody will want, this stops being a
         * constant and the preload has to be told which mode it is warming for.
         */
        val DIFFICULTY: Difficulty = Difficulty.HARD

        /**
         * A size out of the range [id]'s mode allows at that point in its stream.
         *
         * Two modes narrow the range, for reasons that are not the same reason.
         *
         * [PuzzleMode.ENDLESS] narrows it *along its ordinal*, because it is the only mode a player
         * walks through in order: level one is the first board anybody sees, and a 10x10 there is
         * not a hard first level so much as an unreadable one. That ramp ends, because the lesson
         * ends - see [rampFor].
         *
         * [PuzzleMode.DAILY] narrows it *flatly*, to [DAILY_MAX_SIZE], and never opens up. Nothing
         * about a date says the player has learned anything, so there is no ramp to walk; what
         * decides this is what the mode is for. A daily is the board a player fits into a coffee
         * break before deciding whether to come back tomorrow, and one day in six being a 10x10 -
         * which is what the full range meant - made a sixth of the calendar a board most people
         * would abandon. An abandoned daily is not a hard daily: it breaks the streak, and a broken
         * streak is the one thing that stops the player opening the app at all. So the ceiling comes
         * down to eight, which is the largest grid that still reads at a glance on a phone, and the
         * floor stays at [MIN_SIZE] so the sizes still vary rather than settling on one.
         *
         * **There is deliberately no `else` here**, and there was one until the unused `SPECIAL`
         * mode was removed - see [PuzzleMode]. Both modes are named, so the `when` is exhaustive and
         * a third mode does not compile until somebody decides what its sizes are. That is worth the
         * ceremony because of what the `else` branch actually did: it handed anything unnamed the
         * full [MIN_SIZE]..[MAX_SIZE] range, which is the widest spec in the game and precisely the
         * one the daily had to be rescued from. A new mode inheriting it silently is the same
         * mistake arriving a second time, with nothing to fail.
         */
        private fun sizeFor(id: PuzzleId): Int = when (id.mode) {
            PuzzleMode.ENDLESS -> sizeIn(id.seed, rampFor(id.ordinal))
            PuzzleMode.DAILY -> sizeIn(id.seed, MIN_SIZE..DAILY_MAX_SIZE)
        }

        /**
         * How deep into the frequency-ranked dictionary [id]'s words may reach.
         *
         * Ramped for the same reason as the size and for the same mode only, but **not** on the same
         * tiers, and the difference is the point. Size stops mattering first, because it runs out of
         * room: [MAX_SIZE] is the last width the generator will build in a time a player will wait,
         * so [SIZE_TIERS] has somewhere to arrive and arrives at level 250. Vocabulary never stops
         * mattering, because it is not a skill the player acquires by playing - either they know
         * *drogowiec* or they do not, and no number of boards changes that. So this curve is
         * measured in thousands of levels where [rampFor] is measured in hundreds.
         *
         * Three boundaries do fall on the same level in both tables - 15, 100 and 250 - which means
         * levels 16, 101 and 251 step on both axes at once. Left alone rather than nudged apart: a
         * board is not noticeably harder for the two arriving together, and moving a boundary to
         * avoid a coincidence would be letting the other table decide this one, which is the exact
         * coupling the separation is for.
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
         * [PuzzleMode.DAILY] has no ordinal to ramp along - a date is not a position on a curve, and
         * the player who solves today's board is not the same player who started yesterday - so it
         * takes one cap and keeps it: [DAILY_MAX_RANK]. Uncapped, which is what it used to be, made
         * the daily the single hardest board in the game and by some distance: it drew from all
         * 35 000 words on its first day, where a player has to reach level 2500 of the endless mode
         * before the tiers let go of 30 000. The mode meant to be a habit was running an end-game
         * vocabulary, and a board asking for a word the player has never met is not a board they
         * lose, it is one they close - which costs the streak and then the habit.
         *
         * Eight thousand rather than something rounder because that is where the dictionary stops
         * being ordinary: rank 4000 is *cierpienie* and *naprawa*, rank 10 000 is already *kredens*
         * and *ślubny*, so the cap sits just under the point where a length alone stops being enough
         * of a clue. It is also a band the generator is already known to handle at these sizes -
         * endless level 100 runs the same 8000 over the full size range - so this narrows what a
         * daily asks without putting the search into a regime nothing has measured.
         *
         * Exhaustive rather than defaulting, for the reason spelled out in [sizeFor], and the stakes
         * are higher on this axis: the `else` that used to sit here answered [NO_RANK_LIMIT], so a
         * mode added without a thought about vocabulary would draw from all 35 000 words on its
         * first board. That is the exact spec the daily was shipped with by accident and had to be
         * taken off.
         */
        private fun rankFor(id: PuzzleId): Int = when (id.mode) {
            PuzzleMode.ENDLESS ->
                RANK_TIERS.firstOrNull { (lastLevel, _) -> id.ordinal <= lastLevel }?.second
                    ?: NO_RANK_LIMIT
            PuzzleMode.DAILY -> DAILY_MAX_RANK
        }

        /**
         * How wide a board level [level] is allowed to be: the tier of [SIZE_TIERS] it falls in,
         * and the whole range once the table runs out.
         */
        private fun rampFor(level: Long): IntRange =
            SIZE_TIERS.firstOrNull { (lastLevel, _) -> level <= lastLevel }?.second
                ?: MIN_SIZE..MAX_SIZE

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

        /**
         * The daily's own ceiling, well inside [MAX_SIZE]. See [sizeFor] for why it is flat.
         *
         * Eight and not seven, measured against [SIZE_TIERS]: eight is where the endless ceiling
         * sits for its long middle, levels 51 to 125, so a daily is the size of a mid-game level.
         * Seven would have put it at level fifty, which says a daily is easier than the first fifty
         * levels of the game, and it is not meant to be easy - it is meant to be finishable in one
         * sitting.
         *
         * Note this is now a ceiling the endless mode passes rather than one it stops at: from level
         * 126 the endless ramp reaches nine and eventually [MAX_SIZE], and the daily never does.
         * That is the intended end state - a habit is not an end-game - but it does mean the daily
         * stops being the bigger board somewhere around level 126, so "the daily is a break from the
         * levels" is only true for a player who has not got that far.
         */
        const val DAILY_MAX_SIZE = 8

        /**
         * The daily's own vocabulary cap, flat for every date. See [rankFor] for the number.
         *
         * Deliberately a constant here rather than an entry in [RANK_TIERS]: the tiers are a curve
         * along an ordinal and the daily has no ordinal to walk. Folding it in would make it look
         * like a stage the daily eventually leaves.
         */
        const val DAILY_MAX_RANK = 8_000

        /**
         * The size curve of [rampFor]: the last level of each tier, and the range of grid widths
         * that tier draws from. Ordered, and read in order. A level past the last entry draws from
         * [MIN_SIZE]..[MAX_SIZE].
         *
         * A table for the same reason [RANK_TIERS] is one: there are eight boundaries and the shape
         * they make is the thing being decided, which nine named constants would hide. It is *not*
         * folded into [RANK_TIERS] - see there for why the two curves stay apart.
         *
         * **Every ceiling arrives twice, and the pair is the design.** A new width first appears in
         * a two-value tier with the floor at six, so it is a coin flip and the player meets it in
         * the first level or two; then the floor drops to five, the tier becomes three values, and
         * that same width goes back to being occasional. Introduce, then dilute. The alternative -
         * one tier per width, floor rising with the ceiling - is a staircase, and a run of boards
         * that each look bigger than the last reads as a difficulty slider. Size is not one: a 9x9
         * of common words is easier than a 6x6 of rare ones, and the curve must not teach otherwise.
         * That is also why the floor returns to five at the end rather than settling at six.
         *
         * The spans widen as they go - fifteen levels to introduce the first width, fifty to
         * introduce the last - because the earlier steps are the ones that teach something. Going
         * from a 6x6 to a 7x7 doubles what the player has to hold in their head at once; going from
         * a 9x9 to a 10x10 is more of the same reading they have already done sixty times.
         *
         * **The curve is over at 250 and there is nothing past it**, because [MAX_SIZE] is the last
         * width the generator will build in a time a player will wait - a 9x9 has been measured at
         * 1.3 s and a 12x12 at 4.5. Levels past 250 are made harder by the vocabulary curve alone,
         * which is why that one runs ten times further. See [rankFor].
         *
         * Sizes are still drawn from the seed inside the tier, so neighbouring levels do not march
         * upwards one square at a time - see [PuzzleId].
         */
        val SIZE_TIERS: List<Pair<Long, IntRange>> = listOf(
            15L to MIN_SIZE..6,
            25L to 6..7,
            50L to MIN_SIZE..7,
            100L to 6..8,
            125L to MIN_SIZE..8,
            150L to 6..9,
            200L to MIN_SIZE..9,
            250L to 6..MAX_SIZE,
        )

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
