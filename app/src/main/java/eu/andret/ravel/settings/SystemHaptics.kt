package eu.andret.ravel.settings

import android.content.Context
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings

/**
 * Whether the phone itself will let the board vibrate, as far as an app can tell.
 *
 * The board plays its feedback through the view (see [eu.andret.ravel.ui.board.BoardHaptics]), which
 * obeys the phone's touch-feedback switch, so [HapticSettings] can only take haptics away. When the
 * phone has them off, the game's own switch does nothing either way, and the settings tab says so
 * and grays the switch out instead of letting a player flip it and feel nothing.
 *
 * ### Only what is certain
 *
 * [Settings.System.HAPTIC_FEEDBACK_ENABLED] is public and needs no permission, and it is what stock
 * Android's switch writes. Android 13 put intensity sliders over it and manufacturers have screens of
 * their own, which may or may not keep the old value in step; a quiet mode or a battery saver can
 * also silence touch feedback with nothing public to say so. So this answers [OFF] only when the
 * setting reads a definite 0, and [ABSENT] only when there is no vibrator at all. Every other case is
 * [AVAILABLE] - a phone that is silent anyway is no worse off than before, while a warning that is
 * wrong would send a player hunting for a setting that is already on.
 */
enum class SystemHaptics {
    /** Nothing says the board cannot vibrate. */
    AVAILABLE,

    /** The phone's touch-feedback setting is off, so nothing the game does will be felt. */
    OFF,

    /** The device has no vibrator. Most tablets. */
    ABSENT,
    ;

    companion object {
        /**
         * Read now. Not observed: the settings tab reads it again each time it comes into view,
         * which is the only moment the answer can be acted on, and the player changes it in another
         * app, so coming back is exactly when it may have changed.
         */
        fun read(context: Context): SystemHaptics = of(
            hasVibrator = vibratorOf(context)?.hasVibrator() == true,
            touchFeedback = Settings.System.getInt(
                context.contentResolver,
                Settings.System.HAPTIC_FEEDBACK_ENABLED,
                UNKNOWN,
            ),
        )

        /**
         * The decision on its own, for the tests.
         *
         * @param touchFeedback the raw setting, or [UNKNOWN] when the phone has never written it.
         */
        fun of(hasVibrator: Boolean, touchFeedback: Int): SystemHaptics = when {
            !hasVibrator -> ABSENT
            touchFeedback == 0 -> OFF
            else -> AVAILABLE
        }

        /** No value stored. Anything but 0 counts as on - see the class comment. */
        const val UNKNOWN = -1

        private fun vibratorOf(context: Context): Vibrator? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Vibrator::class.java)
            }
    }
}
