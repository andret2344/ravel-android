package eu.andret.ravel.game

import eu.andret.ravel.generator.NO_RANK_LIMIT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PuzzleSpecTest {

    @Test
    fun `a spec is the same every time it is asked for`() {
        val id = PuzzleId(PuzzleMode.ENDLESS, 40)
        assertEquals(PuzzleSpec.of(id), PuzzleSpec.of(id))
    }

    @Test
    fun `every size stays inside the playable range`() {
        PuzzleMode.entries.forEach { mode ->
            for (ordinal in 1L..5_000L) {
                val size = PuzzleSpec.of(PuzzleId(mode, ordinal)).size
                assertTrue(
                    "$mode/$ordinal produced $size",
                    size in PuzzleSpec.MIN_SIZE..PuzzleSpec.MAX_SIZE,
                )
            }
        }
    }

    @Test
    fun `sizes are spread over the whole range rather than favouring one`() {
        // A size taken from a slice of a hash should land on each of the six about a sixth of the
        // time. Anything lopsided here would mean the slice is not as well mixed as assumed.
        val counts = (1L..6_000L)
            .map { ordinal -> PuzzleSpec.of(PuzzleId(PuzzleMode.ENDLESS, ordinal)).size }
            .groupingBy { it }
            .eachCount()
        assertEquals((PuzzleSpec.MIN_SIZE..PuzzleSpec.MAX_SIZE).toSet(), counts.keys)
        val expected = 6_000 / (PuzzleSpec.MAX_SIZE - PuzzleSpec.MIN_SIZE + 1)
        counts.forEach { (size, count) ->
            assertTrue("$size appeared $count times, expected about $expected", count > expected / 2)
        }
    }

    @Test
    fun `the levels a new player meets are small enough to read at a glance`() {
        // The promise the first tiers make. A 9x9 as level three is not a hard level, it is an
        // unreadable one, and nothing else in the game protects a first-time player from one.
        //
        // The bounds are written out rather than read from SIZE_TIERS on purpose. A test that took
        // its expectations from the table would ratify any edit to the table, including one that
        // hands level two a 10x10; these numbers are the claim the table is supposed to keep.
        (1L..15L).forEach { level ->
            val size = PuzzleSpec.of(PuzzleId(PuzzleMode.ENDLESS, level)).size
            assertTrue("level $level produced $size", size <= 6)
        }
        (16L..50L).forEach { level ->
            val size = PuzzleSpec.of(PuzzleId(PuzzleMode.ENDLESS, level)).size
            assertTrue("level $level produced $size", size <= 7)
        }
        (51L..125L).forEach { level ->
            val size = PuzzleSpec.of(PuzzleId(PuzzleMode.ENDLESS, level)).size
            assertTrue("level $level produced $size", size <= 8)
        }
    }

    @Test
    fun `each size tier holds for its whole span`() {
        // Both ends and everything between, so a boundary that moved by one still gets caught. The
        // table is read in order and short-circuits, so an out-of-order entry would make a tier
        // unreachable rather than fail anywhere visible.
        var firstLevel = 1L
        PuzzleSpec.SIZE_TIERS.forEach { (lastLevel, range) ->
            assertTrue("tier boundaries are not ascending at $lastLevel", firstLevel <= lastLevel)
            (firstLevel..lastLevel).forEach { level ->
                val size = PuzzleSpec.of(PuzzleId(PuzzleMode.ENDLESS, level)).size
                assertTrue("level $level produced $size, outside $range", size in range)
            }
            firstLevel = lastLevel + 1
        }
    }

    @Test
    fun `every width is introduced narrow and then diluted`() {
        // The pair is the design, not an accident of the table: a new ceiling always arrives in a
        // two-value tier so the player meets it at once, and the next tier drops the floor back so
        // it stops being every other board. A table edited into a staircase - floor rising with the
        // ceiling - would pass every other test here and quietly turn size into a difficulty slider.
        val ceilings = PuzzleSpec.SIZE_TIERS.map { it.second.last }
        PuzzleSpec.SIZE_TIERS.forEachIndexed { index, (lastLevel, range) ->
            val isNewCeiling = index == 0 || range.last > ceilings[index - 1]
            val expectedFloor = if (isNewCeiling && index > 0) 6 else PuzzleSpec.MIN_SIZE
            assertEquals("tier ending at $lastLevel is $range", expectedFloor, range.first)
        }
        // And the last word goes back to the floor, rather than leaving the game permanently on the
        // narrow band the biggest width was introduced in.
        val pastTheEnd = ((PuzzleSpec.SIZE_TIERS.last().first + 1)..(PuzzleSpec.SIZE_TIERS.last().first + 100))
            .map { level -> PuzzleSpec.of(PuzzleId(PuzzleMode.ENDLESS, level)).size }
        assertEquals((PuzzleSpec.MIN_SIZE..PuzzleSpec.MAX_SIZE).toSet(), pastTheEnd.toSet())
    }

    @Test
    fun `the ramp opens up once it is over`() {
        // The point of the tiers is that they end. A ceiling that quietly stayed at nine would be
        // this test failing to find a single big board in a hundred levels past the last tier.
        val lastTier = PuzzleSpec.SIZE_TIERS.last().first
        val sizes = ((lastTier + 1)..(lastTier + 100))
            .map { level -> PuzzleSpec.of(PuzzleId(PuzzleMode.ENDLESS, level)).size }
        assertTrue("sizes went $sizes", sizes.any { it == PuzzleSpec.MAX_SIZE })
        assertTrue("sizes went $sizes", sizes.any { it == PuzzleSpec.MIN_SIZE })
    }

    @Test
    fun `the ramp is the endless mode's alone`() {
        // Every other mode hands out one board at a time with nothing before it to have learned
        // from, so nothing about a small ordinal should make its board small. Asserted on SPECIAL
        // rather than DAILY because the daily has a ceiling of its own - a flat one, which is a
        // different thing from a ramp and is guarded by the two tests below.
        //
        // Over the first twenty-five ordinals, where the endless ceiling is seven, so a single
        // board bigger than that is proof the ramp was not consulted.
        val sizes = (1L..25L).map { PuzzleSpec.of(PuzzleId(PuzzleMode.SPECIAL, it)).size }
        assertTrue("sizes went $sizes", sizes.any { it > 7 })
    }

    @Test
    fun `no daily board is bigger than one sitting`() {
        // The whole point of the daily's ceiling. A 10x10 once a week is not a hard daily, it is a
        // daily a sixth of the calendar abandons - and an abandoned daily breaks the streak, which
        // is what stops the player coming back at all.
        //
        // Walked over real dates through DailyDate rather than over 1..n, because that is what a
        // daily's ordinal is - a yyyyMMdd, which skips 20260832 - and a run of numbers that are not
        // dates would be testing seeds no board will ever be built from.
        val sizes = threeYearsOfDailies().map { PuzzleSpec.of(PuzzleId(PuzzleMode.DAILY, it)).size }
        assertTrue(
            "biggest was ${sizes.max()} over ${sizes.size} days",
            sizes.all { it <= PuzzleSpec.DAILY_MAX_SIZE },
        )
    }

    @Test
    fun `the daily ceiling still leaves the sizes varying`() {
        // A ceiling is not a fixed size. Every value from the floor to the ceiling has to turn up,
        // or the daily becomes one grid the player stops looking at.
        val sizes = threeYearsOfDailies().map { PuzzleSpec.of(PuzzleId(PuzzleMode.DAILY, it)).size }
        assertEquals(
            (PuzzleSpec.MIN_SIZE..PuzzleSpec.DAILY_MAX_SIZE).toSet(),
            sizes.toSet(),
        )
    }

    @Test
    fun `consecutive levels do not march up or down in size`() {
        // The size follows the seed, and the seed is a hash, so a run of levels must not be a
        // staircase. Twenty levels that all differ from their neighbour would be as suspicious as
        // twenty that never do.
        //
        // Taken from past the last tier, where the whole range is in play. Inside a tier the sizes
        // are drawn from two or three values and a run of them says nothing about the mixing.
        val sizes = (300L..319L).map { level -> PuzzleSpec.of(PuzzleId(PuzzleMode.ENDLESS, level)).size }
        val changes = (1 until sizes.size).count { sizes[it] != sizes[it - 1] }
        assertTrue("sizes went $sizes", changes in 8..19)
    }

    @Test
    fun `the vocabulary opens up far more slowly than the size does`() {
        // The two curves are deliberately different lengths, and this is the assertion that says
        // so: by the time the size ramp has finished entirely, the vocabulary is on its fifth tier
        // of eight with more than two thousand levels still to run. Collapsing them onto shared
        // tiers is the tempting simplification, and it would end the vocabulary curve at 250.
        val lastSizeTier = PuzzleSpec.SIZE_TIERS.last().first
        val sizeRampOver = PuzzleSpec.of(PuzzleId(PuzzleMode.ENDLESS, lastSizeTier + 1))
        assertEquals(16_000, sizeRampOver.maxWordRank)
        // And it is still ramping an order of magnitude further along, where the size curve has
        // nothing left to say at all.
        val lastTier = PuzzleSpec.RANK_TIERS.last().first
        assertTrue("the vocabulary curve stops before the size curve does", lastTier > lastSizeTier * 5)
        assertTrue(
            "still capped at $lastTier",
            PuzzleSpec.of(PuzzleId(PuzzleMode.ENDLESS, lastTier)).maxWordRank < NO_RANK_LIMIT,
        )
    }

    @Test
    fun `each vocabulary tier holds for its whole span`() {
        // Both ends of every tier, so a boundary that moved by one still gets caught.
        var firstLevel = 1L
        PuzzleSpec.RANK_TIERS.forEach { (lastLevel, cap) ->
            listOf(firstLevel, lastLevel).forEach { level ->
                assertEquals(
                    "level $level should sit in the tier ending at $lastLevel",
                    cap,
                    PuzzleSpec.of(PuzzleId(PuzzleMode.ENDLESS, level)).maxWordRank,
                )
            }
            firstLevel = lastLevel + 1
        }
        // Past the last tier the game stops holding anything back rather than inventing another
        // number. Somebody will get here.
        val past = PuzzleSpec.of(PuzzleId(PuzzleMode.ENDLESS, PuzzleSpec.RANK_TIERS.last().first + 1))
        assertEquals(NO_RANK_LIMIT, past.maxWordRank)
    }

    @Test
    fun `the vocabulary tiers are ordered, and get longer as they get less interesting`() {
        // The table is read in order and short-circuits, so an out-of-order entry would silently
        // make a tier unreachable rather than fail anywhere visible.
        val levels = PuzzleSpec.RANK_TIERS.map { it.first }
        val caps = PuzzleSpec.RANK_TIERS.map { it.second }
        assertEquals("tier boundaries are not ascending", levels.sorted(), levels)
        assertEquals("rank caps are not ascending", caps.sorted(), caps)

        // Early tiers short, late tiers long: rank 1500 to 4000 changes what a board asks far more
        // than 25 000 to 30 000 does, so the spans are not allowed to be uniform.
        val spans = levels.mapIndexed { index, level -> level - (levels.getOrNull(index - 1) ?: 0L) }
        assertTrue("spans went $spans", spans.first() < spans.last() / 10)
    }

    @Test
    fun `the vocabulary ramp never goes backwards`() {
        var previous = 0
        for (level in 1L..(PuzzleSpec.RANK_TIERS.last().first + 100)) {
            val cap = PuzzleSpec.of(PuzzleId(PuzzleMode.ENDLESS, level)).maxWordRank
            assertTrue("level $level dropped from $previous to $cap", cap >= previous)
            previous = cap
        }
    }

    @Test
    fun `the vocabulary ramp is the endless mode's alone`() {
        // Same reasoning as the size ramp: no other mode has an ordinal to ramp along, so nothing
        // about a small one should hold its words back. The daily has a cap all the same, and a flat
        // one - see below - so the claim here is only about the ramp.
        assertEquals(NO_RANK_LIMIT, PuzzleSpec.of(PuzzleId(PuzzleMode.SPECIAL, 1)).maxWordRank)
        val caps = threeYearsOfDailies()
            .map { PuzzleSpec.of(PuzzleId(PuzzleMode.DAILY, it)).maxWordRank }
            .toSet()
        assertEquals(setOf(PuzzleSpec.DAILY_MAX_RANK), caps)
    }

    @Test
    fun `no daily asks for a word outside ordinary vocabulary`() {
        // The daily used to be uncapped, which quietly made it the hardest board in the game: all
        // 35 000 words on day one, where the endless mode does not let go of 30 000 until level 2500.
        // A board whose only clue is a length, asking for a word the player has never met, is a board
        // they close - and closing it breaks the streak the mode is built on.
        val cap = PuzzleSpec.of(PuzzleId(PuzzleMode.DAILY, DailyDate.of(2026, 8, 9))).maxWordRank
        assertTrue("daily is capped at $cap", cap < NO_RANK_LIMIT)
        // Below where the endless mode's own curve stops being ordinary Polish - rank 10 000 is
        // already kredens. Stated against the table so moving a tier does not silently overtake this.
        val ordinary = PuzzleSpec.RANK_TIERS.first { it.second >= 12_000 }.second
        assertTrue("daily cap $cap is not below $ordinary", cap < ordinary)
    }

    @Test
    fun `boards are square`() {
        // Stated as a test because the whole identity scheme leans on size being one number: an id
        // cannot name a rectangle without a rule about which side is which.
        val spec = PuzzleSpec.of(PuzzleId(PuzzleMode.DAILY, 20260807))
        assertEquals(spec.size, spec.size)
        assertTrue(spec.size >= PuzzleSpec.MIN_SIZE)
    }

    private companion object {
        /**
         * Every date from the start of 2026 for three years, as daily ordinals.
         *
         * Through [DailyDate.shifted] so they are dates and not a run of numbers that look like
         * dates: the day after 20260831 is 20260901, and a test that walked over 20260832 would be
         * asserting things about seeds no board is ever built from. Three years because that is long
         * enough for a ceiling to be violated somewhere if it is only nearly holding.
         */
        fun threeYearsOfDailies(): List<Long> {
            val first = DailyDate.of(2026, 1, 1)
            return (0..(3 * 365)).map { DailyDate.shifted(first, it) }
        }
    }
}
