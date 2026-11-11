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
     * Two and two-thirds boards for a letter, six and two-thirds for a word, against [Hint]'s prices.
     *
     * ### Why fifteen
     *
     * It was 25, and at 25 a player could not run out. A word cost four boards, so anyone stuck less
     * often than once in four boards saw their balance climb for good, and a player who also solved
     * the daily was paid 555 coins a week by the daily alone - five and a half words for seven
     * boards. Nobody in that position ever needed the shop, and the shop is where the game earns.
     *
     * So playing was made cheaper rather than hints dearer. The prices in [Hint] stayed where the
     * complaint about grinding put them, and [FOR_WATCHING_AD] stayed at one whole word; what moved
     * is how much of the game an ad stands in for, from four boards to six and two-thirds. That is
     * the intended trade: a player who is stuck is now one ad away from the word rather than one
     * evening of other boards, and an ad is worth watching because it is worth more of the game.
     * [eu.andret.ravel.game.Streak.DAY] moved with this number, as it always has, and
     * [FOR_COMPLETING_HARD] and [DAILY_GIFT] with them.
     *
     * Raising the hint prices instead was considered and turned down. Counted in boards, cutting
     * this to fifteen already made a word two-thirds dearer; raising the prices on top would have
     * tightened the same screw twice before anyone had measured the first turn, and it would have
     * cut the ad loose from the word it buys.
     *
     * Before that it was ten, which made it fourteen boards a week against three hundred coins a day
     * from ads - solving was not a way to earn anything at all. 25 fixed that, and fifteen keeps it
     * fixed: an ad is still a handful of boards, not a week of them.
     *
     * The hole it used to close is still closed, with room to spare. A board solved entirely by
     * bought words costs at least three hundred coins - the smallest board here is three words - and
     * pays fifteen, so buying the answer remains the most expensive way to lose money in this game.
     * `PuzzleTest` guards that as an inequality rather than as a figure.
     */
    const val FOR_COMPLETING = 15

    /**
     * Coins for finishing a level played in hard mode, with the word lengths and their count hidden.
     *
     * The one exception to [FOR_COMPLETING] being flat, and an exception of a different kind from
     * the ones that note refuses: size and mode are not the player's choice, while this is, and it
     * is a choice to give something up. Paid for levels only - the daily has no hard mode, so a day
     * stays a day for everyone and the streak's parity with playing holds.
     *
     * Twenty-five: ten on top of [FOR_COMPLETING], two-thirds more. Hard mode is a switch a player
     * flips once, so its rate is in practice the rate for every level that player plays from then
     * on. It was 45 against 25, the same shape at the old scale; it came down with
     * [FOR_COMPLETING], by the owner's choice of a flat ten on top rather than the proportional
     * twenty-seven. A larger bonus was once asked for, to make the switch more tempting, and turned
     * down: it would make a [Hint.LETTER] cheaper than a level or two and an ad worth very little,
     * which is the stuck player's way out getting cheap - and being stuck is meant to cost
     * something. Nobody has measured how much harder a board is without its lengths - on a 5x5 with
     * three words, barely - and raising the bonus later is a gift where lowering it is a nerf.
     *
     * **Hints do not forfeit it.** The extra ten is less than the cheapest hint, so buying a hint
     * on a hard level is always a loss next to not buying one - there is no route to the bonus
     * through the shop, and a rule taking it away would be a rule to explain for nothing.
     * `PuzzleTest` holds that inequality.
     */
    const val FOR_COMPLETING_HARD = 25

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
     * Six and two-thirds solved boards, and that ordering is deliberate. An ad is worth more than a
     * board because it is worth more to the game, and pretending otherwise - paying two coins, say -
     * would make the offer an insult nobody takes, which earns less than not offering it. It was
     * ten boards once, which made a day of ads worth six days of playing and left solving as
     * decoration; then four, which made it a shortcut too small to bother with, since a player who
     * finished four boards had earned it anyway. Six and two-thirds is where [FOR_COMPLETING]
     * coming down to fifteen put it, on purpose: an ad stands in for an evening of other boards,
     * which is the only version where watching one is the obvious answer to being stuck.
     *
     * The same 100 is the price of a streak repair - see `Streak.REPAIR_PRICE` - so the modal that
     * offers "100 coins or an ad" is stating an exchange rate the shop already uses.
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
     * What the shop's daily gift pays: one tap, once a day, for nothing but opening the shop.
     *
     * The one coin in the game paid for showing up rather than for playing, which is why it is
     * small. Its job is a reason to look into the shop every day - where the ad tile is - and not
     * income: at ten it is 70 a week against 105 for solving every daily, and under half the
     * cheapest hint a day. It was fifteen against a board worth 25; when [FOR_COMPLETING] came down
     * to fifteen it had to come down too, because a tap that pays what a solved board pays is a
     * game that has stopped being paid for the boards. `PuzzleTest` holds it under half a letter
     * and under a board.
     *
     * Counted by `DailyGift`, on the same day rules as [ADS_PER_DAY]'s allowance.
     */
    const val DAILY_GIFT = 10

    /**
     * The balance after paying [cost], or null when there is not enough.
     *
     * Null rather than a clamped balance or an exception: "you cannot afford this" is an answer the
     * caller has to act on - by sending the player to the shop - and not an error.
     */
    fun afterSpending(balance: Int, cost: Int): Int? =
        if (balance >= cost) balance - cost else null
}
