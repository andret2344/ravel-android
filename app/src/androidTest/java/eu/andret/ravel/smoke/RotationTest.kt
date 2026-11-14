package eu.andret.ravel.smoke

import android.content.pm.ActivityInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import eu.andret.ravel.R
import eu.andret.ravel.settings.StoredLevelProgress
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Turning the phone while a board is still being built.
 *
 * The manifest has no `android:configChanges` on purpose (see `CLAUDE.md`), so a turn rebuilds the
 * activity, and the search has to survive that: it runs in the view model and in `PuzzleCache`, not
 * in the screen. A search tied to the screen would start over on every turn - or be canceled, and
 * leave a spinner that never ends.
 */
@RunWith(AndroidJUnit4::class)
class RotationTest : SmokeTest() {

    @Test
    fun aBoardTurnedWhileBuildingArrivesInBothArrangements() {
        enterGame()
        // A level whose board takes a while, so the turn lands mid-search. Through the debug hatch,
        // the same one the debug build's "jump to level" button uses.
        compose.runOnUiThread { (app.levels as StoredLevelProgress).jumpTo(SLOW_LEVEL) }
        compose.waitForIdle()

        playLevel()
        turn(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        waitForBoard(SLOW_BOARD_MILLIS)
        val title = compose.activity.getString(R.string.level_number, SLOW_LEVEL)
        assertTrue("the level asked for", compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty())
        hintButton(R.string.hint_letter).assertIsDisplayed()
        hintButton(R.string.hint_word).assertIsDisplayed()

        turn(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        waitForBoard()
        hintButton(R.string.hint_letter).assertIsDisplayed()
        hintButton(R.string.hint_word).assertIsDisplayed()
    }

    private companion object {
        /**
         * One of the slow ones: level 238 took 68 seconds on a mid-range phone in the measurement
         * behind `PuzzleCache` (see `docs/DECISIONS.md`, "Building boards ahead").
         */
        const val SLOW_LEVEL = 238

        /** The cache's own deadline for a build nobody is waiting on, `Puzzles.PREFETCH_TIMEOUT_MILLIS`. */
        const val SLOW_BOARD_MILLIS = 180_000L
    }
}
