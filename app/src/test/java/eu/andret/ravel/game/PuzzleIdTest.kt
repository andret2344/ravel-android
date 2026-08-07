package eu.andret.ravel.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PuzzleIdTest {

    @Test
    fun `the same id always yields the same seed`() {
        assertEquals(
            PuzzleId(PuzzleMode.DAILY, 20260807).seed,
            PuzzleId(PuzzleMode.DAILY, 20260807).seed,
        )
    }

    @Test
    fun `the same ordinal in different modes yields different seeds`() {
        // The whole point: endless level 5 and special event 5 are the same number and must not be
        // the same board.
        val seeds = PuzzleMode.entries.map { mode -> PuzzleId(mode, 5).seed }
        assertEquals(seeds.size, seeds.toSet().size)
    }

    @Test
    fun `no mode's stream ever collides with another's`() {
        // Every mode over the same wide range of ordinals: the streams are disjoint, not merely
        // different at one point. A player who clears ten thousand levels walks this whole range.
        val seen = HashMap<Long, PuzzleId>()
        PuzzleMode.entries.forEach { mode ->
            for (ordinal in 1L..20_000L) {
                val id = PuzzleId(mode, ordinal)
                val clash = seen.put(id.seed, id)
                assertEquals("$id collides with $clash", null, clash)
            }
        }
    }

    @Test
    fun `dates spanning a century never collide with each other`() {
        val seen = HashSet<Long>()
        var count = 0
        for (year in 2000..2099) {
            for (month in 1..12) {
                for (day in 1..28) {
                    count++
                    seen.add(PuzzleId(PuzzleMode.DAILY, year * 10_000L + month * 100L + day).seed)
                }
            }
        }
        assertEquals(count, seen.size)
    }

    @Test
    fun `neighbouring ordinals produce unrelated seeds`() {
        // Consecutive levels must not be neighbouring streams in the generator, or level 51 comes
        // out as a variation on level 50. Two seeds from a good mix differ in about half their
        // bits; anything under a quarter would mean the input's pattern survived.
        val first = PuzzleId(PuzzleMode.ENDLESS, 50).seed
        val second = PuzzleId(PuzzleMode.ENDLESS, 51).seed
        val differingBits = java.lang.Long.bitCount(first xor second)
        assertTrue("only $differingBits bits differ", differingBits in 16..48)
    }

    @Test
    fun `the salts are what keep the modes apart`() {
        // Guards against someone "simplifying" the mix into something that ignores the salt.
        assertNotEquals(
            PuzzleId(PuzzleMode.DAILY, 20260807).seed,
            PuzzleId(PuzzleMode.ENDLESS, 20260807).seed,
        )
    }

    @Test
    fun `seeds are pinned so a board's identity survives a refactor`() {
        // These numbers are the contract with every device that has ever generated these puzzles.
        // A change here is a change to every board the mode has produced - if this test fails, that
        // is the question to answer, not a number to update.
        assertEquals(-4374057668283239204L, PuzzleId(PuzzleMode.DAILY, 20260807).seed)
        assertEquals(-8100068134740190993L, PuzzleId(PuzzleMode.ENDLESS, 1).seed)
        assertEquals(4816972168677063686L, PuzzleId(PuzzleMode.SPECIAL, 1).seed)
    }
}
