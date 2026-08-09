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
     * Under two boards for a letter, four for a word, against [Hint]'s prices.
     *
     * It was ten coins, which made it fourteen boards a week against three hundred coins a day from
     * ads, and at that point solving was not a way to earn anything - it was a thing you did while
     * the real currency came from somewhere else. Raising this to 25 fixed that; the prices in
     * [Hint] then came down by a quarter for a different reason, written up there, and this number
     * did not move with them. It is worth being explicit that not moving it was the decision: what
     * changed is how much a coin buys, not how many coins solving is worth, because the complaint
     * being answered was about the cost of getting unstuck and not about the pay for playing.
     *
     * The hole it used to close is still closed, with room to spare. A board solved entirely by
     * bought words costs at least three hundred coins - the smallest board here is three words - and
     * pays twenty-five, so buying the answer remains the most expensive way to lose money in this
     * game. `PuzzleTest` guards that as an inequality rather than as a figure, which is why cutting
     * the prices did not need it rewritten.
     */
    const val FOR_COMPLETING = 25

    /**
     * Coins for watching one rewarded ad through to the end.
     *
     * Exactly one hint, which is the whole reason for the number: an ad buys a thing the player can
     * name before they watch it, rather than a fraction of one they have to bank. A price they can
     * point at is a trade they can decide about.
     *
     * **Which hint it buys has changed, and the number did not.** It used to be one [Hint.LETTER];
     * with the prices cut it is one [Hint.WORD], and that is a considerably better offer than the
     * one this constant was written for. It was left at 100 anyway, because the alternative - moving
     * it down to 40 to stay pinned to the letter - would have taken the cut being made for the
     * player and handed most of it back. What an ad is worth to the game did not change when hint
     * prices did.
     *
     * The consequence is [ADS_PER_DAY]'s problem and it is spelled out there: the day's three ads
     * are now three whole words rather than one and a bit.
     *
     * Four times what a solved board pays, and that ordering is deliberate. An ad is worth more
     * than a board because it is worth more to the game, and pretending otherwise - paying two
     * coins, say - would make the offer an insult nobody takes, which earns less than not offering
     * it. Four is the number that survived [FOR_COMPLETING] being raised: it was ten times, which
     * made a day of ads worth six days of playing and left solving as decoration. A player who
     * finishes four boards has earned what an ad pays, and that is the ratio worth keeping - an ad
     * is a shortcut past an evening, not past the game.
     *
     * Taken from here and not from the amount the AdMob console attaches to the ad unit. That value
     * is remote configuration: someone changing it in a web form would silently retune the economy
     * of a shipped game, with no build, no review and no way to tell from the code what the number
     * currently is. The reward callback is used as a signal that the ad was watched, and nothing
     * more.
     */
    const val FOR_WATCHING_AD = 100

    /**
     * How many ads a day can be turned into coins.
     *
     * There has to be a ceiling, and it has to be low. Without one the coin economy stops meaning
     * anything - a patient player converts an afternoon into unlimited hints, at which point the
     * prices in [Hint] are decoration and solving a board yourself is the slow way to do it.
     *
     * Three is three hundred coins a day. That used to be three letters or one whole word with
     * change; since the prices in [Hint] came down it is three whole words, or seven letters and a
     * half. Same ceiling, two and a half times the reach - which is the one place the price cut did
     * something nobody asked it to, and the number to watch first if the game turns out to be
     * winnable through the shop.
     *
     * It was left at three on the argument that a board is more words than that, so a day of ads
     * still cannot finish one, and that a player watching three ads has spent three ads' worth of
     * their own attention on it. That argument is weaker than it was and it is worth saying so:
     * lowering this to two is the correction if the game reads as buyable, and it is a cheaper fix
     * than putting the hint prices back up, because it takes the shortcut away from the player who
     * abuses it rather than from the player who was stuck.
     *
     * Raising it is not a monetisation lever, it is a difficulty lever pointed at the wrong axis.
     *
     * Counted per completed watch rather than per ad started, so an ad the player backs out of
     * costs them nothing - see `AdAllowance`.
     */
    const val ADS_PER_DAY = 3

    /**
     * The balance after paying [cost], or null when there is not enough.
     *
     * Null rather than a clamped balance or an exception: "you cannot afford this" is an answer the
     * caller has to act on - by sending the player to the shop - and not an error.
     */
    fun afterSpending(balance: Int, cost: Int): Int? =
        if (balance >= cost) balance - cost else null
}
