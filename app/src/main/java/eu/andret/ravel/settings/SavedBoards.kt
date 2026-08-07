package eu.andret.ravel.settings

import org.json.JSONArray
import org.json.JSONObject

/**
 * A [SavedBoard] as JSON, in one place because two stores write the same thing.
 *
 * The daily challenge keeps the day it is in the middle of and the main mode keeps the level, and
 * both are the same handful of indices into a board that will be regenerated rather than reloaded.
 * Two copies of this would be two formats the moment one of them grew a field.
 *
 * Keys are single letters because these strings are rewritten on every move: a board part-way
 * through a 10x10 is a path of up to a hundred numbers, and the names around them should not cost
 * more than the numbers do.
 */
internal object SavedBoards {
    private const val ORDINAL = "o"
    private const val LANGUAGE = "g"
    private const val FOUND = "f"
    private const val PATH = "p"
    private const val HINTS = "h"

    /** How many words the board holds, which is not the count of words *bought*. */
    private const val TOTAL = "n"

    fun toJson(board: SavedBoard): JSONObject = JSONObject().apply {
        put(ORDINAL, board.ordinal)
        put(LANGUAGE, board.language.code)
        put(TOTAL, board.wordCount)
        put(FOUND, JSONArray(board.found.toList()))
        put(PATH, JSONArray(board.path))
        put(HINTS, JSONObject().also { json ->
            board.hints.forEach { (word, cells) -> json.put("$word", cells) }
        })
    }

    /**
     * Throws on a board with no readable language, which every caller already treats as no board.
     *
     * That covers two cases with one answer. A board written before the language was recorded cannot
     * be placed - it may be from either dictionary, and guessing wrong lays one language's marks over
     * another's grid - and a board in a language a later release dropped has no grid to go back to at
     * all. Both are a lost half-finished board, which is what the stores' `runCatching` already
     * spends: the day itself, the level itself and the streak all survive it.
     */
    fun fromJson(json: JSONObject): SavedBoard = SavedBoard(
        ordinal = json.getLong(ORDINAL),
        language = checkNotNull(LanguageSettings.byCode(json.getString(LANGUAGE))) {
            "Saved board in a language this release does not speak: ${json.getString(LANGUAGE)}"
        },
        wordCount = json.getInt(TOTAL),
        found = json.getJSONArray(FOUND).ints().toSet(),
        path = json.getJSONArray(PATH).ints(),
        hints = json.getJSONObject(HINTS).let { revealed ->
            revealed.keys().asSequence().mapNotNull { key ->
                key.toIntOrNull()?.let { it to revealed.getInt(key) }
            }.toMap()
        },
    )

    private fun JSONArray.ints(): List<Int> = (0 until length()).map(::getInt)
}
