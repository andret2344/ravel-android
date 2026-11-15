package eu.andret.ravel.game

import eu.andret.ravel.generator.Board

/**
 * Which cells [LevelModifier.HIDDEN_LETTERS] covers with a star on a given level. Each letter comes
 * back when the word it belongs to is found, and not before - a hint marks the cell but leaves the
 * star on it ([Puzzle.concealedCells]).
 *
 * The rules, each the owner's choice:
 *
 * - **How many: from half the words, rounded up and never under [MINIMUM], to one per word**, drawn
 *   for the level. A range from [MINIMUM] up would let a fifteen-word board draw three stars nobody
 *   notices for the same bonus as fifteen; starting at half keeps the modifier felt on every board
 *   while still varying.
 * - **Never a word's first letter.** A word is traced from its first letter and a hint reveals from
 *   there, so the first letter is the anchor: hiding it turns "which word is this" into "where does it
 *   start", a far harsher puzzle than the one the bonus is priced for.
 * - **Spread freely, but at most one star per three letters of a word ([capOf]) and never two side by
 *   side in it.** Three stars in one word ("*O**A") cannot be reasoned out, only guessed; one in
 *   three still leaves the letters around each star to read it by. A word always has room for one
 *   whatever its length, so a count of at most one per word always fits - the owner's fallback of
 *   "one per word" is the floor these caps never go below.
 *
 * **Drawn from the level's id, not at random when the board opens.** The stars have to be the same
 * cells every time the level comes up - after the screen is left, after the process is killed, after
 * a hint - or they would move under a half-solved word. Being a function of the id also means
 * nothing about them is stored. The stream is its own, salted off the board's seed, so it never
 * touches the generator's and the board stays the board its id names.
 */
object HiddenLetters {
    /** The fewest stars on any board, however few words it has (and never more than it has words). */
    const val MINIMUM = 3

    /**
     * Sets this stream apart from every other use of a puzzle's seed. **Never change it**: the stars
     * on a level opened before the change would move to other cells.
     */
    private const val SALT = 0x5EC2E7_1E77E25L

    /** The cells covered on [board], the board [id] names. */
    fun chooseFor(board: Board, id: PuzzleId): Set<Int> {
        val words = board.solution
        if (words.isEmpty()) return emptySet()
        val random = SplitMix(id.seed xor SALT)
        val least = minOf(words.size, maxOf(MINIMUM, (words.size + 1) / 2))
        val count = least + random.nextInt(words.size - least + 1)
        // Every position but the first of every word, in an order drawn for the level; the first
        // ones that fit the caps take a star.
        val candidates = words.indices
            .flatMap { word -> (1 until words[word].cells.size).map { position -> word to position } }
            .toMutableList()
        random.shuffle(candidates)
        val taken = List(words.size) { HashSet<Int>() }
        val cells = HashSet<Int>()
        for ((word, position) in candidates) {
            if (cells.size == count) break
            val placed = taken[word]
            if (placed.size >= capOf(words[word].cells.size)) continue
            if (position - 1 in placed || position + 1 in placed) continue
            placed += position
            cells += words[word].cells[position]
        }
        return cells
    }

    /** At most one star per three letters of a word of [length], and always room for one. */
    fun capOf(length: Int): Int = maxOf(1, length / 3)

    /**
     * SplitMix64, written out rather than taken from `kotlin.random.Random(seed)`, whose algorithm is
     * the standard library's to change: the same seed has to land on the same cells in every version
     * of the app, or an update would move the stars under a level already part-way through.
     */
    private class SplitMix(private var state: Long) {
        fun nextLong(): Long {
            state += -0x61c8864680b583ebL
            var z = state
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
            return z xor (z ushr 31)
        }

        /** A value in `0 until bound`; the modulo bias is far below anything a board could show. */
        fun nextInt(bound: Int): Int = ((nextLong() ushr 1) % bound).toInt()

        /** Fisher-Yates. */
        fun <T> shuffle(list: MutableList<T>) {
            for (index in list.lastIndex downTo 1) {
                val other = nextInt(index + 1)
                val held = list[index]
                list[index] = list[other]
                list[other] = held
            }
        }
    }
}
