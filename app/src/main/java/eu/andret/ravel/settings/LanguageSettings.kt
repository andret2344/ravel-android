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
 * Which language the game speaks: one of the ones it has, guessed from the phone until the player
 * says otherwise.
 *
 * One setting behind two things that used to be separate. It picks the dictionary boards are
 * generated from, and it picks the locale the interface is read in - see [localize]. Splitting them
 * would let a player set the game to English and still be told "Poziom 7" over an English board,
 * which is not a combination anybody asks for.
 *
 * Stored as a language code rather than an enum ordinal, so adding a language later cannot silently
 * repoint an existing player's saved choice.
 *
 * ### There is no "follow the phone" any more
 *
 * There used to be, as a third option in the picker and as a null in here, and the options screen
 * drew all three. It is gone from the picker, and what is left of it is the *default*: a player who
 * has never chosen is on [fromDevice], which is the phone's language when the game speaks it and
 * English when it does not.
 *
 * The difference that leaves is worth naming, because it is a real behaviour change and not a tidy-up.
 * Absent storage still means "resolve from the phone every time", so a player who has never opened
 * the picker does follow their phone across a system language switch. The moment they pick anything -
 * including the language they were already on - it is written down and they stop following it. That
 * is the whole of what the option used to buy, minus the ability to *go back* to following, which is
 * an answer to a question players do not ask and which cost a third row and a nullable everywhere it
 * was carried.
 *
 * Observable rather than a plain getter, and one instance per process rather than one per screen -
 * see [eu.andret.ravel.RavelApplication.language]. Both are consequences of the same change: the
 * language now selects which ladder [StoredLevelProgress] is reading, so a store that learned about
 * a switch only on the next process would show the old language's level until the app was killed.
 */
class LanguageSettings(context: Context) {
    private val context = context.applicationContext
    private val preferences = this.context.getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE)

    private val _current = MutableStateFlow(stored() ?: fromDevice(this.context))

    /**
     * The language to play and read in right now, which is also the one the picker shows as chosen.
     *
     * One flow where there used to be two. The picker needed a second, nullable one back when it had
     * to draw "follow the phone" as distinct from the language that option resolved to; with the
     * option gone, so is the distinction and so is the nullable.
     */
    val current: StateFlow<Language> = _current.asStateFlow()

    /** What the player picked, or null if they never have. Only [localize] has a use for the null. */
    val chosen: Language? get() = stored()

    /**
     * Puts the game on [language] for good.
     *
     * Written even when it is the language already being played, which looks like a no-op and is
     * not: until something is stored, the language is re-derived from the phone on every launch, so
     * writing is what turns a guess into a decision. A player who picks the language they are on has
     * said "this one", and their phone changing later must not overrule that.
     */
    fun choose(language: Language) {
        val previous = _current.value
        preferences.edit { putString(KEY_LANGUAGE, language.code) }
        if (language == previous) return
        // Each language holds its own trie. Keeping the old one alive after a switch doubles the
        // memory for a dictionary nothing is going to ask for again.
        Ravel.forget()
        _current.value = language
    }

    private fun stored(): Language? = preferences.getString(KEY_LANGUAGE, null)?.let(::byCode)

    companion object {
        private const val KEY_LANGUAGE = "language"

        /** The [Language] with this code, or null for a code no release of the game speaks. */
        fun byCode(code: String): Language? = Language.entries.firstOrNull { it.code == code }

        /**
         * The phone's language if the game speaks it, English otherwise.
         *
         * English is the fallback rather than Polish because a player whose phone is in neither
         * wants the one they are more likely to read, not the developer's.
         *
         * This is the whole of what the removed "follow the phone" option did, and it is now what
         * every player gets before they have chosen anything - which is why it is the guess and not
         * a setting: there is no state to store for "I have not decided yet".
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
         * Returning [base] unchanged for the unchosen case is what keeps the guess honest, including
         * the system's own per-app language picker on Android 13 and later: with nothing forced
         * here, whatever locale the system handed the activity is the one it keeps. It is also the
         * one place a null [chosen] still means something, now that the picker has no row for it.
         */
        fun localize(base: Context): Context {
            val chosen = LanguageSettings(base).chosen ?: return base
            val configuration = Configuration(base.resources.configuration)
            configuration.setLocale(Locale.forLanguageTag(chosen.code))
            return base.createConfigurationContext(configuration)
        }
    }
}
