package eu.andret.ravel.game

import eu.andret.ravel.generator.Board
import eu.andret.ravel.generator.Language
import eu.andret.ravel.generator.Placement

/**
 * The tutorial's board, written by hand once per language rather than generated.
 *
 * A generated board would be a different board on every generator change and could not be taught
 * from: the steps point at "this word" and "that word", and the words have to be ones a new player
 * certainly knows. Both boards were picked from the generator's own output at rank 700 and are
 * proved unique against the full shipped dictionary by `TutorialTest`, so they obey every rule a
 * real board does - which is what makes the last step, finding a word with no help, honest.
 *
 * Both share one shape - four walls at the same cells, three words of 3, 4 and 5 letters - and both
 * repeat letters across words on purpose (two Ds, two Ws and two As in Polish; two Hs, two Ns, two
 * Ds and three As in English). A board whose every letter appeared once would teach nothing about
 * the one thing that makes the game a puzzle: a letter can belong to more than one word you can see.
 *
 * [Board.solution] is in the order the steps teach the words, not in any order of the board's: the
 * first two are guided, the last is left to the player.
 */
internal object Tutorial {
    /** How many of the board's words are introduced with their first letter outlined. */
    const val GUIDED_WORDS = 2

    /**
     * The board for [language]. An exhaustive `when` on purpose: a third language is a compile
     * error here until somebody writes it a board.
     */
    fun boardFor(language: Language): Board = when (language) {
        Language.POLISH -> board(
            rows = listOf("dpo#", "waw#", "#dóc", "#yła"),
            "dwa" to intArrayOf(0, 4, 5),
            "powód" to intArrayOf(1, 2, 6, 10, 9),
            "cały" to intArrayOf(11, 15, 14, 13),
        )
        Language.ENGLISH -> board(
            rows = listOf("nah#", "dan#", "#hdn", "#uma"),
            "and" to intArrayOf(5, 6, 10),
            "human" to intArrayOf(9, 13, 14, 15, 11),
            "hand" to intArrayOf(2, 1, 0, 4),
        )
    }

    private fun board(rows: List<String>, vararg words: Pair<String, IntArray>): Board = Board(
        width = rows.first().length,
        height = rows.size,
        letters = rows.joinToString(separator = "").toCharArray(),
        solution = words.map { (word, cells) -> Placement(word, cells) },
    )
}
