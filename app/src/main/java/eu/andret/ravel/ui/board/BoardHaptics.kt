package eu.andret.ravel.ui.board

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import eu.andret.ravel.settings.HapticSettings

/**
 * The three things a board has to say through the phone's vibrator, and nothing else.
 *
 * Three and not more, because haptics on a screen the player drags a finger across for minutes at a
 * time are a budget rather than a garnish: every buzz that does not mean something makes the ones
 * that do harder to feel. The ladder is the whole design - a cell is a tick, a word is a tap, a
 * finished board is a thump - and it is a ladder rather than three unrelated effects so that a
 * player can tell what happened without looking, which is the only reason to have haptics at all.
 *
 * ### Why [View.performHapticFeedback] and not [android.os.Vibrator]
 *
 * The vibrator gives exact control: a duration, an amplitude, an arbitrary waveform. It was the
 * obvious way to build a three-step ladder and it is the wrong one here.
 *
 * - It needs `VIBRATE`, which is a permission on the manifest of a word puzzle that has no other
 *   reason to want one.
 * - It ignores the system's touch-feedback setting. A player who turned haptics off would keep
 *   getting a buzz per letter, and the only way back would be a fourth switch in this app's own
 *   settings duplicating a switch the phone already has.
 * - It ignores the per-device haptic calibration. "20 ms at amplitude 120" is a different sensation
 *   on every actuator ever shipped; the constants below are what the OEM tuned its own keyboard to,
 *   which is exactly the feel a word game wants and is not a feel this project can measure.
 *
 * What is given up is the celebration: there is no standard constant for "you finished", so the end
 * of a board is the strongest ordinary one rather than a pattern of its own. That is a real loss and
 * it is accepted, because the alternative costs the two hundred ticks per board that come before it.
 *
 * ### Why the constants are chosen by API level
 *
 * The three that fit best are recent - `SEGMENT_TICK` is API 34, `CONFIRM` is API 30 - and this app
 * ships to 24. An unknown constant is not a crash: the system maps it to no effect and returns
 * false, which is silence. Silence on everything below Android 14 is not a graceful degradation, it
 * is the feature not existing, so each rung falls back to a constant that has been in the platform
 * since long before [minSdk][android.os.Build.VERSION_CODES.N].
 *
 * The fallbacks are not evenly spaced, and cannot be. Below API 30 the middle rung is `VIRTUAL_KEY`
 * against a `KEYBOARD_TAP` cell, which are the same class of effect a notch apart rather than the
 * clear step the modern constants give - see [CELL] for why the bottom of the range could not be
 * used and what that costs. The rung that always survives is the last one: a finished board is a
 * long press everywhere, and that is the event worth protecting.
 */
@Stable
internal class BoardHaptics(private val view: View) {
    /**
     * Whether the player has left board haptics on, kept as a plain property rather than read from
     * the store on every tick.
     *
     * A tick happens once per cell a finger crosses, so this is read dozens of times a second and a
     * preference lookup there would be a preference lookup dozens of times a second. It is pushed in
     * from the composition instead - see [rememberBoardHaptics] - which also means the switch
     * reaches a board the player is already in the middle of, rather than the next one they open.
     *
     * Not a Compose state: nothing draws from it, and making it observable would put a snapshot read
     * inside a gesture handler for no reader.
     */
    var enabled: Boolean = true

    /** A letter joined or dropped from the path being traced. The lightest thing the phone can do. */
    fun cell() = play(CELL)

    /** A word completed - by tracing, by tapping or by a hint. Heavier than a cell, still a tap. */
    fun word() = play(WORD)

    /** The last word on the board. The one moment in a solve that is allowed to be felt properly. */
    fun board() = play(BOARD)

    /**
     * The one place the switch is checked, so there is no way to add a fourth event that forgets to.
     *
     * The check is here and not at each call site for the same reason the events are three methods
     * rather than three constants passed around: what the board knows is that something happened,
     * and whether that turns into a vibration is this class's business alone.
     */
    private fun play(constant: Int) {
        if (enabled) view.performHapticFeedback(constant)
    }

    private companion object {
        /**
         * `SEGMENT_TICK` is the constant Android added for precisely this gesture - a finger
         * crossing the divisions of a control - so it is used where it exists.
         *
         * Below it the fallback is `KEYBOARD_TAP`, a keypress, which is a rung louder than the tick
         * this wants to be. It is deliberate, and it replaced two attempts at the quieter thing:
         *
         * - `TEXT_HANDLE_MOVE`, a cursor stepping between characters, is the same idea as
         *   `SEGMENT_TICK` one generation earlier and looks like the obvious fallback. It is gated
         *   in the platform on `config_enableHapticTextHandle`, an OEM resource that is off on
         *   plenty of shipped devices, and when it is off the system maps the constant to no effect
         *   at all. A fallback that is silently nothing on an unknown share of phones is worse than
         *   no fallback, because it cannot be told apart from the feature being broken.
         * - `CLOCK_TICK` is not gated and is the oldest tick the platform has, so it survives that
         *   objection - and it is the lightest effect Android defines, which the test device could
         *   not produce. Rotary (ERM) motors, which is what most budget phones still carry, need
         *   twenty-odd milliseconds to spin up; a tick is over before they have moved.
         *
         * So the bottom of the platform's range is unusable on the hardware this game will mostly
         * run on, and the ladder starts one rung higher than it was drawn. What that costs is
         * headroom between this and [WORD], which is now one step rather than two - and a cell tick
         * that is felt slightly too much is still a cell tick, where one that is not felt is nothing.
         */
        val CELL: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            HapticFeedbackConstants.SEGMENT_TICK
        } else {
            HapticFeedbackConstants.KEYBOARD_TAP
        }

        /**
         * `CONFIRM` is short and definite and means what a found word means, and it is a different
         * sensation from [CELL] rather than a louder one - which is the separation that matters,
         * since the two are only one step apart in strength now.
         *
         * `VIRTUAL_KEY` below API 30 is the closest the older range gets. It is a keypress like the
         * cell's, tuned a notch heavier by every OEM that ships a `config_virtualKeyVibePattern`,
         * and on a device that ships neither pattern apart the two rungs will feel the same. That is
         * accepted rather than solved: it affects Android 7 to 10, where the alternative is the tick
         * tier that [CELL] documents as unusable.
         */
        val WORD: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        }

        /**
         * The heaviest constant every Android has, and the only one a player will recognise as an
         * event rather than as feedback. Not gated on a version, because there is nothing newer
         * worth having: `GESTURE_END` and `TOGGLE_ON` are both lighter than this, and a finished
         * board should not feel like closing a drawer.
         */
        const val BOARD: Int = HapticFeedbackConstants.LONG_PRESS
    }
}

/**
 * The board's haptics, tied to the view they play through.
 *
 * Keyed on the view rather than remembered once: a composable moved to another window - which is
 * what a recreated activity is - would otherwise keep buzzing a view that has been detached, and a
 * detached view feeds back to nothing at all.
 *
 * [enabled] is written into the instance rather than being taken as a constructor argument, so the
 * object survives a flip of the switch. It has to: the gesture handlers hold this instance from the
 * moment the board is built, and a new object per value would leave a finger already on the screen
 * ticking through the one that was replaced.
 *
 * @param enabled the player's setting. The system's own touch-feedback setting is checked after this
 *   one, by the platform, and neither can be overruled by the other - see [HapticSettings].
 */
@Composable
internal fun rememberBoardHaptics(enabled: Boolean): BoardHaptics {
    val view = LocalView.current
    val haptics = remember(view) { BoardHaptics(view) }
    SideEffect { haptics.enabled = enabled }
    return haptics
}
