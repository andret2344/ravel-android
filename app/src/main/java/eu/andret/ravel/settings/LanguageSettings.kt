package eu.andret.ravel.settings

import android.content.Context
import android.content.res.Configuration
import androidx.core.content.edit
import eu.andret.ravel.generator.Language
import eu.andret.ravel.generator.Ravel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Which language the game speaks: the phone's, unless the player has said otherwise.
 *
 * One setting behind two things that used to be separate. It picks the dictionary boards are
 * generated from, and it picks the locale the interface is read in - see [localize]. Splitting them
 * would let a player set the game to English and still be told "Poziom 7" over an English board,
 * which is not a combination anybody asks for.
 *
 * Stored as a language code rather than an enum ordinal, so adding a language later cannot silently
 * repoint an existing player's saved choice. Absent means "follow the phone", which is a different
 * state from "the player chose Polish and the phone is Polish": switching the phone to English then
 * has to switch the game too in the first case and must not in the second.
 *
 * Observable rather than a plain getter, and one instance per process rather than one per screen -
 * see [eu.andret.ravel.RavelApplication.language]. Both are consequences of the same change: the
 * language now selects which ladder [StoredLevelProgress] is reading, so a store that learned about
 * a switch only on the next process would show the old language's level until the app was killed.
 */
class LanguageSettings(context: Context) {
    private val context = context.applicationContext
    private val preferences = this.context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _choice = MutableStateFlow(stored())

    private val _current = MutableStateFlow(stored() ?: fromDevice(this.context))

    /**
     * What the player picked in the options, or null while the game follows the phone.
     *
     * Kept apart from [current] rather than derived from it, because the options screen has to be
     * able to draw the difference: "follow the phone" and "Polish" are the same language on a Polish
     * phone and two different answers, and a picker that could not tell them apart would move the
     * player's selection onto Polish the moment they opened it.
     */
    val choice: StateFlow<Language?> = _choice.asStateFlow()

    /** The language to play and read in right now. */
    val current: StateFlow<Language> = _current.asStateFlow()

    /** What the player picked in the options, or null while the game follows the phone. */
    var chosen: Language?
        get() = _choice.value
        set(language) {
            val previous = _current.value
            preferences.edit {
                if (language == null) remove(KEY_LANGUAGE) else putString(KEY_LANGUAGE, language.code)
            }
            _choice.value = language
            val next = language ?: fromDevice(context)
            if (next == previous) return
            // Each language holds its own trie. Keeping the old one alive after a switch doubles
            // the memory for a dictionary nothing is going to ask for again.
            Ravel.forget()
            _current.value = next
        }

    private fun stored(): Language? = preferences.getString(KEY_LANGUAGE, null)?.let(::byCode)

    companion object {
        private const val FILE = "ravel-settings"
        private const val KEY_LANGUAGE = "language"

        /** The [Language] with this code, or null for a code no release of the game speaks. */
        fun byCode(code: String): Language? = Language.entries.firstOrNull { it.code == code }

        /**
         * The phone's language if the game speaks it, English otherwise.
         *
         * English is the fallback rather than Polish because a player whose phone is in neither
         * wants the one they are more likely to read, not the developer's.
         */
        fun fromDevice(context: Context): Language {
            val locale: Locale = context.resources.configuration.locales[0]
            return byCode(locale.language) ?: Language.ENGLISH
        }

        /**
         * [base] with the player's chosen language applied, or [base] itself while they have not
         * chosen one.
         *
         * Called from `Activity.attachBaseContext`, which is the earliest point at which a locale
         * can be forced and the only one that catches every resource the activity goes on to read.
         *
         * Done this way rather than through `AppCompatDelegate.setApplicationLocales` because that
         * is an appcompat API and this is a Compose app with no appcompat in it: taking it would
         * mean the dependency and an `AppCompatActivity` for one setting. The framework's own
         * `LocaleManager` is API 33 and this ships to 24, which would leave most of the range with
         * a setting that silently did half of what it says.
         *
         * Returning [base] unchanged for the unchosen case is what keeps "follow the phone" honest,
         * including the system's own per-app language picker on Android 13 and later: with nothing
         * forced here, whatever locale the system handed the activity is the one it keeps.
         */
        fun localize(base: Context): Context {
            val chosen = LanguageSettings(base).chosen ?: return base
            val configuration = Configuration(base.resources.configuration)
            configuration.setLocale(Locale.forLanguageTag(chosen.code))
            return base.createConfigurationContext(configuration)
        }
    }
}
