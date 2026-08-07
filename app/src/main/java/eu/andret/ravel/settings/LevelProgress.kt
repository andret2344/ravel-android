package eu.andret.ravel.settings

import eu.andret.ravel.game.Hint
import kotlinx.coroutines.flow.StateFlow

/**
 * How far through the main mode the player is **in the language they are playing**, and what the
 * level they are on has cost so far.
 *
 * One number and one board, which is the whole of it. The mode is a ladder walked in order - there
 * is no going back to level three and no jumping to level thirty - so the history of a level that is
 * already behind the player is not something any screen can ask about. Keeping per-level records
 * anyway would be storing a month of data to answer a question the game never asks.
 *
 * Every field here belongs to one language and changes when the player switches. Level seven in
 * Polish and level seven in English are not the same board - the seed picks a path through the
 * generator's search and the dictionary decides where it lands - so they are two ladders, walked
 * independently: twenty levels of Polish and five of English is a player who can go back to the
 * twenty-first Polish level, not one who has to start it again. The lifetime totals are the opposite
 * kind of number and live in [LevelTotals].
 *
 * @param completed the highest level finished in this language, so [current] is the one on offer.
 *   Zero for a player who has never played it, whose first level is one.
 * @param board [current] part-way through, or null when it has not been touched yet. Only ever the
 *   current level: finishing one throws its board away, and no other level can be opened.
 * @param boughtLetters letters bought on [current], across every visit to it.
 * @param boughtWords whole words bought on [current], across every visit to it.
 * @param seconds time spent looking at [current], summed across every visit.
 */
data class LevelProgressState(
    val completed: Int = 0,
    val board: SavedBoard? = null,
    val boughtLetters: Int = 0,
    val boughtWords: Int = 0,
    val seconds: Int = 0,
) {
    /** The only level that can be played: the one after the last one finished. */
    val current: Int get() = completed + 1

    /**
     * [current] part-way through, or null when there is nothing to go back to.
     *
     * Checked against the level being asked about rather than handed over blind, because a board
     * saved before the level was finished can outlive it by one write - and a board from level six
     * put onto level seven would be a path pointing at cells that have moved.
     */
    fun boardFor(level: Int): SavedBoard? = board?.takeIf { it.ordinal == level.toLong() }

    /** Whether [level] is the one on offer. Everything else is either done or locked. */
    fun isCurrent(level: Int): Boolean = level == current

    /*
     * The three costs, asked for by level rather than read off directly.
     *
     * They belong to the current level and to nothing else - a level that is behind the player has
     * no cost to report and one ahead has not been opened - so a screen showing any other level has
     * to be answered with zero rather than with the current level's spending under someone else's
     * number.
     */

    fun boughtLettersFor(level: Int): Int = if (isCurrent(level)) boughtLetters else 0

    fun boughtWordsFor(level: Int): Int = if (isCurrent(level)) boughtWords else 0

    fun secondsFor(level: Int): Int = if (isCurrent(level)) seconds else 0

    /** Whether [current] has been started and left, which is what the tile has to say "finish" to. */
    val started: Boolean get() = boardFor(current) != null
}

/**
 * What the main mode has come to across every language, and every level, ever.
 *
 * Separate from [LevelProgressState] because the two answer questions of different sizes and a
 * single class holding both would not say which was which. "Which level am I on" is about one ladder
 * and changes when the player switches language; "how much of this game have I played" is about the
 * player, and a number that halved because they changed a setting would be reporting a loss that did
 * not happen. Per-level records would answer the second question too, and would be a month of data
 * kept for one screen - which is why these are five counters and not a history.
 *
 * @param levelsCompleted levels finished in every language added together, which is also the count
 *   of boards the mode has produced: the ladder is walked in order and never repeats.
 * @param wordsFound words on every level finished, and [seconds] the time those levels took.
 * @param boughtLetters letters bought anywhere on the ladder, and [boughtWords] whole words. Kept
 *   because the statistics screen counts hints across both modes, and a per-level cost that is
 *   cleared when the level ends leaves nothing to count - these are added on the way past.
 */
data class LevelTotals(
    val levelsCompleted: Int = 0,
    val wordsFound: Int = 0,
    val seconds: Int = 0,
    val boughtLetters: Int = 0,
    val boughtWords: Int = 0,
)

/**
 * The player's place in the main mode, and whatever level they are in the middle of.
 *
 * An interface for the same reason as [DailyProgress]: one thing every screen needs, several ways to
 * store it, and the storage is the part most likely to be replaced by an account later.
 *
 * There is no method for setting the level. It is [LevelProgressState.completed] plus one and
 * nothing else can move it, which is what "no skipping ahead" means in code rather than in a screen
 * that declines to draw a button.
 *
 * Nothing here takes a language. The store is told once which one is being played and follows it
 * from then on, so a screen asking about "the level" is asking about the ladder in front of the
 * player without having to know there are others.
 */
interface LevelProgress {
    /** The ladder being played, which changes under its collectors when the language does. */
    val state: StateFlow<LevelProgressState>

    /** The same store's lifetime figures, which do not. */
    val totals: StateFlow<LevelTotals>

    /** Adds one bought [hint] to the current level's running cost, and to the lifetime count. */
    fun recordHint(level: Int, hint: Hint)

    /** Adds [seconds] of screen time to the current level's running cost. */
    fun recordTime(level: Int, seconds: Int)

    /**
     * Marks [level] as finished, which moves the player on to the next one and clears its cost.
     *
     * @param wordCount how many words the board held, which goes into [LevelTotals.wordsFound] on
     *   the way past. The per-level cost is cleared here; the lifetime totals are the only thing
     *   that survives a level, which is why they are added before the clearing rather than after.
     * @return false when [level] was already behind the player, which is how the caller knows not
     *   to pay for it twice. A board restored onto the screen after the level was completed - a
     *   process killed and rebuilt, a back stack replayed - can reach the end again, and the second
     *   finish is not a level beaten.
     */
    fun complete(level: Int, wordCount: Int): Boolean

    /** Writes down where the player has got to, so closing the screen is not losing the level. */
    fun saveBoard(board: SavedBoard)
}
