package eu.andret.ravel.game

/**
 * The two ways a player can buy their way past a word they cannot see.
 *
 * They differ in what they give away, and the prices say so: a letter keeps the player working,
 * while a word takes the puzzle off their hands.
 *
 * Prices live beside the thing they price rather than in the screen that draws it, because the
 * screen is not the only thing that will need them: [eu.andret.ravel.settings.Wallet] has to check a
 * balance before the move is made, and both have to be looking at the same number.
 *
 * ### Why these are a quarter of what they were
 *
 * They were 100 and 250, and they were tuned to feel expensive. That was answering the wrong
 * question. This game hides a word behind nothing but its length, and a player who is stuck is not
 * stuck because they have not tried hard enough - they are stuck because the only clue is a number.
 * Pricing the way out at four solved boards did not make the game harder, it made being stuck last
 * longer, and time spent stuck on a board with no new information is not difficulty. It is grinding
 * for the coins to leave.
 *
 * What was kept exactly is the ratio between the two, 2.5, because that is the part that is really a
 * rule rather than a number. It puts the break-even at two letters: two cost less than a word, three
 * cost more. So a player who needs a nudge buys letters and a player who is beaten buys the word,
 * and which of those they are is decided by the board rather than by arithmetic. Moving either price
 * without the other moves that break-even, which is the actual design here.
 */
enum class Hint(val cost: Int) {
    /**
     * One more cell of the word the player is closest to finishing - and, when their path went
     * wrong, the correction that gets them back to where it was still right.
     */
    LETTER(cost = 40),

    /**
     * That same word, all of it, which finishes it.
     *
     * Two and a half letters' worth rather than one, because it ends the word outright: a player
     * who buys letters is still solving, and a player who buys a word has stopped.
     */
    WORD(cost = 100),
}
