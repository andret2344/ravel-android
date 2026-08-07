package eu.andret.ravel.ui.level

/**
 * What a level paid the moment it was finished, and what it cost to finish.
 *
 * Reported rather than left for the screen to work out, because by the time the screen could ask,
 * the answer has moved on: the balance is already the new balance and the ladder has already moved
 * on to the next level. Only the code that did the paying knows what the payment was.
 *
 * @param level the level just beaten, which is not the one the player is on any more.
 * @param coins what it paid, or zero for a level that was already behind the player - a board
 *   restored onto the screen after the fact can reach the end again, and that is not a level beaten.
 * @param seconds how long it took, summed over every sitting it took to finish it.
 */
data class LevelCompletion(
    val level: Int,
    val coins: Int,
    val seconds: Int,
)

/**
 * What the level being played has cost in hints so far, across every visit to it.
 *
 * Named for the buying rather than for the thing bought, like every other pair of these counts: a
 * `words` on its own is ambiguous between the words a player paid to be shown and the words a board
 * contains, and both numbers exist.
 */
data class LevelCost(
    val boughtLetters: Int = 0,
    val boughtWords: Int = 0,
)
