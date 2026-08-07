package eu.andret.ravel

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.andret.ravel.settings.LanguageSettings
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
        val language = (application as RavelApplication).language
        // What this activity was built with. Resources are bound to a context that was localized
        // once, in attachBaseContext, so a language chosen afterwards cannot reach the strings
        // already on screen: the activity has to be built again. Captured rather than re-read so
        // the comparison below is against the locale actually in use, not the newest one stored.
        val built = language.current.value
        setContent {
            val current by language.current.collectAsStateWithLifecycle()
            // Recreated rather than restarted, so the navigation state survives the switch: the
            // player who changes the language from the options tab is still on the options tab
            // afterwards, reading it in the language they just picked.
            LaunchedEffect(current) {
                if (current != built) recreate()
            }
            RavelTheme {
                RavelApp()
            }
        }
    }
}
