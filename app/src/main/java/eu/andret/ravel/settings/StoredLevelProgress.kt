package eu.andret.ravel.settings

import android.content.Context
import androidx.core.content.edit
import eu.andret.ravel.game.Hint
import eu.andret.ravel.generator.Language
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * The main mode's ladders, written to the device - one per language, in one file.
 *
 * Its own preference file rather than a corner of the daily one, because the two have nothing to say
 * to each other and the daily file is rewritten whole on every hint. A level and a streak that share
 * a file share every write, and the first bug of that shape is a streak lost to a board.
 *
 * Written through on every change, for the same reason as [Wallet] and [StoredDailyProgress]: the
 * number being protected is how far the player has got, and progress that survives everything except
 * the system reclaiming memory is progress nobody will trust.
 *
 * Plain preference keys rather than one JSON blob, unlike the daily store, because this is three
 * integers and a board and not two maps. The board is the only thing here with a shape, and it has
 * [SavedBoards] for it.
 *
 * ## Which keys belong to a language
 *
 * The per-language ones carry the code in front of them - `pl.completed`, `en.board` - and the
 * lifetime ones carry nothing. Prefixes rather than a file per language: one file is one `edit` per
 * change, and it keeps the two kinds of number side by side where the difference between them is
 * visible in the key. A file per language would put them in different files, which is where a reader
 * stops being able to tell a scoped number from a global one without opening both.
 *
 * There is deliberately no stored count of levels completed across all languages. It is the sum of
 * the per-language ones and is read as such, on the same argument that keeps the daily streak
 * derived: a second copy of a number is a number that can disagree with the first.
 */
class StoredLevelProgress(
    context: Context,
    private val language: StateFlow<Language>,
    scope: CoroutineScope,
) : LevelProgress {
    private val preferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read(language.value))

    override val state: StateFlow<LevelProgressState> = _state.asStateFlow()

    private val _totals = MutableStateFlow(readTotals())

    override val totals: StateFlow<LevelTotals> = _totals.asStateFlow()

    init {
        // The ladder in front of the player is whichever one their language names, so a switch has
        // to swap it under everything already collecting. Without this the menu would go on showing
        // the old language's level - over boards generated from the new one's dictionary - until the
        // process was killed, which is a mismatch nothing on screen could explain.
        scope.launch {
            language.collect { _state.value = read(it) }
        }
    }

    override fun recordHint(level: Int, hint: Hint) = onCurrent(level) { current ->
        when (hint) {
            Hint.LETTER -> current.copy(boughtLetters = current.boughtLetters + 1)
            Hint.WORD -> current.copy(boughtWords = current.boughtWords + 1)
        }
    }

    override fun recordTime(level: Int, seconds: Int) {
        require(seconds >= 0) { "Cannot spend a negative amount of time: $seconds" }
        if (seconds == 0) return
        onCurrent(level) { current -> current.copy(seconds = current.seconds + seconds) }
    }

    override fun complete(level: Int, wordCount: Int): Boolean {
        val current = _state.value
        if (level != current.current) return false
        // The lifetime figures are added before the level's own are cleared, because clearing them
        // is what makes this the last moment they exist. Levels completed is not among them: it is
        // summed from the per-language counters, and the write below is what moves this language's.
        writeTotals(
            _totals.value.let { totals ->
                totals.copy(
                    wordsFound = totals.wordsFound + wordCount,
                    seconds = totals.seconds + current.seconds,
                )
            },
        )
        // The next level starts clean: its own board, its own cost.
        write(LevelProgressState(completed = level))
        return true
    }

    /**
     * Puts the player on [level] outright, as if every level below it had been finished.
     *
     * **A developer tool, and the reason it is here rather than on [LevelProgress] is the whole
     * point.** That interface says in as many words that there is no method for setting the level,
     * because the ladder being unskippable is a rule the type enforces rather than one a screen
     * observes - and every consumer in the app is typed as the interface, so that stays true. The
     * escape hatch is on the class, reachable only by a caller that already knows it is holding the
     * concrete store, and the only such caller lives in the `debug` source set and does not exist in
     * a release build at all.
     *
     * It is on the store rather than written into the preference file by the debug code because the
     * key names are private here for a reason. A tool that spelled `pl.completed` itself would go on
     * compiling after this class renamed it, and silently stop working.
     *
     * **Nothing is paid and nothing is counted.** No coins, no words, no time - the lifetime totals
     * are left exactly as they were. One consequence is visible and is not worth hiding:
     * [LevelTotals.levelsCompleted] is *derived* from the per-language counter this writes, so the
     * statistics screen will report the levels as completed while the words and seconds behind them
     * stay at whatever they really were. That is the honest reading of what happened - the levels
     * were skipped, not played - and a tool that faked plausible totals to match would be putting
     * invented numbers into the one place the app keeps a real history.
     *
     * The current level's board and costs go with it, since they belonged to a level the player is
     * no longer on.
     */
    fun jumpTo(level: Int) {
        require(level >= 1) { "There is no level below one: $level" }
        write(LevelProgressState(completed = level - 1))
    }

    override fun saveBoard(board: SavedBoard) {
        preferences.edit { putString(key(KEY_BOARD), SavedBoards.toJson(board).toString()) }
        _state.value = _state.value.copy(board = board)
    }

    /**
     * Applies [change] only when [level] is the level being played.
     *
     * The guard is not defensive tidiness: a board can outlive its level by a frame - the last word
     * completes the level and the screen is still up, still able to bank a second of clock - and a
     * cost written then would land on the *next* level, which the player has not opened yet.
     *
     * Hints are counted twice on purpose, once against the level and once for good. The per-level
     * count is cleared when the level ends and the statistics screen needs a figure that is not.
     */
    private inline fun onCurrent(level: Int, change: (LevelProgressState) -> LevelProgressState) {
        val current = _state.value
        if (level != current.current) return
        val next = change(current)
        val letters = next.boughtLetters - current.boughtLetters
        val words = next.boughtWords - current.boughtWords
        // Banking a second of clock comes through here too and buys nothing, so the lifetime
        // counters are left alone unless a purchase actually moved one of them.
        if (letters != 0 || words != 0) {
            writeTotals(
                _totals.value.copy(
                    boughtLetters = _totals.value.boughtLetters + letters,
                    boughtWords = _totals.value.boughtWords + words,
                ),
            )
        }
        write(next)
    }

    private fun write(next: LevelProgressState) {
        preferences.edit {
            putInt(key(KEY_COMPLETED), next.completed)
            putInt(key(KEY_LETTERS), next.boughtLetters)
            putInt(key(KEY_WORDS), next.boughtWords)
            putInt(key(KEY_SECONDS), next.seconds)
            if (next.board == null) {
                remove(key(KEY_BOARD))
            } else {
                putString(key(KEY_BOARD), SavedBoards.toJson(next.board).toString())
            }
        }
        _state.value = next
        // Levels completed is summed from what was just written, so the totals have to be re-read
        // rather than left alone: this is the one write that can move a number nothing else stores.
        _totals.value = readTotals()
    }

    private fun writeTotals(next: LevelTotals) {
        preferences.edit {
            putInt(KEY_TOTAL_FOUND, next.wordsFound)
            putInt(KEY_TOTAL_SECONDS, next.seconds)
            putInt(KEY_TOTAL_LETTERS, next.boughtLetters)
            putInt(KEY_TOTAL_WORDS, next.boughtWords)
        }
        _totals.value = next
    }

    /**
     * What was stored for [language], with anything unreadable treated as absent.
     *
     * A board that will not parse costs the player the level they were part-way through; a board
     * that throws on read costs them the app. The level itself is an integer and survives either
     * way, which is the number that actually matters.
     */
    private fun read(language: Language): LevelProgressState {
        val completed = preferences.getInt(key(KEY_COMPLETED, language), 0)
        val board = preferences.getString(key(KEY_BOARD, language), null)
            ?.let { runCatching { SavedBoards.fromJson(JSONObject(it)) }.getOrNull() }
        return LevelProgressState(
            completed = completed,
            // A board belonging to a level already finished is not a board to go back to. It can
            // only get here through a write that landed between completing a level and clearing it.
            // The language is checked as well, which the keys make redundant and which stays anyway:
            // it costs one comparison to be certain that no grid is wearing another grid's marks.
            board = board?.takeIf { it.ordinal == completed + 1L && it.language == language },
            boughtLetters = preferences.getInt(key(KEY_LETTERS, language), 0),
            boughtWords = preferences.getInt(key(KEY_WORDS, language), 0),
            seconds = preferences.getInt(key(KEY_SECONDS, language), 0),
        )
    }

    /**
     * The lifetime figures, with the count of levels summed across every language rather than read.
     *
     * Zero for a player who was already up the ladder before these were counted, which undercounts
     * their history rather than refusing to show one.
     */
    private fun readTotals(): LevelTotals = LevelTotals(
        levelsCompleted = Language.entries.sumOf { preferences.getInt(key(KEY_COMPLETED, it), 0) },
        wordsFound = preferences.getInt(KEY_TOTAL_FOUND, 0),
        seconds = preferences.getInt(KEY_TOTAL_SECONDS, 0),
        boughtLetters = preferences.getInt(KEY_TOTAL_LETTERS, 0),
        boughtWords = preferences.getInt(KEY_TOTAL_WORDS, 0),
    )

    /** A per-language key, defaulting to the language being played. */
    private fun key(name: String, language: Language = this.language.value): String =
        "${language.code}.$name"

    private companion object {
        const val FILE = "ravel-levels"
        const val KEY_COMPLETED = "completed"
        const val KEY_BOARD = "board"
        const val KEY_LETTERS = "letters"
        const val KEY_WORDS = "words"
        const val KEY_SECONDS = "seconds"
        const val KEY_TOTAL_FOUND = "totalFound"
        const val KEY_TOTAL_SECONDS = "totalSeconds"
        const val KEY_TOTAL_LETTERS = "totalLetters"
        const val KEY_TOTAL_WORDS = "totalWords"
    }
}
