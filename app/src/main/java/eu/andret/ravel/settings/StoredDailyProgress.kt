package eu.andret.ravel.settings

import android.content.Context
import androidx.core.content.edit
import eu.andret.ravel.game.DailyDate
import eu.andret.ravel.game.Hint
import eu.andret.ravel.generator.Language
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * The daily history and the board in progress, written to the device.
 *
 * Written through on every change rather than at some later save point, for the same reason as
 * [Wallet]: the thing being protected is a streak, and a streak that survives everything except the
 * system reclaiming memory is not a streak anyone will trust. The whole file is a few hundred bytes
 * of JSON per year of play, so rewriting it on each change costs less than tracking what changed.
 *
 * JSON in a single preference rather than one key per field, because the shape is two maps and a
 * record, and flattening a map into preference keys means inventing a key format, parsing it back,
 * and finding out later that a day was half-written. One string is written or it is not.
 *
 * The format is versioned by nothing at all, deliberately: anything unreadable is treated as absent
 * and the player starts clean. That is the right trade before release and the wrong one after it,
 * at which point [KEY_DAYS] has to grow a version and this comment has to be deleted.
 *
 * ## The one thing here that knows about languages
 *
 * The history does not have any. A day is solved or it is not, whichever dictionary it was solved
 * from - see [DailyProgressState]. The board in progress is the exception, because it is a set of
 * indices into one particular grid, and today's grid in Polish and today's in English are unrelated.
 * There is one slot for it, so the language is checked on the way out rather than stored per
 * language: a board from the other one is hidden while the player is away from it and comes back if
 * they switch back, up until the day they start the same date in the new language and overwrite it.
 */
class StoredDailyProgress(
    context: Context,
    private val language: StateFlow<Language>,
    scope: CoroutineScope,
) : DailyProgress {
    private val preferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /**
     * Read at every use rather than held from construction.
     *
     * This object lives as long as the process, and a process can outlive a midnight easily - a
     * phone left on the home screen overnight is the ordinary case. A "today" captured once would
     * have every streak in the app counted against yesterday from then on, and the only symptom
     * would be a streak that quietly stopped growing.
     */
    private val today: Long get() = DailyDate.today()

    private val _state = MutableStateFlow(read())

    override val state: StateFlow<DailyProgressState> = _state.asStateFlow()

    init {
        // The half-finished board is the only thing here a language switch can invalidate, and it
        // has to go the moment it does: the menu decides between "Play" and "Continue" from it, and
        // continuing onto a grid built from another dictionary marks the wrong words silently.
        scope.launch {
            language.collect { current ->
                _state.value = _state.value.copy(board = storedBoard()?.takeIf {
                    it.language == current
                })
            }
        }
    }

    override fun recordHint(ordinal: Long, hint: Hint) = addToPending(ordinal) { soFar ->
        when (hint) {
            Hint.LETTER -> soFar.copy(boughtLetters = soFar.boughtLetters + 1)
            Hint.WORD -> soFar.copy(boughtWords = soFar.boughtWords + 1)
        }
    }

    override fun recordTime(ordinal: Long, seconds: Int) {
        require(seconds >= 0) { "Cannot spend a negative amount of time: $seconds" }
        if (seconds == 0) return
        addToPending(ordinal) { soFar -> soFar.copy(seconds = (soFar.seconds ?: 0) + seconds) }
    }

    override fun complete(ordinal: Long, wordCount: Int): Boolean {
        val current = _state.value
        if (current.isSolved(ordinal)) return false
        // The running cost stops running and becomes the record; nothing can be added to it after.
        val cost = (current.pending[ordinal] ?: DayResult()).copy(wordCount = wordCount)
        // The board is finished, so the half-finished copy of it is no longer a thing to go back to.
        preferences.edit { remove(KEY_BOARD) }
        write(
            recount(
                solved = current.solved + (ordinal to cost),
                pending = current.pending - ordinal,
                board = null,
            ),
        )
        return true
    }

    override fun saveBoard(board: SavedBoard) {
        preferences.edit { putString(KEY_BOARD, SavedBoards.toJson(board).toString()) }
        _state.value = _state.value.copy(board = board)
    }

    private inline fun addToPending(ordinal: Long, change: (DayResult) -> DayResult) {
        val current = _state.value
        if (current.isSolved(ordinal)) return
        val soFar = current.pending[ordinal] ?: DayResult()
        write(current.copy(pending = current.pending + (ordinal to change(soFar))))
    }

    private fun write(next: DailyProgressState) {
        preferences.edit { putString(KEY_DAYS, days(next).toString()) }
        _state.value = next
    }

    private fun read(): DailyProgressState {
        val board = storedBoard()?.takeIf { it.language == language.value }
        val stored = preferences.getString(KEY_DAYS, null)
            ?: return recount(emptyMap(), emptyMap(), board)
        val parsed = runCatching { parse(JSONObject(stored)) }.getOrNull()
            ?: return recount(emptyMap(), emptyMap(), board)
        return recount(parsed.first, parsed.second, board)
    }

    /** Whatever board is on disk, in whatever language, or null when there is none to be had. */
    private fun storedBoard(): SavedBoard? = preferences.getString(KEY_BOARD, null)
        ?.let { runCatching { SavedBoards.fromJson(JSONObject(it)) }.getOrNull() }

    /**
     * Walks back from today counting solved days, then sweeps the whole history for the best run.
     *
     * The walk starts at today and is allowed one miss only if that miss is today itself - see
     * [DailyProgressState.streak] for why. Anything earlier ends the count.
     */
    private fun recount(
        solved: Map<Long, DayResult>,
        pending: Map<Long, DayResult>,
        board: SavedBoard?,
    ): DailyProgressState {
        var day = if (today in solved) today else DailyDate.shifted(today, -1)
        var streak = 0
        while (day in solved) {
            streak++
            day = DailyDate.shifted(day, -1)
        }
        return DailyProgressState(
            solved = solved,
            pending = pending,
            board = board,
            streak = streak,
            longest = maxOf(streak, longestIn(solved.keys)),
        )
    }

    /** The best run anywhere in [solved]: a day starts a run when the day before it is missing. */
    private fun longestIn(solved: Set<Long>): Int = solved.maxOfOrNull { start ->
        if (DailyDate.shifted(start, -1) in solved) {
            0
        } else {
            var day = start
            var run = 0
            while (day in solved) {
                run++
                day = DailyDate.shifted(day, 1)
            }
            run
        }
    } ?: 0

    private companion object {
        const val FILE = "ravel-daily"
        const val KEY_DAYS = "days"
        const val KEY_BOARD = "board"

        // Short names because this string is rewritten on every hint and every pause, and a year of
        // play is a few hundred of these objects.
        const val LETTERS = "l"
        const val WORDS = "w"
        const val SECONDS = "s"
        const val DONE = "d"
        const val FOUND = "f"

        /** Both maps in one object, each day carrying whether it was finished. */
        fun days(state: DailyProgressState): JSONObject {
            val json = JSONObject()
            state.solved.forEach { (ordinal, result) ->
                json.put(ordinal.toString(), result.toJson(done = true))
            }
            state.pending.forEach { (ordinal, result) ->
                json.put(ordinal.toString(), result.toJson(done = false))
            }
            return json
        }

        fun parse(json: JSONObject): Pair<Map<Long, DayResult>, Map<Long, DayResult>> {
            val solved = HashMap<Long, DayResult>()
            val pending = HashMap<Long, DayResult>()
            json.keys().forEach { key ->
                val ordinal = key.toLongOrNull() ?: return@forEach
                val day = json.getJSONObject(key)
                val result = DayResult(
                    boughtLetters = day.optInt(LETTERS),
                    boughtWords = day.optInt(WORDS),
                    // Absent rather than zero for a day nothing timed, which is what null means.
                    seconds = if (day.has(SECONDS)) day.getInt(SECONDS) else null,
                    // Absent on every day solved before boards started counting their words, which
                    // reads as zero and undercounts the total rather than refusing to show one.
                    wordCount = day.optInt(FOUND),
                )
                if (day.optBoolean(DONE)) solved[ordinal] = result else pending[ordinal] = result
            }
            return solved to pending
        }

        fun DayResult.toJson(done: Boolean): JSONObject = JSONObject().apply {
            put(LETTERS, boughtLetters)
            put(WORDS, boughtWords)
            seconds?.let { put(SECONDS, it) }
            // Only ever set on a finished day, so it is left out of the object entirely while the
            // day is still running rather than written as a zero nobody can tell from a real one.
            if (wordCount > 0) put(FOUND, wordCount)
            if (done) put(DONE, true)
        }

    }
}
