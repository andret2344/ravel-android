package eu.andret.ravel.game

/**
 * What solving pays, and what spending costs - the two halves of the same economy, kept in one file
 * so nobody tunes one without seeing the other.
 *
 * Prices are the same in every mode. A daily challenge is not worth more or less than a level, and
 * charging differently would push players towards whichever mode was cheapest rather than whichever
 * they wanted to play - see [Hint] for the prices themselves.
 */
object Rewards {
    /**
     * Coins for finishing a board. The same for any board, of any size, in any mode.
     *
     * Flat on purpose. Paying by size would have players picking modes by what they pay rather
     * than by what they want to play, and the size is not the player's choice anyway - it falls
     * out of the puzzle's id.
     *
     * Deliberately small against [Hint]'s prices: ten boards for a letter, twenty-five for a word.
     * That also closes the obvious hole - a board solved entirely by bought words costs at least a
     * thousand coins and pays ten - so there is no way to farm coins by buying the answer.
     */
    const val FOR_COMPLETING = 10

    /**
     * The balance after paying [cost], or null when there is not enough.
     *
     * Null rather than a clamped balance or an exception: "you cannot afford this" is an answer the
     * caller has to act on - by sending the player to the shop - and not an error.
     */
    fun afterSpending(balance: Int, cost: Int): Int? =
        if (balance >= cost) balance - cost else null
}
