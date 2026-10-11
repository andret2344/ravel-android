package eu.andret.ravel.settings

import android.content.Context
import androidx.core.content.edit
import eu.andret.ravel.game.BoardShelf
import eu.andret.ravel.game.Puzzle
import eu.andret.ravel.game.PuzzleId
import eu.andret.ravel.game.PuzzleMode
import eu.andret.ravel.generator.Board
import eu.andret.ravel.generator.Language
import eu.andret.ravel.generator.Placement
import org.json.JSONArray
import org.json.JSONObject

/**
 * Built boards, one preference per puzzle, kept until solved. See [eu.andret.ravel.game.PuzzleCache]
 * for why they are kept at all.
 *
 * The board is written whole - letters and solution - rather than as the seed it came from, because
 * the seed is exactly what costs a minute to turn back into a board. Only the grid is stored; the
 * player's marks on it stay in [SavedBoards], where they always were.
 *
 * Keys are `<mode>.<language>.<ordinal>`, with the mode as a one-letter code rather than the enum's
 * name, on the argument [eu.andret.ravel.game.WordMark.code] makes: a rename should not orphan what
 * is on disk. A value that does not parse is read as no board, and the screen builds one the way it
 * would have without this file.
 */
class StoredBoards(context: Context) : BoardShelf {
    private val preferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun get(id: PuzzleId, language: Language): Puzzle? =
        preferences.getString(key(id.mode, language, id.ordinal), null)
            ?.let { runCatching { Puzzle(fromJson(JSONObject(it))) }.getOrNull() }

    override fun put(id: PuzzleId, language: Language, puzzle: Puzzle) = preferences.edit {
        putString(key(id.mode, language, id.ordinal), toJson(puzzle.board).toString())
    }

    override fun remove(id: PuzzleId, language: Language) = preferences.edit {
        remove(key(id.mode, language, id.ordinal))
    }

    override fun ordinals(mode: PuzzleMode, language: Language): List<Long> {
        val prefix = key(mode, language, ordinal = null)
        return preferences.all.keys
            .filter { it.startsWith(prefix) }
            .mapNotNull { it.removePrefix(prefix).toLongOrNull() }
    }

    private companion object {
        /** Named in both backup rule files, which must be edited together. */
        const val FILE = "ravel-boards"

        private const val WIDTH = "w"
        private const val HEIGHT = "h"
        private const val LETTERS = "l"
        private const val SOLUTION = "s"
        private const val WORD = "w"
        private const val CELLS = "c"

        fun key(mode: PuzzleMode, language: Language, ordinal: Long?): String {
            val code = when (mode) {
                PuzzleMode.DAILY -> "d"
                PuzzleMode.ENDLESS -> "e"
            }
            return "$code.${language.code}.${ordinal ?: ""}"
        }

        fun toJson(board: Board): JSONObject = JSONObject().apply {
            put(WIDTH, board.width)
            put(HEIGHT, board.height)
            put(LETTERS, String(board.letters))
            put(SOLUTION, JSONArray().also { words ->
                board.solution.forEach { placement ->
                    words.put(JSONObject().apply {
                        put(WORD, placement.word)
                        put(CELLS, JSONArray(placement.cells.toList()))
                    })
                }
            })
        }

        fun fromJson(json: JSONObject): Board {
            val words = json.getJSONArray(SOLUTION)
            return Board(
                width = json.getInt(WIDTH),
                height = json.getInt(HEIGHT),
                letters = json.getString(LETTERS).toCharArray(),
                solution = (0 until words.length()).map { index ->
                    val word = words.getJSONObject(index)
                    val cells = word.getJSONArray(CELLS)
                    Placement(word.getString(WORD), IntArray(cells.length(), cells::getInt))
                },
            )
        }
    }
}
