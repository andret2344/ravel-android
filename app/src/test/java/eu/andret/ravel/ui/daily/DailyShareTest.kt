package eu.andret.ravel.ui.daily

import eu.andret.ravel.game.WordMark
import eu.andret.ravel.game.WordResult
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The row, which is the only part of the share text that is not a resource.
 *
 * Worth pinning because it is the part that leaves the app: the rest is assembled by the platform
 * out of a translated layout, and a wrong separator is a wrong separator - a wrong colour is a
 * player telling everyone they bought a word they found themselves.
 */
class DailyShareTest {

    @Test
    fun `each mark has its own colour`() {
        assertEquals("3🟩", DailyShare.row(listOf(WordResult(3, WordMark.UNAIDED))))
        assertEquals("3🟨", DailyShare.row(listOf(WordResult(3, WordMark.PARTLY_REVEALED))))
        assertEquals("3🟥", DailyShare.row(listOf(WordResult(3, WordMark.REVEALED))))
    }

    @Test
    fun `every length is beside its own square`() {
        // The pairing is the reason there is no second line of numbers: a digit and an emoji are not
        // the same width, so a row of lengths under a row of squares lines up nowhere.
        val words = listOf(
            WordResult(4, WordMark.REVEALED),
            WordResult(5, WordMark.UNAIDED),
            WordResult(9, WordMark.PARTLY_REVEALED),
        )
        assertEquals("4🟥 5🟩 9🟨", DailyShare.row(words))
    }

    @Test
    fun `the row keeps the order it was given`() {
        // The order is the whole comparability claim - position n is the same word for every player
        // of that day - so a row that sorted or grouped itself would quietly break it.
        val words = listOf(
            WordResult(7, WordMark.REVEALED),
            WordResult(2, WordMark.UNAIDED),
            WordResult(5, WordMark.PARTLY_REVEALED),
        )
        assertEquals("7🟥 2🟩 5🟨", DailyShare.row(words))
    }

    @Test
    fun `a word of ten letters or more is still one entry`() {
        // Two digits beside one square, which is why the stored form keeps lengths in an array
        // rather than woven into the marks string - see StoredDailyProgress.shareJson.
        val words = listOf(
            WordResult(10, WordMark.UNAIDED),
            WordResult(1, WordMark.UNAIDED),
        )
        assertEquals("10🟩 1🟩", DailyShare.row(words))
    }
}
