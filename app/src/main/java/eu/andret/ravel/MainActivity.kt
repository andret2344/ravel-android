package eu.andret.ravel

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.andret.ravel.settings.LanguageSettings
import eu.andret.ravel.settings.ThemeChoice
import eu.andret.ravel.ui.LoadingScreen
import eu.andret.ravel.ui.RavelApp
import eu.andret.ravel.ui.theme.RavelTheme

class MainActivity : ComponentActivity() {
    /**
     * The earliest point at which the player's language can be forced, and the only one early enough
     * to catch every resource this activity goes on to read.
     *
     * Read straight off the preferences rather than through [RavelApplication.language], because
     * this runs before the activity has an application to ask - and because there is nothing to
     * observe yet either way. See [LanguageSettings.localize] for why the locale is applied here
     * rather than through the appcompat or framework APIs for it.
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageSettings.localize(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val ravel = application as RavelApplication
        // Asked here because a consent form is a dialog and needs an activity to sit in front of,
        // and asked before anything is drawn because the game waits on the answer - see below. It
        // shows a form only where one is owed, and it does nothing at all on the second call, so a
        // language switch rebuilding this activity is a check rather than a second form.
        ravel.consent.gather(this)
        val language = ravel.language
        // What this activity was built with. Resources are bound to a context that was localized
        // once, in attachBaseContext, so a language chosen afterwards cannot reach the strings
        // already on screen: the activity has to be built again. Captured rather than re-read so
        // the comparison below is against the locale actually in use, not the newest one stored.
        val built = language.current.value
        setContent {
            val current by language.current.collectAsStateWithLifecycle()
            val settled by ravel.consent.settled.collectAsStateWithLifecycle()
            val theme by ravel.theme.choice.collectAsStateWithLifecycle()
            val dark = when (theme) {
                ThemeChoice.SYSTEM -> isSystemInDarkTheme()
                ThemeChoice.LIGHT -> false
                ThemeChoice.DARK -> true
            }
            // The bars' icons follow the palette the game is drawn in, not the phone's. Left to
            // enableEdgeToEdge's own detection they follow the phone, which on a light game over a
            // dark phone is white clock and battery icons on a white bar. Scrims are the activity
            // library's own defaults, which it does not expose.
            LaunchedEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) {
                        dark
                    },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                )
            }
            // Recreated rather than restarted, so the navigation state survives the switch: the
            // player who changes the language from the options tab is still on the options tab
            // afterwards, reading it in the language they just picked.
            LaunchedEffect(current) {
                if (current != built) recreate()
            }
            RavelTheme(darkTheme = dark) {
                /*
                 * Nothing of the game is drawn until the consent question has been settled.
                 *
                 * The form has to come first, and "first" has to mean before the player has started
                 * reading something else: a dialog that lands on top of a screen they were already
                 * looking at gets dismissed by whichever button is closest, and the answer to that
                 * dialog is what the app's advertising stands on legally.
                 *
                 * Cheap in every case that is not the first launch. The flag lives on the
                 * application, so it survives this activity being rebuilt, and it is already true
                 * on a device that has answered - which means the loading screen is not a frame the
                 * returning player ever sees. And it always ends: see AdConsent.gather for the
                 * timer that opens the game whatever the consent service is doing, because a word
                 * puzzle that works offline must not have a launch that depends on a network.
                 */
                if (settled) RavelApp() else LoadingScreen()
            }
        }
    }

    private companion object {
        /** `androidx.activity`'s private `DefaultLightScrim` and `DefaultDarkScrim`. */
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
