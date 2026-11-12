package eu.andret.ravel.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import eu.andret.ravel.generator.Language
import java.util.Locale

/**
 * The one-time rename of everything stored under the bare language codes `pl` and `en`, which
 * became `pl-PL` and `en-US`.
 *
 * **Temporary: delete it before 1.0.0** (`docs/TODO.md`). By then every tester has launched a
 * build with it once, and nobody else ever had the old keys. What stays for good is the alias in
 * [LanguageSettings.byCode], which is what reads an old code wherever this did not reach.
 *
 * Run at every process start rather than behind a "done" flag: it is idempotent, since once nothing
 * starts with an old code there is nothing to rename, and a flag would be one more key to delete
 * with it. It renames *keys* only:
 *
 * - `ravel-levels`: `en.completed` becomes `en-US.completed`, and so on for every per-language key.
 * - `ravel-boards`: `d.en.20261112` becomes `d.en-US.20261112`.
 * - `ravel-settings`: the chosen language below API 33, stored as a value, becomes the new code.
 *
 * The codes inside stored JSON - a saved board's language, the share card's - are left as they are.
 * The alias reads them, and they are rewritten in the new form the next time their store writes.
 * The per-app language the system holds from API 33 is left too: it is read by language alone.
 *
 * The file names are spelled out here rather than taken from the stores, whose constants are
 * private, and this is the one other place that will ever need them.
 */
object LegacyLanguageKeys {
    private const val LEVELS_FILE = "ravel-levels"
    private const val BOARDS_FILE = "ravel-boards"
    private const val KEY_LANGUAGE = "language"

    fun migrate(context: Context) {
        val app = context.applicationContext
        rename(app.getSharedPreferences(LEVELS_FILE, Context.MODE_PRIVATE), ::levelKey)
        rename(app.getSharedPreferences(BOARDS_FILE, Context.MODE_PRIVATE), ::boardKey)
        val settings = app.getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE)
        val stored = settings.getString(KEY_LANGUAGE, null)
        val language = stored?.let(LanguageSettings::byCode)
        if (language != null && language.code != stored) {
            settings.edit(commit = true) { putString(KEY_LANGUAGE, language.code) }
        }
    }

    /** `en.completed` -> `en-US.completed`; null for a key already new or not per-language. */
    fun levelKey(key: String): String? {
        val language = legacy(key.substringBefore('.')) ?: return null
        if (!key.contains('.')) return null
        return language.code + "." + key.substringAfter('.')
    }

    /** `d.en.42` -> `d.en-US.42`; null for a key already new. */
    fun boardKey(key: String): String? {
        val parts = key.split('.', limit = 3)
        if (parts.size != 3) return null
        val language = legacy(parts[1]) ?: return null
        return "${parts[0]}.${language.code}.${parts[2]}"
    }

    /** The [Language] whose old, bare code is [code], or null when [code] is not one. */
    private fun legacy(code: String): Language? =
        Language.entries.firstOrNull { Locale.forLanguageTag(it.code).language == code }

    /**
     * Moves every value whose key [renamed] maps to a new one, keeping its type. A value already
     * under the new key wins - it can only have been written by this build - and the old one goes.
     * Written with `commit` so the rename is on disk before any store opens the file.
     */
    private fun rename(preferences: SharedPreferences, renamed: (String) -> String?) {
        val moves = preferences.all.mapNotNull { (key, value) ->
            renamed(key)?.let { Triple(key, it, value) }
        }
        if (moves.isEmpty()) return
        preferences.edit(commit = true) {
            moves.forEach { (old, new, value) ->
                if (!preferences.contains(new)) put(new, value)
                remove(old)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun SharedPreferences.Editor.put(key: String, value: Any?) {
        when (value) {
            is Int -> putInt(key, value)
            is Long -> putLong(key, value)
            is Float -> putFloat(key, value)
            is Boolean -> putBoolean(key, value)
            is String -> putString(key, value)
            is Set<*> -> putStringSet(key, value as Set<String>)
        }
    }
}
