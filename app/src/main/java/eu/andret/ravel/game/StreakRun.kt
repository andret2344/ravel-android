package eu.andret.ravel.game

/**
 * The run of days a streak counts, and the one gap in it a player may buy back.
 *
 * Pure arithmetic over two sets of [DailyDate] ordinals - the days solved and the days repaired -
 * so every rule here is testable without a device. `StoredDailyProgress` feeds it what is on disk
 * and stores what it answers; nothing else in the app counts a streak.
 *
 * ## What a repaired day is
 *
 * A day the player missed and then paid for, the next day, with [Streak.REPAIR_PRICE] coins or one
 * rewarded ad. It **bridges** the run - the days either side of it count as unbroken - and it is
 * **not counted** in it: the run stays the length it was, the missed day pays nothing, and the
 * ladder's position does not move. "N days in a row" therefore still means N days the player
 * actually solved; the repair only stops a gap from setting it back to zero.
 *
 * ## When a repair is on offer - [repairable]
 *
 * - **Only yesterday, and only today.** The gap has to be exactly one day, the day before today,
 *   and the day before that has to have been solved. Two missed days in a row lose the run, and
 *   a gap not repaired by the following midnight stays a gap - otherwise a player back after a
 *   month could buy a month-old run back with one ad, and the run would stop meaning anything.
 * - **Once per cycle.** A repair belongs to the cycle of the solved day after it, and that cycle
 *   cannot take a second one. Two per cycle were asked for and then cut to one: a run kept alive
 *   through two gaps in seven days is five days a week, which is not "in a row" by any reading.
 * - **Not after an explicit no.** Declining is final for that gap, so the offer does not come back
 *   on every visit to the tab.
 *
 * Solving today before repairing yesterday is allowed and leaves the repair on offer: a player who
 * went straight to the board from a reminder has not refused anything. Today's board then paid the
 * first rung of a new run, and the repair does not top that up - it restores the count, not coins.
 */
object StreakRun {
    /**
     * How long the run is that ends today or yesterday, repaired days passed over and not counted.
     *
     * Yesterday still counts as an end, because a run the player has not yet had a chance to break
     * today is not broken - it is waiting. See `DailyProgressState.streak`.
     */
    fun length(solved: Set<Long>, repaired: Set<Long>, today: Long): Int =
        walkBack(from = endOf(solved, today), solved = solved, repaired = repaired).length

    /** Whether the run [length] counts passes over a repaired day, which the run card marks. */
    fun includesRepair(solved: Set<Long>, repaired: Set<Long>, today: Long): Boolean =
        walkBack(from = endOf(solved, today), solved = solved, repaired = repaired).repaired

    /**
     * The best run anywhere in the history, by the same rule as [length]: repaired days bridge, and
     * only solved days count.
     */
    fun longest(solved: Set<Long>, repaired: Set<Long>): Int {
        val days = solved + repaired
        return days.maxOfOrNull { start ->
            if (DailyDate.shifted(start, -1) in days) {
                0
            } else {
                var day = start
                var run = 0
                while (day in days) {
                    if (day in solved) run++
                    day = DailyDate.shifted(day, 1)
                }
                run
            }
        } ?: 0
    }

    /**
     * The missed day that can still be bought back today, or null when there is none.
     *
     * @param declined the gap the player last said no to, or null. A gap declined is not offered
     *   again.
     */
    fun repairable(solved: Set<Long>, repaired: Set<Long>, declined: Long?, today: Long): Long? {
        val missed = DailyDate.shifted(today, -1)
        val before = DailyDate.shifted(today, -2)
        if (missed in solved || missed in repaired || before !in solved || missed == declined) {
            return null
        }
        val run = walkBack(from = before, solved = solved, repaired = repaired)
        // The new repair would sit before solved day `run.length + 1`, and a repair belongs to the
        // cycle of the solved day after it. The last one sat before day `length - sinceRepair + 1`.
        // Same cycle index, same cycle - and the cycle already has its one.
        if (run.repaired) {
            val lastCycle = (run.length - run.sinceRepair) / Streak.CYCLE
            val nextCycle = run.length / Streak.CYCLE
            if (lastCycle == nextCycle) return null
        }
        return missed
    }

    /**
     * What a repair would save: the run that ended the day before the gap, in days solved.
     *
     * Zero when nothing is [repairable], which is what keeps a screen from offering to save a run of
     * nothing.
     */
    fun stake(solved: Set<Long>, repaired: Set<Long>, declined: Long?, today: Long): Int {
        if (repairable(solved, repaired, declined, today) == null) return 0
        return walkBack(from = DailyDate.shifted(today, -2), solved = solved, repaired = repaired).length
    }

    /** Today when it is solved, otherwise yesterday - the two days a live run can end on. */
    private fun endOf(solved: Set<Long>, today: Long): Long =
        if (today in solved) today else DailyDate.shifted(today, -1)

    /**
     * One run read backwards from [from].
     *
     * @property length solved days in it.
     * @property repaired whether it passes over a repaired day.
     * @property sinceRepair solved days after the latest repaired day, or [length] when there is
     *   none.
     */
    private data class Walk(val length: Int, val repaired: Boolean, val sinceRepair: Int)

    private fun walkBack(from: Long, solved: Set<Long>, repaired: Set<Long>): Walk {
        var day = from
        var length = 0
        var sinceRepair = 0
        var sawRepair = false
        while (day in solved || day in repaired) {
            if (day in solved) {
                length++
                if (!sawRepair) sinceRepair++
            } else {
                sawRepair = true
            }
            day = DailyDate.shifted(day, -1)
        }
        return Walk(length = length, repaired = sawRepair, sinceRepair = sinceRepair)
    }
}
