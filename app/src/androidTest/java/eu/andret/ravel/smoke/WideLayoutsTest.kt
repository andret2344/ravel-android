package eu.andret.ravel.smoke

import android.content.pm.ActivityInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import eu.andret.ravel.R
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every tab, and a board, on a phone lying on its side - the arrangements `AdaptiveScreen` picks when
 * the window is wider than it is tall (`docs/DECISIONS.md`, "Arranging a screen by its window").
 *
 * Each check is the thing a wide layout has already lost once: the hint buttons pushed off the
 * bottom of the screen by a board given half the width, and the daily's play tile put behind a
 * scroll. "Displayed" here means on screen, not merely composed somewhere below the fold.
 */
@RunWith(AndroidJUnit4::class)
class WideLayoutsTest : SmokeTest() {

    @Test
    fun everyTabAndTheBoardFitOnTheirSide() {
        enterGame()
        turn(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)

        compose.onNode(hasText(text(R.string.level_play)) and hasClickAction()).assertIsDisplayed()

        openTab(R.string.tab_daily)
        compose.onAllNodes(hasText(text(R.string.tile_play))).onFirst().assertIsDisplayed()

        openTab(R.string.tab_shop)
        // The two tiles carry their names as the glyphs' descriptions, not as text.
        compose.onNodeWithContentDescription(text(R.string.shop_gift_claim)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.shop_earn_ads)).assertIsDisplayed()

        openTab(R.string.tab_stats)
        compose.onNodeWithText(text(R.string.stats_section_daily)).assertIsDisplayed()

        openTab(R.string.tab_settings)
        compose.onNodeWithText(text(R.string.settings_language)).assertIsDisplayed()

        openTab(R.string.tab_main)
        playLevel()
        waitForBoard()
        hintButton(R.string.hint_letter).assertIsDisplayed()
        hintButton(R.string.hint_word).assertIsDisplayed()
    }
}
