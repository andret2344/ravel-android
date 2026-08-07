package eu.andret.ravel.settings

import eu.andret.ravel.generator.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelProgressStateTest {

    @Test
    fun `a new player starts on level one`() {
        assertEquals(1, LevelProgressState().current)
    }

    @Test
    fun `the level on offer is the one after the last one finished`() {
        assertEquals(8, LevelProgressState(completed = 7).current)
    }

    @Test
    fun `only the current level is current`() {
        val state = LevelProgressState(completed = 6)
        assertFalse("a level behind the player", state.isCurrent(6))
        assertTrue(state.isCurrent(7))
        assertFalse("a level they cannot reach", state.isCurrent(8))
    }

    @Test
    fun `a saved board belongs to the level it was saved on`() {
        val state = LevelProgressState(completed = 6, board = board(level = 7))
        assertNull("level six is behind the player", state.boardFor(6))
        assertNull("level eight has not been opened", state.boardFor(8))
        assertEquals(7L, state.boardFor(7)?.ordinal)
        assertTrue(state.started)
    }

    @Test
    fun `costs are reported against the current level and nothing else`() {
        // The store keeps one level's spending, so any other level has to be answered with zero
        // rather than with the current level's tally under someone else's number.
        val state =
            LevelProgressState(completed = 3, boughtLetters = 2, boughtWords = 1, seconds = 90)
        assertEquals(2, state.boughtLettersFor(4))
        assertEquals(1, state.boughtWordsFor(4))
        assertEquals(90, state.secondsFor(4))
        assertEquals(0, state.boughtLettersFor(3))
        assertEquals(0, state.boughtWordsFor(5))
        assertEquals(0, state.secondsFor(5))
    }

    @Test
    fun `an untouched level has nothing to go back to`() {
        assertFalse(LevelProgressState(completed = 6).started)
    }

    private fun board(level: Int) = SavedBoard(
        ordinal = level.toLong(),
        language = Language.POLISH,
        found = setOf(0),
        path = listOf(1, 2),
        hints = mapOf(0 to 1),
        wordCount = 4,
    )
}
