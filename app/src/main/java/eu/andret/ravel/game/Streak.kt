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
 * `6 * `[DAY]` + `[SEVENTH]` = 110`, against the seventy a week of solving pays through
 * [Rewards.FOR_COMPLETING]. Showing up is therefore worth roughly as much as playing and not
 * several times more, which is the only version where a player optimising for coins is still a
 * player who opens a board.
 */
object Streak {
    /** How many days the reward ladder runs before it starts over. */
    const val CYCLE = 7

    /** Any of the first six days. */
    const val DAY = 10

    /**
     * The seventh day, which is the one the other six exist to reach.
     *
     * Five days' worth on top of a sixth, so missing day six costs the player day seven as well and
     * they can see that coming from the start of the week. A smaller bonus would make the seventh
     * day a rounding error and the six before it arbitrary.
     */
    const val SEVENTH = 50

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
