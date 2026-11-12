package eu.andret.ravel.settings

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
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
 * ### Where the choice is kept: the system's, from Android 13
 *
 * From API 33 the choice is the system's per-app language ([LocaleManager.getApplicationLocales]),
 * and nothing is written to [SETTINGS_FILE]. The game's own picker writes it there, and the system's
 * screen (Settings > Apps > Ravel > Language) is the same setting seen from outside: a choice made
 * in either place is the choice in both. It used to be a stored code on every API level, with the
 * system's screen listed (`res/xml/locales_config.xml`) but obeyed only until the player first chose
 * in the game - after that a pick in Android's settings changed nothing, silently.
 *
 * One store on each side of 33, never both. A copy in [SETTINGS_FILE] beside the system's value
 * would bring the conflict back: a player who resets the system screen to "System default" would
 * find the game falling back to the copy instead of to the phone. The code stored on a phone that
 * has since updated to 13 is simply no longer read - deliberately not handed over to the system,
 * since before 1.0.0 the only players who have one are testers.
 *
 * The system's value is backed up and restored by the system itself, which is what the stored code
 * got from `data_extraction_rules.xml`. Below 33 nothing changes: the code is stored and forced in
 * [localize].
 *
 * Because the system's value can change while the process lives - the player opens Android's
 * settings, picks a language and comes back - [current] is not settled at construction there.
 * [refresh] reads it again; the activity calls it on every creation, which a change of the per-app
 * language always causes, and the application on every configuration change.
 *
 * ### There is no "follow the phone" any more
 *
 * There used to be, as a third option in the picker and as a null in here, and the options screen
 * drew all three. It is gone from the picker, and what is left of it is the *default*: a player who
 * has never chosen is on [fromDevice], which is the phone's language when the game speaks it and
 * English when it does not.
 *
 * The difference that leaves is worth naming, because it is a real behavior change and not a tidy-up.
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

    private val _current = MutableStateFlow(resolve())

    /**
     * The language to play and read in right now, which is also the one the picker shows as chosen.
     *
     * One flow where there used to be two. The picker needed a second, nullable one back when it had
     * to draw "follow the phone" as distinct from the language that option resolved to; with the
     * option gone, so is the distinction and so is the nullable.
     */
    val current: StateFlow<Language> = _current.asStateFlow()

    /** What the player picked, or null if they never have. */
    val chosen: Language? get() = chosenIn(context)

    /**
     * Puts the game on [language] for good.
     *
     * Written even when it is the language already being played, which looks like a no-op and is
     * not: until something is stored, the language is re-derived from the phone on every launch, so
     * writing is what turns a guess into a decision. A player who picks the language they are on has
     * said "this one", and their phone changing later must not overrule that.
     */
    fun choose(language: Language) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // The system rebuilds the activity in the new locale on its own - a per-app language
            // change is a configuration change - so nothing here or in MainActivity asks for it.
            Api33.localeManager(context).applicationLocales =
                LocaleList(Locale.forLanguageTag(language.code))
        } else {
            preferences.edit { putString(KEY_LANGUAGE, language.code) }
        }
        switchTo(language)
    }

    /**
     * Reads the language again, for a choice made outside the game: from API 33 the system's own
     * screen can change it while this process is alive. A no-op below 33, where nothing but
     * [choose] writes it.
     */
    fun refresh() = switchTo(resolve())

    private fun switchTo(language: Language) {
        if (language == _current.value) return
        // Each language holds its own trie. Keeping the old one alive after a switch doubles the
        // memory for a dictionary nothing is going to ask for again.
        Ravel.forget()
        _current.value = language
    }

    private fun resolve(): Language = chosen ?: fromDevice(context)

    companion object {
        private const val KEY_LANGUAGE = "language"

        /**
         * The [Language] with this code, or null for a code no release of the game speaks.
         *
         * Also takes the bare `pl` and `en` the codes were before they carried a region, and for
         * good: [LegacyLanguageKeys] renames the keys once, but the codes also sit inside stored
         * JSON (a saved board, the share card) until it is next written, and a backup taken before
         * the rename can be restored at any time after it. A code this cannot read is a saved board
         * that fails to load, which is worth one line to never risk.
         */
        fun byCode(code: String): Language? =
            Language.entries.firstOrNull { it.code == code } ?: byLanguage(code)

        /**
         * The [Language] for a bare language subtag - `en`, `pl` - whatever region it comes with.
         * A phone in `en-GB` and a system pick of "English (Poland)" are both English here: there
         * is one English and one Polish, and the region a phone pairs them with says nothing about
         * which words a player knows.
         */
        fun byLanguage(subtag: String): Language? =
            Language.entries.firstOrNull { Locale.forLanguageTag(it.code).language == subtag }

        /**
         * What the player picked, or null if they never have: the system's per-app language from
         * API 33, the stored code below it. See the class comment for why never both.
         */
        private fun chosenIn(context: Context): Language? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Api33.chosen(context)
            } else {
                context.applicationContext
                    .getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE)
                    .getString(KEY_LANGUAGE, null)
                    ?.let(::byCode)
            }

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
            // From API 33 the context's own locale is the per-app one whenever a language is
            // chosen, so the phone's is asked of the system instead. Only called with nothing
            // chosen, where the two agree, but a phone that disagreed with itself once would make
            // this the one place to be wrong.
            val locale: Locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Api33.localeManager(context).systemLocales[0]
            } else {
                context.resources.configuration.locales[0]
            }
            return byLanguage(locale.language) ?: Language.ENGLISH
        }

        /**
         * [base] with the player's chosen language applied, or [base] itself while they have not
         * chosen one - and [base] itself from API 33, where the system applies the per-app
         * language to every context of the app before this is ever called.
         *
         * Called from `Activity.attachBaseContext`, which is the earliest point at which a locale
         * can be forced and the only one that catches every resource the activity goes on to read.
         *
         * Done this way below 33 rather than through `AppCompatDelegate.setApplicationLocales`
         * because that is an appcompat API and this is a Compose app with no appcompat in it:
         * taking it would mean the dependency and an `AppCompatActivity` for one setting.
         *
         * Forcing a locale only works if its strings are on the phone. Play splits a bundle by
         * language and installs only the phone's, which is why `app/build.gradle.kts` turns that
         * split off: with it, a Polish choice on an English phone forced a locale it had no
         * strings for and read English throughout.
         */
        fun localize(base: Context): Context {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
            val chosen = chosenIn(base) ?: return base
            val configuration = Configuration(base.resources.configuration)
            configuration.setLocale(Locale.forLanguageTag(chosen.code))
            return base.createConfigurationContext(configuration)
        }
    }

    /**
     * The API 33 calls, kept in one object so that nothing outside a version check can reach them
     * and lint can see every one of them guarded.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private object Api33 {
        fun localeManager(context: Context): LocaleManager =
            context.getSystemService(LocaleManager::class.java)

        /** The system's per-app language, or null for "System default" or an unknown language. */
        fun chosen(context: Context): Language? {
            val locales = localeManager(context).applicationLocales
            return if (locales.isEmpty) null else byLanguage(locales[0].language)
        }
    }
}
