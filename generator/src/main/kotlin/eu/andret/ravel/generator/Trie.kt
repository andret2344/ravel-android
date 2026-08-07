package eu.andret.ravel.generator

/**
 * Flat prefix tree over an [Alphabet]. Nodes live in a single int array with no per-node object,
 * because the solver queries them millions of times per board.
 */
class Trie(private val alphabet: Alphabet) {
    private var next = IntArray(INITIAL_NODES * alphabet.size) { EMPTY }
    private var terminal = BooleanArray(INITIAL_NODES)
    private var nodeCount = 1

    val size: Int get() = nodeCount

    /** Slots allocated, which is what the trie actually costs. Doubling leaves up to half unused. */
    val capacity: Int get() = terminal.size

    /**
     * Releases the slack left by doubling. Worth calling once the last word is in: the arrays grow
     * by powers of two, so a dictionary that needs 131 761 nodes reserves 262 144 of them, and on
     * a phone that is twenty megabytes of nothing.
     */
    fun compact() {
        if (nodeCount == terminal.size) return
        next = next.copyOf(nodeCount * alphabet.size)
        terminal = terminal.copyOf(nodeCount)
    }

    fun add(word: String) {
        var node = ROOT
        for (letter in word) {
            val letterIndex = alphabet.index(letter)
            require(letterIndex >= 0) { "Character '$letter' outside the game alphabet in word '$word'" }
            val slot = node * alphabet.size + letterIndex
            var child = next[slot]
            if (child == EMPTY) {
                child = allocate()
                next[slot] = child
            }
            node = child
        }
        terminal[node] = true
    }

    /** Node reached from [node] through [letter], or [EMPTY] when there is no such edge. */
    fun step(node: Int, letter: Char): Int {
        val letterIndex = alphabet.index(letter)
        return if (letterIndex < 0) EMPTY else next[node * alphabet.size + letterIndex]
    }

    fun isWord(node: Int): Boolean = node != EMPTY && terminal[node]

    private fun allocate(): Int {
        if (nodeCount == terminal.size) grow()
        return nodeCount++
    }

    private fun grow() {
        val previousCapacity = terminal.size
        val capacity = previousCapacity * 2
        next = next.copyOf(capacity * alphabet.size).also { grown ->
            grown.fill(EMPTY, previousCapacity * alphabet.size, grown.size)
        }
        terminal = terminal.copyOf(capacity)
    }

    companion object {
        const val ROOT = 0
        const val EMPTY = -1
        private const val INITIAL_NODES = 1 shl 16
    }
}
