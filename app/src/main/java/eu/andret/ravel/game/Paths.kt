package eu.andret.ravel.game

import kotlin.math.abs

// The rules about paths on a board, with no board state and no Android anywhere near them. A path
// is a list of cell indices in the order they are walked, and everything the game decides about a
// player's move comes down to the three questions answered here: is this path the word, how much of
// it is right so far, and may it step there at all.

/**
 * True when [traced] walks exactly the cells of [cells], in that order.
 *
 * One direction only. The letters on the board spell the word one way round; drawn the other way
 * they spell it backwards, and a word backwards is not a word - so the path a player traces has to
 * run the way the word reads.
 *
 * Order is also what separates two routes over the same cells. Comparing the cells as sets would
 * accept a scribble that happens to land on the right squares while spelling something else.
 *
 * The generator is looser than this on purpose: it treats a word and its reverse as one answer when
 * it proves a board unique, so a board that is unique there is still unique under this stricter
 * rule. Tightening here can only ever reject, never admit a second answer.
 */
internal fun matchesPlacement(cells: IntArray, traced: List<Int>): Boolean =
    cells.size == traced.size && cells.indices.all { cells[it] == traced[it] }

/**
 * How many cells [first] and [second] agree on before they diverge.
 *
 * This is what "the part that was right" means when a hint corrects a path: everything up to the
 * first disagreement stands, and the rest was a wrong turn.
 */
internal fun commonPrefixLength(first: List<Int>, second: List<Int>): Int {
    val shortest = minOf(first.size, second.size)
    var length = 0
    while (length < shortest && first[length] == second[length]) length++
    return length
}

/**
 * Orthogonal neighbours only, which is the only step a word's path is built from.
 *
 * [width] is the board's, since a cell index means nothing without it - 6 and 7 are neighbours on a
 * board six wide and opposite corners of nothing on a board one wide.
 */
internal fun areAdjacent(width: Int, from: Int, to: Int): Boolean {
    val rowStep = abs(from / width - to / width)
    val columnStep = abs(from % width - to % width)
    return rowStep + columnStep == 1
}
