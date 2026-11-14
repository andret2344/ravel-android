package eu.andret.ravel.game

import eu.andret.ravel.generator.Language
import java.net.URLEncoder

/**
 * The words a finished board held, and the dictionary they came from - what the completion dialog
 * and the calendar list so a player can look up a word they did not know.
 *
 * It exists because of how deep the vocabulary goes. From level 250 a board may draw words down to
 * rank 12 000 and later ones further still, and a player who has just traced *dziergać* without
 * knowing it is the player who writes "these are not real words" in a review. A word that opens its
 * meaning in one tap turns the same moment into having learned something.
 *
 * The language travels with the words rather than being read at the moment of showing them, for the
 * reason [eu.andret.ravel.settings.ShareCard.language] is recorded: the history is kept across every
 * language, so a Polish day looked at after switching to English would otherwise send its words to
 * the English dictionary.
 *
 * @param words in the order the lengths are shown in, the order of [Puzzle.clues] - see
 *   [solvedWords].
 */
data class SolvedWords(val language: Language, val words: List<String>) {
    init {
        require(words.isNotEmpty()) { "A solved board holds at least one word" }
        require(words.none(String::isEmpty)) { "A word with no letters was never on the board" }
    }
}

/**
 * Every word of the board, in the order the player sees the lengths in.
 *
 * Sorted the way [Puzzle.clues] and [wordResults] are - by length, ties in solution order - so the
 * list under the board, the squares on the share card and these words are one sequence. The word is
 * the dictionary's, which is also how it was traced: a word is only accepted from its first letter.
 */
fun Puzzle.solvedWords(language: Language): SolvedWords = SolvedWords(
    language = language,
    words = board.solution.indices
        .sortedBy { board.solution[it].word.length }
        .map { board.solution[it].word },
)

/**
 * The name of the site [definitionUrl] opens for [language], for the line under the list that says
 * where a tap leads. A proper name, so it is not translated.
 */
fun definitionSource(language: Language): String = when (language) {
    Language.POLISH -> "sjp.pl"
    Language.ENGLISH -> "Wiktionary"
}

/**
 * Where [word]'s meaning is looked up, in the dictionary of [language].
 *
 * **Polish goes to sjp.pl, English to Wiktionary**, and the split was measured rather than chosen
 * for symmetry. sjp.pl is where the Polish word list comes from, so it has a page for every word a
 * Polish board can hold. Polish Wiktionary was checked on 150 random words per band and had no page
 * for 5% of the first 8 000 by rank, 15% up to 16 000 and 19% up to 30 000 - and the words missing
 * are the rare ones, which are exactly the ones a player wants explained. English Wiktionary is the
 * most complete free English dictionary there is for a word list of common base forms like ENABLE.
 *
 * A `when` over every language, so a third one does not compile until somebody chooses where its
 * words are looked up.
 */
fun definitionUrl(word: String, language: Language): String {
    // The path form of percent-encoding: URLEncoder writes a space as "+", which is only a space
    // in a query string. No word here has one, but a link that is right by accident is not right.
    val encoded = URLEncoder.encode(word, Charsets.UTF_8.name()).replace("+", "%20")
    return when (language) {
        Language.POLISH -> "https://sjp.pl/$encoded"
        // The anchor lands on the English entry rather than at the top of the page, where a short
        // word opens with its Translingual entry - a symbol, an abbreviation - before the English.
        Language.ENGLISH -> "https://en.wiktionary.org/wiki/$encoded#English"
    }
}
