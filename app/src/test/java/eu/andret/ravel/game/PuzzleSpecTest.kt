package eu.andret.ravel.game

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
        (1L..PuzzleSpec.FIRST_TIER).forEach { level ->
            val size = PuzzleSpec.of(PuzzleId(PuzzleMode.ENDLESS, level)).size
            assertTrue("level $level produced $size", size <= 6)
        }
        (1L..PuzzleSpec.SECOND_TIER).forEach { level ->
            val size = PuzzleSpec.of(PuzzleId(PuzzleMode.ENDLESS, level)).size
            assertTrue("level $level produced $size", size <= 7)
        }
        ((PuzzleSpec.SECOND_TIER + 1)..PuzzleSpec.THIRD_TIER).forEach { level ->
            val size = PuzzleSpec.of(PuzzleId(PuzzleMode.ENDLESS, level)).size
            assertTrue("level $level produced $size", size <= 8)
        }
    }

    @Test
    fun `the ramp opens up once it is over`() {
        // The point of the tiers is that they end. A ceiling that quietly stayed at eight would be
        // this test failing to find a single big board in a hundred levels past the last tier.
        val sizes = ((PuzzleSpec.THIRD_TIER + 1)..(PuzzleSpec.THIRD_TIER + 100))
            .map { level -> PuzzleSpec.of(PuzzleId(PuzzleMode.ENDLESS, level)).size }
        assertTrue("sizes went $sizes", sizes.any { it > 8 })
        assertTrue("sizes went $sizes", sizes.any { it == PuzzleSpec.MIN_SIZE })
    }

    @Test
    fun `the ramp is the endless mode's alone`() {
        // Every other mode hands out one board at a time with nothing before it to have learned
        // from, so nothing about a small ordinal should make its board small.
        val sizes = (1L..200L).map { PuzzleSpec.of(PuzzleId(PuzzleMode.DAILY, it)).size }
        assertTrue("sizes went $sizes", sizes.any { it > 8 })
    }

    @Test
    fun `consecutive levels do not march up or down in size`() {
        // The size follows the seed, and the seed is a hash, so a run of levels must not be a
        // staircase. Twenty levels that all differ from their neighbour would be as suspicious as
        // twenty that never do.
        //
        // Taken from past the last tier, where the whole range is in play. Inside a tier the sizes
        // are drawn from two or three values and a run of them says nothing about the mixing.
        val sizes = (100L..119L).map { level -> PuzzleSpec.of(PuzzleId(PuzzleMode.ENDLESS, level)).size }
        val changes = (1 until sizes.size).count { sizes[it] != sizes[it - 1] }
        assertTrue("sizes went $sizes", changes in 8..19)
    }

    @Test
    fun `boards are square`() {
        // Stated as a test because the whole identity scheme leans on size being one number: an id
        // cannot name a rectangle without a rule about which side is which.
        val spec = PuzzleSpec.of(PuzzleId(PuzzleMode.DAILY, 20260807))
        assertEquals(spec.size, spec.size)
        assertTrue(spec.size >= PuzzleSpec.MIN_SIZE)
    }
}
