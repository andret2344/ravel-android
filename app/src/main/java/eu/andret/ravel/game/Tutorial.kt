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
 *
 * ## Why the four-letter word comes first
 *
 * The lesson used to open on the shortest word, which reads as the gentle start. It moved when the
 * [Decoy] step arrived, because a decoy only teaches anything while a word of its length is still
 * to be found - "a real word that fits a length on the list, and still does not count" is the whole
 * point. Both boards are full of real three-letter words that are not in the answer, and every
 * four- and five-letter one runs through a cell of the three-letter answer, so with the short word
 * found first there was no decoy left that fitted a length still on the list. Opening on the four
 * leaves the three-letter answer open and the decoys that match it intact.
 */
internal object Tutorial {
    /** How many of the board's words are introduced with their first letter outlined. */
    const val GUIDED_WORDS = 2

    /**
     * A real word lying on the board, of a length still to be found, that is not part of the answer.
     *
     * Players run into these on every board and nothing used to tell them why the game refuses a
     * word that is plainly a word. The tutorial now makes them trace one on purpose, right after the
     * first word, and says why it does not count.
     *
     * @param placements every path on the board spelling [word] from its first letter. All of them,
     *   because the step is passed by tracing the word, and a player who finds it somewhere other
     *   than the one place listed would be told nothing at all. `TutorialTest` enumerates the board
     *   to prove the list is complete.
     */
    class Decoy(val word: String, val placements: List<List<Int>>)

    /**
     * The board for [language]. An exhaustive `when` on purpose: a third language is a compile
     * error here until somebody writes it a board.
     */
    fun boardFor(language: Language): Board = when (language) {
        Language.POLISH -> board(
            rows = listOf("dpo#", "waw#", "#dóc", "#yła"),
            "cały" to intArrayOf(11, 15, 14, 13),
            "powód" to intArrayOf(1, 2, 6, 10, 9),
            "dwa" to intArrayOf(0, 4, 5),
        )
        Language.ENGLISH -> board(
            rows = listOf("nah#", "dan#", "#hdn", "#uma"),
            "hand" to intArrayOf(2, 1, 0, 4),
            "human" to intArrayOf(9, 13, 14, 15, 11),
            "and" to intArrayOf(5, 6, 10),
        )
    }

    /**
     * The decoy for [language]'s board: three letters, like the word left for the player to find
     * alone, and made of cells the first word does not use.
     *
     * Picked from the dozen real words each board holds for being the ones a new player certainly
     * knows - a peacock and a man - rather than the most frequent: "dół" and "had" rank higher, but
     * "dół" shares a cell with the first word and "had" is a form, which a player may argue with.
     */
    fun decoyFor(language: Language): Decoy = when (language) {
        Language.POLISH -> Decoy("paw", listOf(listOf(1, 5, 6), listOf(1, 5, 4)))
        Language.ENGLISH -> Decoy("man", listOf(listOf(14, 15, 11)))
    }

    private fun board(rows: List<String>, vararg words: Pair<String, IntArray>): Board = Board(
        width = rows.first().length,
        height = rows.size,
        letters = rows.joinToString(separator = "").toCharArray(),
        solution = words.map { (word, cells) -> Placement(word, cells) },
    )
}
