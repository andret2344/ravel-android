package eu.andret.ravel.settings

import eu.andret.ravel.game.WordMark
import eu.andret.ravel.game.WordResult
import eu.andret.ravel.generator.Language
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The lifetime totals, which are the only things in this class that are arithmetic rather than a
 * lookup. The streak is derived here too and has `StreakTest` to itself.
 */
class DailyProgressStateTest {

    @Test
    fun `a day nobody counted the words of is skipped rather than counted as zero`() {
        // The distinction the nullable wordCount exists to make. The sum comes out the same either
        // way - which is why this was invisible - so what is being pinned is that the unknown day
        // does not have to be represented as a real count in order to be summed past.
        val state = DailyProgressState(
            solved = mapOf(
                1L to DayResult(wordCount = 6),
                2L to DayResult(wordCount = null),
                3L to DayResult(wordCount = 4),
            ),
        )
        assertEquals(10, state.totalWordsFound)
    }

    @Test
    fun `a day still in progress is in no lifetime total`() {
        // Pending is not a weaker kind of solved. A day the player opened, spent two hints on and
        // walked away from has cost them something, and the calendar says so - but the totals on
        // the stats screen are about boards finished, and letting an abandoned day into them would
        // make every board ever opened look like a board that was beaten.
        val state = DailyProgressState(
            solved = mapOf(1L to DayResult(boughtLetters = 1, seconds = 30, wordCount = 5)),
            pending = mapOf(2L to DayResult(boughtLetters = 4, boughtWords = 2, seconds = 600)),
        )
        assertEquals(1, state.totalSolved)
        assertEquals(1, state.totalBoughtLetters)
        assertEquals(0, state.totalBoughtWords)
        assertEquals(30, state.totalSeconds)
        assertEquals(5, state.totalWordsFound)
    }

    @Test
    fun `a day recorded before anything was timing them adds no time`() {
        // Same shape as the word count and the reason the word count follows it: null is a day with
        // no answer, and a total that read it as zero would be indistinguishable from a real one.
        val state = DailyProgressState(
            solved = mapOf(
                1L to DayResult(seconds = null, wordCount = 3),
                2L to DayResult(seconds = 90, wordCount = 3),
            ),
        )
        assertEquals(90, state.totalSeconds)
        assertEquals(6, state.totalWordsFound)
    }

    @Test
    fun `the share card belongs to the day it names and to no other`() {
        // One slot for the last solve, exactly like the saved board, so the guard is the whole
        // feature: without it a card written yesterday would offer today's date a row of squares
        // from a board the player has not finished.
        val card = ShareCard(
            ordinal = 20260810L,
            language = Language.POLISH,
            size = 8,
            words = listOf(
                WordResult(4, WordMark.UNAIDED),
                WordResult(7, WordMark.REVEALED),
            ),
        )
        val state = DailyProgressState(share = card)
        assertEquals(card, state.shareFor(20260810L))
        assertEquals(null, state.shareFor(20260809L))
        assertEquals(null, state.shareFor(20260811L))
    }

    @Test
    fun `a day nobody has solved has nothing to share`() {
        assertEquals(null, DailyProgressState().shareFor(20260810L))
    }

    @Test
    fun `a card counts its words by counting its entries`() {
        // Which is why nothing stores the count beside them. Two numbers for one fact is the thing
        // this file keeps refusing to have.
        val card = ShareCard(
            ordinal = 20260810L,
            language = Language.ENGLISH,
            size = 6,
            words = listOf(
                WordResult(3, WordMark.UNAIDED),
                WordResult(5, WordMark.PARTLY_REVEALED),
                WordResult(6, WordMark.UNAIDED),
            ),
        )
        assertEquals(3, card.wordCount)
    }
}
