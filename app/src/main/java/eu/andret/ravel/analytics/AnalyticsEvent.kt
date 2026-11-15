package eu.andret.ravel.analytics

import eu.andret.ravel.game.Hint
import eu.andret.ravel.game.LevelModifier
import eu.andret.ravel.game.PuzzleMode

/**
 * Everything the game reports, one class per event.
 *
 * **Every event answers a decision that is still open**, and the list is short on purpose: an event
 * nobody will read is data collected for nothing, and the privacy policy has to be able to describe
 * all of it in a paragraph. The decisions, in the order of `docs/TODO.md`'s plan:
 *
 * - [BoardBuilt]: whether the boards build in time on real phones - the phone measurement, taken on
 *   every phone rather than on the one Redmi in the drawer. The device model is not a parameter:
 *   Firebase attaches it to every event by itself.
 * - [TutorialBegin], [TutorialComplete], [TutorialSkip]: whether the tutorial teaches or is fled.
 * - [LevelFinished]: where players drop off, and how much the late levels cost them in hints.
 * - [HintUnaffordable], [StuckOfferShown], [StuckOfferTaken]: whether the economy is too lean.
 * - [AdRequested], [AdFilled], [AdFailed], [AdWatched]: the fill rate, which is otherwise invisible.
 * - [DailySolved], [StreakRepaired], [DailyFromNotification]: whether the daily brings players back.
 * - [ModifierSet], [ModifierDropped], and the modifiers on [LevelFinished]: whether anybody plays
 *   with modifiers, which decides whether a fourth one is worth building.
 *
 * Pure Kotlin, with no Firebase in it, so `AnalyticsEventTest` can hold every event to Firebase's
 * limits on a desktop JVM: a name the SDK rejects is an event that silently never arrives, and
 * nothing on a device would say so.
 *
 * Parameter values are [String] or [Long] only - the two kinds Firebase reports on. A yes-or-no
 * is a string, never a number: as a number Firebase would offer to sum it.
 *
 * **Names are not stored anywhere, but they are history.** Renaming an event or a parameter splits
 * its data in two at the release that does it, and the console cannot join them back.
 */
sealed class AnalyticsEvent(val name: String) {
    /** The parameters, by Firebase name. */
    abstract val params: Map<String, Any>

    /**
     * A board built, or given up on, by the generator.
     *
     * Reported for every build that ran to an answer, prefetches included - those are most builds,
     * and the slow tail lives in them. A build canceled midway (a screen left, a language switched)
     * is not reported: it says nothing about how long the board takes.
     */
    data class BoardBuilt(
        val mode: PuzzleMode,
        val ordinal: Long,
        val size: Int,
        val millis: Long,
        val found: Boolean,
        val timeoutMillis: Long,
    ) : AnalyticsEvent("board_built") {
        override val params: Map<String, Any> = mapOf(
            "mode" to codeOf(mode),
            "ordinal" to ordinal,
            "size" to size.toLong(),
            "millis" to millis,
            "result" to if (found) "found" else "timeout",
            "timeout_ms" to timeoutMillis,
        )
    }

    /** The tutorial put in front of the player. Firebase's recommended name. */
    data object TutorialBegin : AnalyticsEvent("tutorial_begin") {
        override val params: Map<String, Any> = emptyMap()
    }

    /** The tutorial played to its last step. Firebase's recommended name. */
    data object TutorialComplete : AnalyticsEvent("tutorial_complete") {
        override val params: Map<String, Any> = emptyMap()
    }

    /**
     * The tutorial left before its end, by the corner button or the back gesture. The game counts it
     * as finished (`CLAUDE.md`); this is what says it was not.
     */
    data class TutorialSkip(val step: String) : AnalyticsEvent("tutorial_skip") {
        override val params: Map<String, Any> = mapOf("step" to step)
    }

    /**
     * A level beaten for the first time.
     *
     * @param paid the coins it paid, which is 0 for a level replayed after a progress reset.
     */
    data class LevelFinished(
        val level: Int,
        val seconds: Int,
        val lettersBought: Int,
        val wordsBought: Int,
        val modifiers: Set<LevelModifier>,
        val paid: Int,
    ) : AnalyticsEvent("level_finished") {
        override val params: Map<String, Any> = mapOf(
            "level" to level.toLong(),
            "seconds" to seconds.toLong(),
            "letters_bought" to lettersBought.toLong(),
            "words_bought" to wordsBought.toLong(),
            "modifiers" to codesOf(modifiers),
            "paid" to paid.toLong(),
        )
    }

    /** Today's daily solved, the first time. */
    data class DailySolved(
        val size: Int,
        val seconds: Int,
        val streak: Int,
        val lettersBought: Int,
        val wordsBought: Int,
    ) : AnalyticsEvent("daily_solved") {
        override val params: Map<String, Any> = mapOf(
            "size" to size.toLong(),
            "seconds" to seconds.toLong(),
            "streak" to streak.toLong(),
            "letters_bought" to lettersBought.toLong(),
            "words_bought" to wordsBought.toLong(),
        )
    }

    /** A hint tapped with too few coins for it - the moment the economy says no. */
    data class HintUnaffordable(
        val hint: Hint,
        val mode: PuzzleMode,
        val balance: Int,
    ) : AnalyticsEvent("hint_unaffordable") {
        override val params: Map<String, Any> = mapOf(
            "hint" to when (hint) {
                Hint.LETTER -> "letter"
                Hint.WORD -> "word"
            },
            "mode" to codeOf(mode),
            "balance" to balance.toLong(),
        )
    }

    /** The "stuck?" dialog came up. */
    data object StuckOfferShown : AnalyticsEvent("stuck_offer_shown") {
        override val params: Map<String, Any> = emptyMap()
    }

    /** The "stuck?" dialog's way to the shop was taken. */
    data object StuckOfferTaken : AnalyticsEvent("stuck_offer_taken") {
        override val params: Map<String, Any> = emptyMap()
    }

    /** A rewarded ad asked of the network. With [AdFilled] and [AdFailed], the fill rate. */
    data object AdRequested : AnalyticsEvent("ad_requested") {
        override val params: Map<String, Any> = emptyMap()
    }

    /** A rewarded ad arrived. */
    data object AdFilled : AnalyticsEvent("ad_filled") {
        override val params: Map<String, Any> = emptyMap()
    }

    /**
     * A rewarded ad did not arrive. [code] is the SDK's: 3 is "no fill", the network having nothing,
     * which is the number that matters; the rest are errors.
     */
    data class AdFailed(val code: Int) : AnalyticsEvent("ad_failed") {
        override val params: Map<String, Any> = mapOf("code" to code.toLong())
    }

    /**
     * A rewarded ad watched far enough to pay. Not `ad_reward`, which Firebase reserves.
     *
     * @param placement which of the two places an ad may start (`CLAUDE.md`, "Ads and consent").
     */
    data class AdWatched(val placement: Placement) : AnalyticsEvent("ad_watched") {
        override val params: Map<String, Any> = mapOf(
            "placement" to when (placement) {
                Placement.SHOP -> "shop"
                Placement.STREAK_REPAIR -> "streak_repair"
            },
        )

        enum class Placement { SHOP, STREAK_REPAIR }
    }

    /** A missed day bought back. */
    data class StreakRepaired(val withAd: Boolean) : AnalyticsEvent("streak_repaired") {
        override val params: Map<String, Any> = mapOf("with" to if (withAd) "ad" else "coins")
    }

    /**
     * The game opened from the daily notification. Not `notification_open`, which Firebase reserves
     * for its own messaging.
     */
    data object DailyFromNotification : AnalyticsEvent("daily_from_notification") {
        override val params: Map<String, Any> = emptyMap()
    }

    /** A modifier switched on or off for the next levels, on the levels tab. */
    data class ModifierSet(
        val modifier: LevelModifier,
        val enabled: Boolean,
    ) : AnalyticsEvent("modifier_set") {
        override val params: Map<String, Any> = mapOf(
            "modifier" to codeOf(modifier),
            "enabled" to enabled.toString(),
        )
    }

    /**
     * A modifier taken off on a board - giving up on it for this level, and for the next ones too
     * when [alsoNext].
     */
    data class ModifierDropped(
        val modifier: LevelModifier,
        val level: Int,
        val alsoNext: Boolean,
    ) : AnalyticsEvent("modifier_dropped") {
        override val params: Map<String, Any> = mapOf(
            "modifier" to codeOf(modifier),
            "level" to level.toLong(),
            "also_next" to alsoNext.toString(),
        )
    }

    internal companion object {
        /** Exhaustive, so a third mode cannot report under no name. */
        fun codeOf(mode: PuzzleMode): String = when (mode) {
            PuzzleMode.DAILY -> "daily"
            PuzzleMode.ENDLESS -> "endless"
        }

        /**
         * Written out rather than taken from the enum's name, so renaming an entry in code does not
         * rename it in the data - see the class comment.
         */
        fun codeOf(modifier: LevelModifier): String = when (modifier) {
            LevelModifier.NO_LENGTHS -> "no_lengths"
            LevelModifier.HIDDEN_LETTERS -> "hidden_letters"
            LevelModifier.REVERSED_WORDS -> "reversed_words"
        }

        /**
         * A set of modifiers as one value, in a fixed order so the same set always reads the same,
         * and `none` rather than an empty string, which the console shows as "(not set)".
         */
        fun codesOf(modifiers: Set<LevelModifier>): String =
            LevelModifier.entries.filter { it in modifiers }.joinToString(",") { codeOf(it) }
                .ifEmpty { "none" }
    }
}
