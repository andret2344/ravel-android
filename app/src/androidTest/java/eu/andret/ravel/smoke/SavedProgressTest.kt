package eu.andret.ravel.smoke

import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import eu.andret.ravel.R
import eu.andret.ravel.game.Hint
import eu.andret.ravel.game.PuzzleId
import eu.andret.ravel.game.PuzzleMode
import eu.andret.ravel.settings.StoredBoards
import eu.andret.ravel.settings.StoredLevelProgress
import eu.andret.ravel.settings.Wallet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What a board in progress leaves on disk, which is everything a process killed in the background
 * comes back to.
 *
 * A test cannot kill its own process - the instrumentation lives in it - so this reads the disk the
 * way a process started from nothing would: through new stores over the same preference files, with
 * nothing of the running game's memory. What the real kill adds on top, the screen putting it all
 * back, is a step in the release checklist (`CLAUDE.md`, "Shipping a release").
 *
 * The stores are built here only to read; the rule that keeps one instance per preference file is
 * about two writers disagreeing, and these never write.
 */
@RunWith(AndroidJUnit4::class)
class SavedProgressTest : SmokeTest() {

    @Test
    fun aHintBoughtOnABoardIsOnDiskAndNotOnlyInMemory() {
        enterGame()
        playLevel()
        waitForBoard()
        val before = app.wallet.balance.value

        hintButton(R.string.hint_letter).performClick()
        compose.waitForIdle()

        val language = app.language.current
        assertEquals("the price, paid", before - Hint.LETTER.cost, Wallet(app).balance.value)

        val scope = CoroutineScope(Job())
        try {
            val level = StoredLevelProgress(app, language, scope).state.value
            assertEquals("the hint, recorded against the level", 1, level.boughtLetters)
            val board = level.board
            assertNotNull("the board in progress", board)
            assertTrue("the revealed letter, kept with the board", board!!.hints.isNotEmpty())
        } finally {
            scope.cancel()
        }

        assertNotNull(
            "the grid itself, on the shelf",
            StoredBoards(app).get(PuzzleId(PuzzleMode.ENDLESS, 1L), language.value),
        )
    }
}
