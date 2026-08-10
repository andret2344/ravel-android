package eu.andret.ravel.game

/**
 * How much of one word was showing when the board was finished.
 *
 * Three states and not "which button the player pressed", which is a distinction worth being blunt
 * about because the obvious naming - found / bought a letter / bought the word - is a claim this
 * cannot support. [Puzzle.hints] counts *cells revealed per word* and nothing else: it does not
 * record which kind of hint revealed them, and it cannot, because five letters bought one at a time
 * and one whole word bought outright leave the same cells showing. [Puzzle.bought] knows the two
 * apart but only as totals for the whole board, so it cannot say which word either of them landed
 * on.
 *
 * So these describe the board rather than the wallet, and the names say so. That also makes the one
 * awkward case honest rather than wrong: a player who traces four cells of a five-letter word by
 * hand and then buys a letter has the whole word revealed - [Puzzle.hinted] counts from however far
 * along they already were - and that word is [REVEALED] here even though they did most of it
 * themselves and paid for one letter. Under the other naming it would read as "bought the word",
 * which is false. Under this one it reads as "the word ended up fully revealed", which is true. It
 * is also not a rare case: hints deliberately target the word the player is furthest along on, so
 * being one cell from the end is exactly when a letter gets bought.
 *
 * @param code the single character this is written down as. Short because a solved board's marks
 *   live in the same preference string as the rest of the daily history, which is rewritten on every
 *   hint and every pause - see [eu.andret.ravel.settings.StoredDailyProgress].
 */
enum class WordMark(val code: Char) {
    /** Traced with nothing given away. */
    UNAIDED('u'),

    /** Part of it was showing, and the player found the rest. */
    PARTLY_REVEALED('p'),

    /** All of it was showing by the end, so the player was not the one who found it. */
    REVEALED('r'),
    ;

    companion object {
        /** Null for a character no release wrote, which every caller treats as no marks at all. */
        fun byCode(code: Char): WordMark? = entries.firstOrNull { it.code == code }
    }
}

/**
 * One word of a finished board: how long it was, and how much of it the player found.
 *
 * The two travel together rather than as two lists the same length, because they are one fact about
 * one word and parallel lists are two facts that can drift apart - a zip that loses an element is a
 * board where every mark after it describes the wrong word, and nothing downstream could tell.
 *
 * The length is here because it is the only clue this game gives and it is therefore **already
 * public**: every player of a given day is shown the same list of lengths and nothing else. Printing
 * it gives away nothing and is what makes a shared result legible - "the nine-letter one beat me" is
 * a thing to reply to, where a red square in sixth place is a thing to count.
 */
data class WordResult(val length: Int, val mark: WordMark) {
    init {
        require(length > 0) { "A word with no letters was never on the board: $length" }
    }
}

/**
 * One entry per word, in the order the player sees the lengths in.
 *
 * **The order is the point.** These are meant to be compared between two people who played the same
 * day, and the only thing both of them are shown is the list of lengths - so the entries line up
 * with that list, and position *n* means the same word to both of them. Discovery order would have
 * been the obvious alternative and is the wrong one twice over: it differs per player, so nothing
 * lines up, and it is not recorded anywhere anyway ([Puzzle.found] is a set).
 *
 * Sorted by length exactly as [Puzzle.clues] is, ties left in solution order by the stability of
 * `sortedBy`, so the two lists are the same sequence of words. A test holds them to that rather than
 * a comment, because nothing else would notice if one of them started sorting differently.
 *
 * Reveals are counted against `cells.size` rather than `word.length` - the same number, since a path
 * of *n* cells spells an *n*-letter word - because it is cells that [Puzzle.hints] counts, and a
 * comparison should be against the thing that was actually measured.
 */
fun Puzzle.wordResults(): List<WordResult> = board.solution.indices
    .sortedBy { board.solution[it].word.length }
    .map { index ->
        val placement = board.solution[index]
        val revealed = hints[index] ?: 0
        WordResult(
            length = placement.word.length,
            mark = when {
                revealed <= 0 -> WordMark.UNAIDED
                revealed >= placement.cells.size -> WordMark.REVEALED
                else -> WordMark.PARTLY_REVEALED
            },
        )
    }
