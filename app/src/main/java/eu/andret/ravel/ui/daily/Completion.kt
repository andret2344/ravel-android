package eu.andret.ravel.ui.daily

import eu.andret.ravel.settings.ShareCard

/**
 * What the board paid out the moment it was finished.
 *
 * Reported rather than left for the screen to work out, because by the time the screen could ask,
 * the answer has moved on: the balance is already the new balance, and the streak is already the
 * longer streak. Only the code that did the paying knows what the payment was.
 *
 * @param forBoard the flat reward every finished board pays.
 * @param forStreak what the day itself paid, or zero when it paid nothing - a board finished a
 *   second time, or one out of the calendar, which the streak does not buy.
 * @param cycleDay which rung of the week [forStreak] came off, so the dialog can name it.
 * @param streak the run including today, which is the number the player just extended.
 * @param seconds how long the board took, summed over every sitting it took to finish it.
 * @param card what the board looked like, so the dialog can offer to share it. Carried here rather
 *   than read back out of the store for the same reason as everything else on this class: this is
 *   the moment, and the moment is the thing the dialog is about. The menu's copy of the button asks
 *   the store instead, because by then the moment is over and the store is what remembers it.
 */
data class Completion(
    val forBoard: Int,
    val forStreak: Int,
    val cycleDay: Int,
    val streak: Int,
    val seconds: Int,
    val card: ShareCard,
) {
    val total: Int get() = forBoard + forStreak
}
