package eu.andret.ravel.analytics

import eu.andret.ravel.game.Hint
import eu.andret.ravel.game.LevelModifier
import eu.andret.ravel.game.PuzzleMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Holds every event to Firebase's limits. The SDK drops an event it does not like without a word -
 * no exception, no log line a release build shows - so a bad name here is a hole in the data that
 * nobody would notice for weeks.
 */
class AnalyticsEventTest {
    /**
     * One of each event. [kindOf] is an exhaustive `when` over the sealed class, so a new event that
     * is not added there does not compile - and this list is next to it.
     */
    private val samples: List<AnalyticsEvent> = listOf(
        AnalyticsEvent.BoardBuilt(PuzzleMode.ENDLESS, 126, 8, 670, true, 180_000),
        AnalyticsEvent.TutorialBegin,
        AnalyticsEvent.TutorialComplete,
        AnalyticsEvent.TutorialSkip("decoy_traced"),
        AnalyticsEvent.LevelFinished(12, 95, 1, 0, setOf(LevelModifier.NO_LENGTHS), paid = 20),
        AnalyticsEvent.DailySolved(size = 8, seconds = 300, streak = 7, 0, 1),
        AnalyticsEvent.HintUnaffordable(Hint.WORD, PuzzleMode.DAILY, balance = 35),
        AnalyticsEvent.StuckOfferShown,
        AnalyticsEvent.StuckOfferTaken,
        AnalyticsEvent.AdRequested,
        AnalyticsEvent.AdFilled,
        AnalyticsEvent.AdFailed(3),
        AnalyticsEvent.AdWatched(AnalyticsEvent.AdWatched.Placement.STREAK_REPAIR),
        AnalyticsEvent.StreakRepaired(withAd = true),
        AnalyticsEvent.DailyFromNotification,
        AnalyticsEvent.ModifierSet(LevelModifier.REVERSED_WORDS, enabled = true),
        AnalyticsEvent.ModifierDropped(LevelModifier.HIDDEN_LETTERS, level = 40, alsoNext = false),
    )

    @Test
    fun `there is a sample of every event`() {
        assertEquals(EVENT_KINDS, samples.map(::kindOf).toSet().size)
    }

    @Test
    fun `every event name is one Firebase accepts`() {
        samples.forEach { event ->
            assertTrue(event.name, NAME.matches(event.name))
            assertFalse(event.name, RESERVED_PREFIXES.any(event.name::startsWith))
            assertFalse(event.name, event.name in RESERVED_EVENTS)
        }
    }

    @Test
    fun `no two events share a name`() {
        assertEquals(samples.size, samples.map { it.name }.toSet().size)
    }

    @Test
    fun `every parameter fits Firebase's limits`() {
        samples.forEach { event ->
            assertTrue(event.name, event.params.size <= MAX_PARAMS)
            event.params.forEach { (key, value) ->
                assertTrue("${event.name}.$key", NAME.matches(key))
                assertFalse("${event.name}.$key", RESERVED_PREFIXES.any(key::startsWith))
                assertTrue("${event.name}.$key", value is String || value is Long)
                if (value is String) {
                    assertTrue("${event.name}.$key", value.length <= MAX_VALUE_LENGTH)
                }
            }
        }
    }

    @Test
    fun `every set of modifiers fits in one parameter, in a fixed order`() {
        val all = LevelModifier.entries.toSet()
        assertTrue(AnalyticsEvent.codesOf(all).length <= MAX_VALUE_LENGTH)
        assertEquals("none", AnalyticsEvent.codesOf(emptySet()))
        assertEquals(
            AnalyticsEvent.codesOf(setOf(LevelModifier.NO_LENGTHS, LevelModifier.REVERSED_WORDS)),
            AnalyticsEvent.codesOf(setOf(LevelModifier.REVERSED_WORDS, LevelModifier.NO_LENGTHS)),
        )
    }

    private companion object {
        /** Letters, digits and underscores, starting with a letter, at most 40 long. */
        val NAME = Regex("[a-zA-Z][a-zA-Z0-9_]{0,39}")

        const val MAX_PARAMS = 25

        const val MAX_VALUE_LENGTH = 100

        val RESERVED_PREFIXES = listOf("firebase_", "google_", "ga_")

        /** Firebase's reserved event names, from the `FirebaseAnalytics.Event` documentation. */
        val RESERVED_EVENTS = setOf(
            "ad_activeview", "ad_click", "ad_exposure", "ad_impression", "ad_query", "ad_reward",
            "adunit_exposure", "app_background", "app_clear_data", "app_exception", "app_remove",
            "app_store_refund", "app_store_subscription_cancel", "app_store_subscription_convert",
            "app_store_subscription_renew", "app_uninstall", "app_update", "app_upgrade",
            "dynamic_link_app_open", "dynamic_link_app_update", "dynamic_link_first_open", "error",
            "first_open", "first_visit", "in_app_purchase", "notification_dismiss",
            "notification_foreground", "notification_open", "notification_receive", "os_update",
            "session_start", "session_start_with_rollout", "user_engagement",
        )

        const val EVENT_KINDS = 17

        /** Exhaustive on purpose - see [samples]. */
        fun kindOf(event: AnalyticsEvent): Int = when (event) {
            is AnalyticsEvent.BoardBuilt -> 1
            AnalyticsEvent.TutorialBegin -> 2
            AnalyticsEvent.TutorialComplete -> 3
            is AnalyticsEvent.TutorialSkip -> 4
            is AnalyticsEvent.LevelFinished -> 5
            is AnalyticsEvent.DailySolved -> 6
            is AnalyticsEvent.HintUnaffordable -> 7
            AnalyticsEvent.StuckOfferShown -> 8
            AnalyticsEvent.StuckOfferTaken -> 9
            AnalyticsEvent.AdRequested -> 10
            AnalyticsEvent.AdFilled -> 11
            is AnalyticsEvent.AdFailed -> 12
            is AnalyticsEvent.AdWatched -> 13
            is AnalyticsEvent.StreakRepaired -> 14
            AnalyticsEvent.DailyFromNotification -> 15
            is AnalyticsEvent.ModifierSet -> 16
            is AnalyticsEvent.ModifierDropped -> 17
        }
    }
}
