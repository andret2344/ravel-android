package eu.andret.ravel.game

import eu.andret.ravel.generator.Board

/** One hidden word as the player sees it: its length, and whether they have traced it yet. */
data class Clue(val length: Int, val found: Boolean)

/**
 * A board being played: what has been found, what is marked, and what a hint has given away.
 *
 * Every move returns a new puzzle rather than changing this one. That keeps the rules honest -
 * there is no half-applied move - and it is what lets the whole of the game be tested without a
 * device, a view model or a frame of animation.
 *
 * [found] and [hints] hold indices into `board.solution`, which is the only place the answer lives:
 * a screen is given cells and lengths, never words.
 *
 * @param path the cells the player currently has marked, in order. It survives lifting a finger: a
 *   half-traced word stays on the board to be corrected or continued, which is what makes a long
 *   word tractable in more than one movement.
 * @param hints how many cells of a word a hint has revealed, counted from where the word begins -
 *   which is the only end it can be traced from. Kept separately from [path] so a revealed cell
 *   stays revealed when the player clears what they were tracing: a hint, once paid for, is not
 *   lost by tidying up.
 * @param bought how many of each kind of hint were paid for, which [hints] cannot answer: five
 *   letters bought one at a time and one whole word bought outright leave the same five cells
 *   revealed, and they are not the same thing to a player looking back at how they did.
 */
data class Puzzle(
    val board: Board,
    val found: Set<Int> = emptySet(),
    val path: List<Int> = emptyList(),
    val hints: Map<Int, Int> = emptyMap(),
    val bought: Map<Hint, Int> = emptyMap(),
) {
    /** The paths of the words found so far, so the board can draw them as the words they are. */
    val solvedPaths: List<List<Int>> = found.sorted().map { board.solution[it].cells.toList() }

    /** Every cell already spoken for by a found word. */
    val solvedCells: Set<Int> = solvedPaths.flatMapTo(HashSet()) { it }

    /** Cells a hint has given away, minus those now covered by a word that has been found. */
    val hintedCells: Set<Int> = hints.entries
        .filter { (word, _) -> word !in found }
        .flatMapTo(HashSet()) { (word, revealed) -> board.solution[word].cells.take(revealed) }

    /** Words still to find, which is what a hint has to choose between. */
    val unfound: List<Int> = board.solution.indices.filter { it !in found }

    /**
     * Lengths in ascending order, each carrying whether it has been found.
     *
     * Sorted by length alone and never re-sorted as words are found: a list that reshuffled itself
     * under the player would cost them the count they were keeping in their head.
     */
    val clues: List<Clue> = board.solution
        .mapIndexed { index, placement -> Clue(placement.word.length, index in found) }
        .sortedBy(Clue::length)

    val isComplete: Boolean get() = unfound.isEmpty()

    /** How many of [hint] were paid for on this board. */
    fun boughtOf(hint: Hint): Int = bought[hint] ?: 0

    /**
     * The same puzzle with [cells] marked, or with the word found when they spell one.
     *
     * Every route into the board goes through here - dragging, tapping, taking a hint - so
     * completing a word is checked in exactly one place rather than at each of them.
     */
    fun withPath(cells: List<Int>): Puzzle {
        val index = board.solution.indexOfFirst { matchesPlacement(it.cells, cells) }
        return if (index >= 0 && index !in found) {
            copy(found = found + index, path = emptyList())
        } else {
            copy(path = cells)
        }
    }

    /**
     * A tap, which is the correction a player makes without redrawing anything.
     *
     * - on the first marked cell: clears the path, since there is no shorter path to keep;
     * - on any other marked cell: cuts the path back to it, so a wrong turn ten cells ago is undone
     *   without retracing the nine good cells before it;
     * - on an unmarked cell next to the path's end: extends by one;
     * - anywhere else: starts a new path there.
     */
    fun tapped(cell: Int): Puzzle = when {
        cell in solvedCells || board.isWall(cell) -> this
        path.isEmpty() -> withPath(listOf(cell))
        cell == path.first() -> withPath(emptyList())
        cell in path -> withPath(path.take(path.indexOf(cell) + 1))
        areAdjacent(board.width, path.last(), cell) -> withPath(path + cell)
        else -> withPath(listOf(cell))
    }

    /**
     * Gives away part or all of the word the player is closest to finishing.
     *
     * "Closest" counts a word's revealed cells and however much of the current path already runs
     * along it, whichever is further. So a hint continues what the player was doing rather than
     * opening a third front, and a path that started right and went wrong is cut back to the part
     * that was right before anything more is given away - which is the correction the player would
     * otherwise have to find by trial.
     *
     * The revealed cells become the current path. After [Hint.LETTER] it is there to be continued;
     * after [Hint.WORD] there is nothing to continue, since a whole word traced is a word found.
     *
     * Ties between equally advanced words go to whichever starts earlier in reading order, so
     * repeated hints work down the board instead of jumping about.
     */
    fun hinted(hint: Hint): Puzzle {
        val target = target() ?: return this
        val cells = board.solution[target].cells
        val revealed = when (hint) {
            // One past whatever the player already has, which is what makes a letter a nudge
            // rather than an answer.
            Hint.LETTER -> cells.take(progressOn(target) + 1)
            Hint.WORD -> cells.toList()
        }
        // Counted here and not where the coins leave the wallet, because this is the one place that
        // knows a hint actually did something: the early return above is a hint with nothing left
        // to reveal, and the player is not charged for it either.
        return copy(
            hints = hints + (target to revealed.size),
            bought = bought + (hint to boughtOf(hint) + 1),
        ).withPath(revealed)
    }

    /** The word a hint would act on: the one furthest along, ties broken by position on the board. */
    private fun target(): Int? = unfound.minWithOrNull(
        compareBy({ -progressOn(it) }, { board.solution[it].cells.min() }),
    )

    /**
     * How far along the word at [index] the player already is, by hint or by hand.
     *
     * Both are counted from where the word begins, since that is the only end it can be traced
     * from: a path that starts anywhere else is not a partly-right answer, it is a different path,
     * and it scores nothing here.
     */
    private fun progressOn(index: Int): Int {
        val cells = board.solution[index].cells.toList()
        return maxOf(hints[index] ?: 0, commonPrefixLength(cells, path))
    }
}
