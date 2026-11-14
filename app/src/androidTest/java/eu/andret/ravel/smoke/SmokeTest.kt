package eu.andret.ravel.smoke

import android.os.ParcelFileDescriptor
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import eu.andret.ravel.MainActivity
import eu.andret.ravel.R
import eu.andret.ravel.RavelApplication
import org.junit.Rule
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * What every smoke test shares: the real activity, started offline from a fresh install, and the
 * few moves it takes to get past the first launch.
 *
 * **Run on an emulator, never on a phone someone plays on.** The orchestrator clears the app's data
 * before every test (`clearPackageData` in `app/build.gradle.kts`), and the debug build has the same
 * application id as the release, so the data cleared is the game's. See `CLAUDE.md`, "Shipping a
 * release", for the command.
 *
 * Every text is read from the app's own resources through the activity, so the tests do not care
 * which language the emulator is set to: they look for whatever the player would see.
 */
abstract class SmokeTest {

    /** Offline before the activity starts, so it is in force for the consent request - see [OfflineRule]. */
    @get:Rule(order = 0)
    val offline = OfflineRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    protected val app: RavelApplication
        get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as RavelApplication

    /** A string as the player sees it, in the language the game is showing. */
    protected fun text(@StringRes id: Int): String = compose.activity.getString(id)

    protected fun exists(@StringRes id: Int): Boolean =
        compose.onAllNodesWithText(text(id)).fetchSemanticsNodes().isNotEmpty()

    /**
     * Past everything a fresh install puts in front of the game: the consent wait, the tutorial,
     * and the launch question about today's daily if it comes.
     */
    protected fun enterGame() {
        compose.waitUntil(STARTUP_MILLIS) { exists(R.string.tutorial_skip) }
        compose.onNodeWithText(text(R.string.tutorial_skip)).performClick()
        compose.waitForIdle()
        if (exists(R.string.daily_prompt_later)) {
            compose.onNodeWithText(text(R.string.daily_prompt_later)).performClick()
            compose.waitForIdle()
        }
    }

    /**
     * The tab named [label], in the bar or in the rail. Matched as a tab and not by its text alone,
     * because "Game" is also the title of the game tab's own menu.
     */
    protected fun tab(@StringRes label: Int): SemanticsNodeInteraction =
        compose.onNode(hasText(text(label)) and IS_TAB)

    protected fun openTab(@StringRes label: Int) {
        tab(label).performClick()
        compose.waitForIdle()
    }

    /** The level menu's button that opens the level on offer. */
    protected fun playLevel() {
        compose.onAllNodes(hasText(text(R.string.level_play)) and hasClickAction()).onFirst().performClick()
        compose.waitForIdle()
    }

    /** Waits until a board is on screen and playable, which is when its hint buttons are. */
    protected fun waitForBoard(timeoutMillis: Long = BOARD_MILLIS) {
        compose.waitUntil(timeoutMillis) { exists(R.string.hint_letter) }
    }

    protected fun hintButton(@StringRes label: Int): SemanticsNodeInteraction =
        compose.onNode(hasText(text(label)) and hasClickAction())

    /** Turns the phone, the way the system does: the activity is rebuilt and the view models stay. */
    protected fun turn(orientation: Int) {
        compose.activityRule.scenario.onActivity { it.requestedOrientation = orientation }
        compose.waitForIdle()
    }

    /** The system back gesture. */
    protected fun pressBack() {
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    protected companion object {
        /** The consent wait is capped at four seconds; the rest is a cold start on an emulator. */
        const val STARTUP_MILLIS = 30_000L

        /** Level one's board, which a phone builds in well under a second. */
        const val BOARD_MILLIS = 30_000L

        val IS_TAB = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
    }
}

/**
 * Airplane mode for the length of a test, put back as it was afterwards.
 *
 * Offline for two reasons. The consent form: from an address in the EEA the consent service shows it
 * on every fresh install, as a web view over the game that no test can answer in every language and
 * layout Google may give it - offline the request fails and the game opens at once, which is the
 * offline path players also take. And the ads: the AdMob ids are live, and a test has no business
 * asking for inventory, even the test ads an emulator is served.
 *
 * `cmd connectivity airplane-mode` exists from Android 11, which is why the emulator these run on has
 * to be at least that.
 */
class OfflineRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val wasOn = shell("settings get global airplane_mode_on").trim() == "1"
            if (!wasOn) shell("cmd connectivity airplane-mode enable")
            try {
                base.evaluate()
            } finally {
                if (!wasOn) shell("cmd connectivity airplane-mode disable")
            }
        }
    }

    /** Runs [command] as the shell user and returns what it printed, once it has finished. */
    private fun shell(command: String): String {
        val output = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(output).use { it.readBytes().decodeToString() }
    }
}
