package eu.andret.ravel.settings

import android.content.Context
import androidx.core.content.edit
import eu.andret.ravel.game.LevelModifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Which [LevelModifier]s the player wants on the levels they open next.
 *
 * This is the player's choice for levels **not yet opened**, and nothing more. The set a level is
 * actually played with is decided once, when its board first comes up, and kept with the level - see
 * [LevelProgress.open]. A level that has been seen without a modifier cannot have it added, since
 * what a modifier pays for is not having had what it takes away; one opened with a modifier cannot
 * lose it from here either, since the rule is that a level's modifiers do not move under it. The only
 * way one comes off an opened level is on the board ([LevelProgress.drop]).
 *
 * All off by default. They are offered on the main tab where nobody can miss them, which is the whole
 * of the discoverability they need, and a first board with no lengths would be a first board nobody
 * understands.
 *
 * Not per language, unlike the ladders: how a player likes to play does not change with the
 * dictionary. Stored in [SETTINGS_FILE] beside the other preferences, one boolean per modifier.
 */
class ModifierSettings(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE)

    private val _chosen = MutableStateFlow(
        LevelModifier.entries.filter { preferences.getBoolean(keyOf(it), false) }.toSet(),
    )

    /** The modifiers the next level opened will be played with. */
    val chosen: StateFlow<Set<LevelModifier>> = _chosen.asStateFlow()

    /** Written before it is published, as with [HapticSettings.setEnabled]. */
    fun set(modifier: LevelModifier, enabled: Boolean) {
        preferences.edit { putBoolean(keyOf(modifier), enabled) }
        _chosen.value = if (enabled) _chosen.value + modifier else _chosen.value - modifier
    }

    private companion object {
        /**
         * The stored key of each modifier, exhaustive so a new one cannot ship without one.
         *
         * **Never rename a key**: a renamed key switches the modifier off for every player who had
         * it on.
         */
        fun keyOf(modifier: LevelModifier): String = when (modifier) {
            LevelModifier.NO_LENGTHS -> "noLengths"
            LevelModifier.HIDDEN_LETTERS -> "hiddenLetters"
            LevelModifier.REVERSED_WORDS -> "reversedWords"
        }
    }
}
