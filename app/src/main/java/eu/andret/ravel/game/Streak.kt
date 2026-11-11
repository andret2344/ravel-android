package eu.andret.ravel.game

/**
 * What showing up pays: a seven-day cycle that climbs, resets, and climbs again.
 *
 * The shape is the point. A flat daily payment is worth the same whether it is the player's first
 * day back or their sixtieth in a row, so there is nothing to protect and nothing to lose; a cycle
 * that ends in [SEVENTH] gives every week a day worth not missing, and gives a broken streak a cost
 * the player can name. It restarts rather than climbing forever because a reward that keeps growing
 * eventually pays more than the game does, and because a player who breaks a hundred-day streak has
 * lost something they cannot get back inside a week - which is a reason to stop playing, not to
 * start again.
 *
 * These rules are pure and live beside [Rewards] so nobody tunes one without seeing the other.
 * Nothing here knows what a day is, when one ended, or whether it was earned - see `DailyProgress`
 * for that, and [DailyDate] for what a day is called.
 *
 * The sums are deliberately of the same order as the game they are attached to: a full week pays
 * `6 * `[DAY]` + `[SEVENTH]` = 140`, against the 105 a week of solving pays through
 * [Rewards.FOR_COMPLETING]. Showing up is therefore worth roughly as much as playing and not
 * several times more, which is the only version where a player optimizing for coins is still a
 * player who opens a board.
 *
 * [DAY] has always moved with [Rewards.FOR_COMPLETING] - multiplied by two and a half when that
 * was, cut to fifteen when that was - because the ratio between the two is what the paragraph above
 * is about. The week's ratio is 1.33 now, down from 1.57: [SEVENTH] did not follow [DAY] down in
 * proportion, see there.
 *
 * ## Repairing a run
 *
 * A single missed day can be bought back the next day, for [REPAIR_PRICE] or one rewarded ad, once
 * per cycle. The rules and the reasons are on [StreakRun]; this file only holds the price, beside
 * the rewards it has to be read against.
 */
object Streak {
    /** How many days the reward ladder runs before it starts over. */
    const val CYCLE = 7

    /** Any of the first six days, and worth exactly one solved board - see [Rewards]. */
    const val DAY = 15

    /**
     * The seventh day, which is the one the other six exist to reach.
     *
     * More than three days' worth on top of a sixth, so missing day six costs the player day seven
     * as well and they can see that coming from the start of the week. A smaller bonus would make
     * the seventh day a rounding error and the six before it arbitrary.
     *
     * It was `5 * `[DAY], and the rule was the multiple rather than the number. When [DAY] came down
     * to fifteen that rule would have given 75, and fifty was chosen instead: the economy was being
     * made leaner on purpose (see [Rewards.FOR_COMPLETING]), and the week's ratio to solving -
     * guarded by `StreakTest` as a band - drops from 1.57 to 1.33 and stays inside it. It also caps
     * what a broken run costs in coins at 35, which is less than [REPAIR_PRICE]: a repair is bought
     * for the count of days, not for the coins it saves, and that is the honest version of it.
     */
    const val SEVENTH = 50

    /**
     * What buying back one missed day costs in coins - or one rewarded ad instead.
     *
     * Exactly [Rewards.FOR_WATCHING_AD], so the repair offers one exchange rate and not two: "100
     * coins or an ad" is the rate the shop already pays, and a player who has learned one has
     * learned both. `StreakTest` holds the two equal.
     *
     * Deliberately more than a broken run costs in coins (see [SEVENTH]). What a repair saves is
     * the number on the run card, and a price under the coins at stake would make repairing the
     * rational move every time rather than a choice about how much the run is worth to the player.
     *
     * One price for every repair. A second repair in the same cycle costing more was considered and
     * dropped together with the second repair itself: one per cycle needs no escalation.
     */
    const val REPAIR_PRICE = 100

    /**
     * What the [day]th day of a cycle pays, counting from 1.
     *
     * @throws IllegalArgumentException outside `1..`[CYCLE], because a day outside the cycle is a
     *   bug in whoever counted it rather than a day worth nothing.
     */
    fun rewardFor(day: Int): Int {
        require(day in 1..CYCLE) { "Day outside the cycle: $day" }
        return if (day == CYCLE) SEVENTH else DAY
    }

    /**
     * Where a streak of [length] days sits in the cycle, counting from 1.
     *
     * A length of zero - no streak at all - is day zero, and the day the player is *about* to earn
     * is [nextDay]. Keeping those apart matters on the screen: before today is played the strip has
     * to highlight the day being offered, and after it, the day just taken.
     */
    fun dayInCycle(length: Int): Int {
        require(length >= 0) { "Negative streak: $length" }
        return if (length == 0) 0 else (length - 1) % CYCLE + 1
    }

    /** The cycle day one more day of playing would land on, which is what today is worth. */
    fun nextDay(length: Int): Int = dayInCycle(length + 1)

    /** The whole ladder, in order, for a screen that draws all seven at once. */
    val ladder: List<Int> = (1..CYCLE).map(::rewardFor)
}
