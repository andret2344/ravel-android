package eu.andret.ravel.smoke

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import eu.andret.ravel.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Each of the five tabs keeps its own back stack across a switch - see `switchTo` in `RavelApp.kt`.
 *
 * The case it matters most for is the one the game itself causes: a hint the player cannot afford
 * sends them to the shop, and coming back has to find the board they left, not the ladder in front
 * of it. Nothing on the JVM can see this; it lives in the navigation graph's saved state.
 */
@RunWith(AndroidJUnit4::class)
class BackStacksTest : SmokeTest() {

    @Test
    fun everyTabComesBackToWhereItWasLeft() {
        enterGame()

        // Two tabs taken one screen deep: a board on the game tab, the licenses on the options tab.
        playLevel()
        waitForBoard()
        openTab(R.string.tab_settings)
        compose.onNodeWithText(text(R.string.settings_licenses_title)).performScrollTo().performClick()
        compose.waitForIdle()
        assertFalse("still on the options' first page", exists(R.string.settings_language))

        // The other three, each landing on its own first page.
        openTab(R.string.tab_daily)
        assertTrue("daily menu", exists(R.string.tile_calendar))
        openTab(R.string.tab_stats)
        assertTrue("stats", exists(R.string.stats_section_daily))
        openTab(R.string.tab_shop)
        assertTrue("shop", exists(R.string.shop_daily))

        // Back on the two deep tabs, each where it was left.
        openTab(R.string.tab_main)
        assertTrue("the board, not the ladder", exists(R.string.hint_letter))
        openTab(R.string.tab_settings)
        assertTrue("the licenses page", exists(R.string.settings_licenses_title))
        assertFalse("the licenses page, not the options", exists(R.string.settings_language))

        // And back walks that tab's own stack, to the options' first page.
        pressBack()
        assertTrue("the options' first page", exists(R.string.settings_language))
    }
}
