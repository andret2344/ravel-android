package eu.andret.ravel.generator

/**
 * The letters a language's tiles may carry.
 *
 * One character occupies one tile. In Polish that makes digraphs (sz, cz, rz, dż) two tiles each,
 * and "ż" and "z" distinct letters - a decision the data agrees with, since the source word lists
 * store no digraph as a unit.
 *
 * Each alphabet is derived from its own word list rather than assumed, so it holds exactly the
 * characters that actually occur in playable words. Polish keeps q, v and x because loanwords
 * admissible in word games use them.
 */
class Alphabet(val letters: String) {
    val size: Int = letters.length

    private val indexOf = IntArray(TABLE_SIZE) { -1 }

    init {
        require(size in 1..MAX_SIZE) { "Alphabet of $size letters is outside 1..$MAX_SIZE" }
        require(letters.toSet().size == size) { "Alphabet '$letters' repeats a letter" }
        letters.forEachIndexed { index, letter ->
            require(letter.code < TABLE_SIZE) { "Letter '$letter' is outside the supported character range" }
            indexOf[letter.code] = index
        }
    }

    /** Index of [letter] in this alphabet, or -1 when it does not belong to it. */
    fun index(letter: Char): Int = if (letter.code < TABLE_SIZE) indexOf[letter.code] else -1

    fun contains(letter: Char): Boolean = index(letter) >= 0

    fun isWordValid(word: String): Boolean = word.all(::contains)

    companion object {
        private const val TABLE_SIZE = 0x180

        /** Bitmask-friendly ceiling; nothing currently comes close, but it keeps the limit explicit. */
        const val MAX_SIZE = 64

        val POLISH = Alphabet("aąbcćdeęfghijklłmnńoóprsśtuwyzźżqvx")
        val ENGLISH = Alphabet("abcdefghijklmnopqrstuvwxyz")
    }
}
