package eu.andret.ravel.settings

import android.content.Context
import androidx.core.content.edit
import eu.andret.ravel.game.DailyDate
import eu.andret.ravel.game.Hint
import eu.andret.ravel.game.StreakRun
import eu.andret.ravel.game.WordMark
import eu.andret.ravel.game.WordResult
import eu.andret.ravel.generator.Language
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
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

    override fun complete(card: ShareCard): Boolean {
        val current = _state.value
        if (current.isSolved(card.ordinal)) return false
        // The running cost stops running and becomes the record; nothing can be added to it after.
        // The count comes off the card rather than beside it, so "nobody counted" stays the only
        // thing a null DayResult.wordCount can mean: ShareCard cannot be built without at least one
        // mark, so a zero can no longer arrive here from a caller that has lost its board.
        val cost = (current.pending[card.ordinal] ?: DayResult()).copy(wordCount = card.wordCount)
        preferences.edit {
            // The board is finished, so the half-finished copy of it is no longer a thing to go
            // back to. The card replaces it in the same edit: one is what the player was doing and
            // the other is what they did, and there is never a moment where both are true.
            remove(KEY_BOARD)
            putString(KEY_SHARE, shareJson(card).toString())
        }
        write(
            recount(
                solved = current.solved + (card.ordinal to cost),
                pending = current.pending - card.ordinal,
                board = null,
                share = card,
                repaired = current.repaired,
            ),
        )
        if (card.ordinal > lastPaid()) preferences.edit { putLong(KEY_LAST_PAID, card.ordinal) }
        return true
    }

    override fun paidBeforeReset(ordinal: Long): Boolean =
        !_state.value.isSolved(ordinal) && ordinal <= lastPaid()

    private fun lastPaid(): Long = preferences.getLong(KEY_LAST_PAID, Long.MIN_VALUE)

    override fun refresh() {
        // Nothing is read from disk: only "today" has moved, and the state already holds the rest.
        // An unchanged count makes an equal state, which the flow does not emit again.
        _state.value = _state.value.let { recount(it.solved, it.pending, it.board, it.share, it.repaired) }
    }

    override fun repair(ordinal: Long): Boolean {
        // Recounted against the clock first: the state in memory may be from before a midnight the
        // caller's ad played across, and that midnight may have turned this gap into two.
        refresh()
        val current = _state.value
        if (current.repairable != ordinal) return false
        val repaired = current.repaired + ordinal
        preferences.edit { putString(KEY_REPAIRED, JSONArray(repaired.sorted()).toString()) }
        _state.value = recount(current.solved, current.pending, current.board, current.share, repaired)
        return true
    }

    override fun declineRepair(ordinal: Long) {
        preferences.edit { putLong(KEY_REPAIR_DECLINED, ordinal) }
        refresh()
    }

    override fun markRepairAsked(ordinal: Long) {
        preferences.edit { putLong(KEY_REPAIR_ASKED, ordinal) }
        refresh()
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

    /**
     * Forgets every day, the streak, the board in progress and the share card, in every language -
     * the whole file but the last day that paid, since the daily history is not kept per language. The player's "reset
     * progress", and nothing else.
     *
     * On the concrete store and not on [DailyProgress], for the reason
     * [StoredLevelProgress.clear] gives.
     */
    fun clear() {
        // The last day that paid survives, so today's board solved again after a reset is history
        // and not income - see paidBeforeReset. Taken from the record too, for an install that
        // solved days before this key existed.
        val paid = maxOf(lastPaid(), _state.value.solved.keys.maxOrNull() ?: Long.MIN_VALUE)
        preferences.edit {
            this.clear()
            if (paid != Long.MIN_VALUE) putLong(KEY_LAST_PAID, paid)
        }
        _state.value = read()
    }

    private fun read(): DailyProgressState {
        val board = storedBoard()?.takeIf { it.language == language.value }
        val share = storedShare()
        val repaired = storedRepaired()
        val stored = preferences.getString(KEY_DAYS, null)
            ?: return recount(emptyMap(), emptyMap(), board, share, repaired)
        val parsed = runCatching { parse(JSONObject(stored)) }.getOrNull()
            ?: return recount(emptyMap(), emptyMap(), board, share, repaired)
        return recount(parsed.first, parsed.second, board, share, repaired)
    }

    /**
     * Every day bought back, or none when the key is missing or unreadable.
     *
     * Its own key rather than a flag on the day inside [KEY_DAYS], because a repaired day is by
     * definition one that has no entry there - it was missed - and giving it one would make every
     * reader of that map learn to skip days that look solved and are not.
     */
    private fun storedRepaired(): Set<Long> = preferences.getString(KEY_REPAIRED, null)
        ?.let { text ->
            runCatching {
                val array = JSONArray(text)
                (0 until array.length()).map(array::getLong).toSet()
            }.getOrNull()
        }
        ?: emptySet()

    private fun storedDay(key: String): Long? =
        if (preferences.contains(key)) preferences.getLong(key, 0L) else null

    /** Whatever board is on disk, in whatever language, or null when there is none to be had. */
    private fun storedBoard(): SavedBoard? = preferences.getString(KEY_BOARD, null)
        ?.let { runCatching { SavedBoards.fromJson(JSONObject(it)) }.getOrNull() }

    /**
     * The last solve worth telling anyone about, or null.
     *
     * Not filtered by language on the way out, which is the difference between this and
     * [storedBoard] and is the whole reason [ShareCard] carries its own. A half-finished board in
     * the other dictionary is unusable - its indices point into a grid that is not on screen - while
     * a finished solve in the other dictionary is a fact that already happened, and the text built
     * from it says which language it happened in.
     */
    private fun storedShare(): ShareCard? = preferences.getString(KEY_SHARE, null)
        ?.let { runCatching { shareFromJson(JSONObject(it)) }.getOrNull() }

    /**
     * Counts the run, the best run and the repair on offer against today - see
     * [eu.andret.ravel.game.StreakRun] for every rule involved.
     */
    private fun recount(
        solved: Map<Long, DayResult>,
        pending: Map<Long, DayResult>,
        board: SavedBoard?,
        share: ShareCard?,
        repaired: Set<Long>,
    ): DailyProgressState {
        val days = solved.keys
        val declined = storedDay(KEY_REPAIR_DECLINED)
        val repairable = StreakRun.repairable(days, repaired, declined, today)
        val streak = StreakRun.length(days, repaired, today)
        return DailyProgressState(
            solved = solved,
            pending = pending,
            board = board,
            // Carried through whatever it says. A card for a day that is no longer today is not
            // deleted here, because deleting it would mean this method knowing what a stale card
            // is - and DailyProgressState.shareFor already answers that, at every call site, by
            // asking about a day rather than reading the slot.
            share = share,
            streak = streak,
            longest = maxOf(streak, StreakRun.longest(days, repaired)),
            repaired = repaired,
            repairable = repairable,
            repairStake = StreakRun.stake(days, repaired, declined, today),
            runRepaired = StreakRun.includesRepair(days, repaired, today),
            repairAsked = repairable != null && storedDay(KEY_REPAIR_ASKED) == repairable,
        )
    }

    private companion object {
        const val FILE = "ravel-daily"
        const val KEY_DAYS = "days"
        const val KEY_BOARD = "board"

        /**
         * Its own preference rather than a field on the day inside [KEY_DAYS].
         *
         * One card exists at a time, and putting it in the history would mean rewriting the whole
         * history to record a solve - and, worse, would put it somewhere that is *kept*, when the
         * point of a card is that it stops being interesting at midnight. A key of its own is
         * written once per solve and overwritten by the next one, which is the lifetime it has.
         */
        const val KEY_SHARE = "share"

        /** The latest day that has ever paid out, kept through a reset. */
        const val KEY_LAST_PAID = "lastPaid"

        /** Every missed day bought back, as a JSON array of ordinals. */
        const val KEY_REPAIRED = "repaired"

        /**
         * The last gap the player said no to repairing. One day, not a list: a refusal only ever
         * matters on the day after the gap, which is the only day that gap can be repaired.
         */
        const val KEY_REPAIR_DECLINED = "repairDeclined"

        /** The last gap whose repair dialog has been shown, so it opens by itself only once. */
        const val KEY_REPAIR_ASKED = "repairAsked"

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
                    // Absent while a day is running, and on every day solved before boards started
                    // counting their words. Null and not zero, for the same reason as the seconds
                    // above and rather more sharply - see DayResult.wordCount.
                    wordCount = if (day.has(FOUND)) day.getInt(FOUND) else null,
                )
                if (day.optBoolean(DONE)) solved[ordinal] = result else pending[ordinal] = result
            }
            return solved to pending
        }

        // The card's own keys. Short to match everything else in this file rather than because this
        // string is hot - it is written once per solved day, unlike the two above it.
        const val SHARE_ORDINAL = "o"
        const val SHARE_LANGUAGE = "g"
        const val SHARE_SIZE = "z"
        const val SHARE_MARKS = "m"
        const val SHARE_LENGTHS = "n"

        /**
         * The marks go out as one string of [eu.andret.ravel.game.WordMark.code] characters.
         *
         * An array of names would be the obvious shape and is worse in both directions: longer on
         * disk than the board it describes, and it makes the enum's *names* a storage format, so
         * renaming `PARTLY_REVEALED` would quietly stop yesterday's card parsing. The codes are
         * declared as a format on the enum and are the only thing a rename cannot touch.
         *
         * The lengths go beside them as an array rather than woven into the same string. One char
         * per mark is unambiguous; one char per digit is not, because a word can reach ten letters
         * and `"10u"` and `"1u0u"` are the same characters in a different order. Splitting them puts
         * the pairing in one place - [shareFromJson] - instead of in a parser that has to guess.
         */
        fun shareJson(card: ShareCard): JSONObject = JSONObject().apply {
            put(SHARE_ORDINAL, card.ordinal)
            put(SHARE_LANGUAGE, card.language.code)
            put(SHARE_SIZE, card.size)
            put(SHARE_MARKS, card.words.map { it.mark.code }.joinToString(""))
            put(SHARE_LENGTHS, JSONArray(card.words.map(WordResult::length)))
        }

        /**
         * Throws on anything it cannot place, which the caller already treats as no card at all.
         *
         * Losing a card costs the player one share button on one day. Every branch here is a card
         * written by a release that spelled something differently, and guessing at it would put a
         * row of squares under a date that did not produce them - which is worse than the button
         * not being there, because the player would post it.
         *
         * The two arrays are checked against each other rather than zipped and hoped for. `zip`
         * stops at the shorter one, so a truncated write would come back as a shorter board that
         * looks entirely valid - a card claiming five words on a board that held six, with every
         * length after the missing one attached to the wrong mark.
         */
        fun shareFromJson(json: JSONObject): ShareCard {
            val marks = json.getString(SHARE_MARKS).map { code ->
                checkNotNull(WordMark.byCode(code)) { "No such mark: $code" }
            }
            val lengths = json.getJSONArray(SHARE_LENGTHS).let { array ->
                (0 until array.length()).map(array::getInt)
            }
            check(marks.size == lengths.size) {
                "A card with ${marks.size} marks over ${lengths.size} words describes neither"
            }
            return ShareCard(
                ordinal = json.getLong(SHARE_ORDINAL),
                language = checkNotNull(LanguageSettings.byCode(json.getString(SHARE_LANGUAGE))) {
                    "Solved board in a language this release does not speak"
                },
                size = json.getInt(SHARE_SIZE),
                words = lengths.zip(marks, ::WordResult),
            )
        }

        fun DayResult.toJson(done: Boolean): JSONObject = JSONObject().apply {
            put(LETTERS, boughtLetters)
            put(WORDS, boughtWords)
            seconds?.let { put(SECONDS, it) }
            // Only ever set on a finished day, so it is left out of the object entirely while the
            // day is still running rather than written as a zero nobody can tell from a real one.
            // The test is now the null and not `> 0`: those agreed on every value the field could
            // hold, but one of them was reading a count and the other was reading a flag.
            wordCount?.let { put(FOUND, it) }
            if (done) put(DONE, true)
        }

    }
}
