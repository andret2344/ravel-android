package eu.andret.ravel.generator

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BoardSpecTest {

    @Test
    fun `computes a word count that can actually cover the cells`() {
        val random = Random(7)
        val spec = BoardSpec.forDifficulty(9, 9, Difficulty.HARD)
        listOf(3, 12, 25, 36, 49, 81, 144, 400).forEach { freeCount ->
            repeat(40) {
                val count = spec.wordCountFor(freeCount, random)
                assertTrue(
                    freeCount in MIN_WORD_LENGTH * count..MAX_WORD_LENGTH * count,
                    "$count words cannot cover $freeCount cells",
                )
            }
        }
    }

    @Test
    fun `the word count scales with the board rather than staying put`() {
        val random = Random(11)
        val spec = BoardSpec.forDifficulty(9, 9, Difficulty.HARD)
        val small = (1..60).map { spec.wordCountFor(25, random) }.average()
        val large = (1..60).map { spec.wordCountFor(144, random) }.average()

        assertTrue(large > small * 3, "25 cells gave $small words, 144 cells gave $large")
        // Both should land near the target average word length rather than at either extreme.
        // A 9x9 spec aims at nine letters, so the band has to reach that far.
        assertTrue(25 / small in 4.0..10.0, "25 cells over $small words")
        assertTrue(144 / large in 4.0..10.0, "144 cells over $large words")
    }

    @Test
    fun `a tiny board still yields one word`() {
        val spec = BoardSpec(width = 3, height = 1)
        spec.validate()
        assertEquals(1, spec.wordCountFor(3, Random(1)))
    }

    @Test
    fun `rejects a grid too small for any word`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            BoardSpec.forDifficulty(1, 2, Difficulty.EASY).validate()
        }
        assertContains(failure.message.orEmpty(), "too few for even one")
    }

    @Test
    fun `every offered wall count leaves room for a word`() {
        listOf(
            BoardSpec.forDifficulty(5, 5, Difficulty.EASY),
            BoardSpec.forDifficulty(7, 7, Difficulty.HARD),
            BoardSpec.forDifficulty(12, 12, Difficulty.HARD),
        ).forEach { spec ->
            val counts = spec.wallCounts()
            assertTrue(counts.isNotEmpty(), "no wall count for ${spec.width}x${spec.height}")
            counts.forEach { wallCount ->
                assertTrue(
                    spec.cellCount - wallCount >= MIN_WORD_LENGTH,
                    "$wallCount walls leave too few cells on ${spec.width}x${spec.height}",
                )
            }
        }
    }
}
