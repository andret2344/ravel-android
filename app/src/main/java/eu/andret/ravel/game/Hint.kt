package eu.andret.ravel.game

/**
 * The two ways a player can buy their way past a word they cannot see.
 *
 * They differ in what they give away, and the prices say so: a letter keeps the player working,
 * while a word takes the puzzle off their hands. Nothing here spends anything yet - the wallet is
 * not built - so [cost] is currently a label on a button and a number this game has committed to.
 *
 * Prices live beside the thing they price rather than in the screen that draws it, because the
 * screen is not the only thing that will need them: a wallet has to check a balance before the
 * move is made, and both have to be looking at the same number.
 */
enum class Hint(val cost: Int) {
    /**
     * One more cell of the word the player is closest to finishing - and, when their path went
     * wrong, the correction that gets them back to where it was still right.
     */
    LETTER(cost = 100),

    /**
     * That same word, all of it, which finishes it.
     *
     * Two and a half letters' worth rather than one, because it ends the word outright: a player
     * who buys letters is still solving, and a player who buys a word has stopped.
     */
    WORD(cost = 250),
}
