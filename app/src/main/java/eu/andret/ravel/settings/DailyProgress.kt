package eu.andret.ravel.settings

import eu.andret.ravel.game.DailyDate
import eu.andret.ravel.game.Hint
import eu.andret.ravel.game.WordResult
import eu.andret.ravel.generator.Language
import kotlinx.coroutines.flow.StateFlow

/**
 * How a day went: what it cost the player to finish it, and how long they were looking at it.
 *
 * The two hint counts are kept apart rather than summed into "hints used", because they are not the
 * same admission: a letter is a nudge and a word is the answer, and a player looking back at a
 * month wants to know which of the two they have been reaching for.
 *
 * Both are named for the buying and not for the thing bought. There are two kinds of "words" in this
 * file - the ones the player paid to be shown, and the ones a board contains - and they were both
 * called `words` until a total was added that mixed them. The names say which is which now.
 *
 * @param seconds time with the board actually on screen, summed across every visit, or null for a
 *   day recorded before anything was timing them. Foreground time and not wall-clock: a board left
 *   open overnight took ten minutes and a night's sleep, and only one of those is the answer to
 *   "how long did it take you".
 */
data class DayResult(
    val boughtLetters: Int = 0,
    val boughtWords: Int = 0,
    val seconds: Int? = null,
    /**
     * How many words the day's board held, written down when it was solved, or null when nobody
     * counted.
     *
     * **Null and not zero**, on the same argument as [seconds] and for a sharper reason. A solved
     * board held at least one word, so zero is a value this field can never legitimately take on a
     * finished day - which made it a perfect stand-in for "unknown" and a terrible one, because it
     * is also a number. It joined the sum in [DailyProgressState.totalWordsFound] like any other,
     * dragging a lifetime total down by exactly as much as it did not know, with nothing anywhere
     * able to tell the two apart. Null is a state the sum can skip and a screen could qualify.
     *
     * Null while a day is still running, and on any day recorded before boards started counting
     * their words. **The second kind cannot be repaired**, which is the whole reason to store this
     * at all rather than derive it: regenerating the board answers with what the *current* generator
     * builds for that date, and that is not the board that was solved - board identity is
     * (id, generator version), see [eu.andret.ravel.game.PuzzleId]. A count written down at the
     * moment of solving is a record of what happened; a count derived later is a statement about
     * today's generator wearing an old date.
     *
     * The cheaper reason points the same way: deriving it means running the generator - seconds of
     * work and a dictionary in memory - once per day of history, and a total that costs a second per
     * day is a total no screen can show.
     *
     * On a solved day this is also the count of words *found*, since a day is only solved when the
     * board is empty of them. That is what lets [DailyProgressState.totalWordsFound] sum it.
     */
    val wordCount: Int? = null,
)

/**
 * A board part-way through, reduced to the least that has to be written down.
 *
 * The grid itself is not here. A [eu.andret.ravel.game.PuzzleId] regenerates it exactly, so saving
 * it would be saving a derived thing - and a large one, since the letters, the walls and the
 * solution together are most of a board. What cannot be derived is what the player did to it.
 *
 * Everything here indexes into the regenerated board, which is why [eu.andret.ravel.game.PuzzleId]
 * documents board identity as (id, generator version): a generator change reissues the board, and a
 * saved path would then point at cells that have moved. See the checks where this is restored.
 *
 * @param language the language the grid was generated from, which is part of that identity and was
 *   the one piece of it missing. An id and a generator version name a board only once the dictionary
 *   is fixed: the same day in Polish and in English is two unrelated grids, so a Polish board's found
 *   words and revealed cells laid over an English one mark the wrong words and point at the wrong
 *   cells. Nothing throws - the indices are all in range - which is why it has to be checked rather
 *   than caught.
 */
data class SavedBoard(
    val ordinal: Long,
    val language: Language,
    val found: Set<Int>,
    val path: List<Int>,
    val hints: Map<Int, Int>,
    /**
     * How many words the board holds in total.
     *
     * The only field here that is not the player's doing. It is stored because the menu wants to
     * say "two of five" on a tile, and the alternative is running the generator - seconds of work
     * and a dictionary in memory - to answer a question about a screen the player is not on.
     */
    val wordCount: Int,
)

/**
 * A board just solved, kept only for as long as its result is still worth telling anyone.
 *
 * This is what the share button is built from, and it exists as a slot of its own rather than as
 * fields on [DayResult] because of how long it is useful for. A day's *cost* is history and belongs
 * in the calendar forever; how the solve went is a thing the player posts on the day and then never
 * looks at again. Keeping [marks] on every [DayResult] would put a list per word into every day of
 * a player's history - the one map in this file that grows without limit - to answer a question
 * nobody asks about last March.
 *
 * So there is one slot, exactly like [DailyProgressState.board], and it is checked against the day
 * being asked about rather than cleaned up on a timer: see [DailyProgressState.shareFor]. A card
 * left over from yesterday answers nothing today and costs a few dozen bytes until the next solve
 * overwrites it, which is cheaper than anything that would have to wake up at midnight to delete it.
 *
 * @param ordinal the day this describes, which is what makes the single slot safe.
 * @param language the dictionary the grid was built from. Stored rather than read at the moment of
 *   sharing for the reason [SavedBoard.language] is stored: the same date in Polish and in English
 *   is two unrelated grids, so a card shared after a language switch would otherwise label a Polish
 *   solve as an English one - and the whole reason to print the language is that two players
 *   comparing times on different dictionaries are comparing nothing.
 * @param size the grid's width, which is also its height. Recorded and not derived from [ordinal]
 *   through [eu.andret.ravel.game.PuzzleSpec], on the same argument as [DayResult.wordCount]: the
 *   spec is arithmetic over the id and moving a constant in it would silently restate what an
 *   already-solved day looked like. One int is cheaper than deciding each time whether that has
 *   happened yet.
 * @param words one entry per word, in the order the lengths are shown in - see
 *   [eu.andret.ravel.game.wordResults]. Its size is the board's word count, which is why nothing
 *   here stores that separately.
 */
data class ShareCard(
    val ordinal: Long,
    val language: Language,
    val size: Int,
    val words: List<WordResult>,
) {
    init {
        require(words.isNotEmpty()) { "A solved board holds at least one word" }
        require(size > 0) { "A board with no width was never on screen: $size" }
    }

    /** How many words the board held, which is one entry each. */
    val wordCount: Int get() = words.size
}

/**
 * Which days have been solved, how each of them went, and how many run up to today without a gap.
 *
 * All of it is one history across every language, unlike the main mode's ladder - see
 * [LevelProgressState]. A calendar is a calendar: a day is solved or it is not, and letting it be
 * solved once per language would pay for the same date twice out of one wallet and split a streak
 * that only means anything as a single run.
 *
 * @param solved every solved day by [DailyDate] ordinal, each with what it cost. The calendar draws
 *   from this directly, so it is the whole history and not a window: a month the player scrolls
 *   back to has to know what happened in it.
 * @param pending days started and not yet finished, with what they have cost so far. Kept apart
 *   from [solved] because an unfinished day is not a day the streak counts, and merging the two
 *   would make every board the player ever opened look like a board they beat.
 * @param board the day in progress, always in the language being played now. A board saved in
 *   another one is dropped on the way in rather than handed over - see [StoredDailyProgress] - so
 *   nothing above this line has to know the language exists.
 * @param streak days solved consecutively, ending today or yesterday. Yesterday still counts,
 *   because a streak the player has not yet had a chance to break today is not broken - it is
 *   waiting. It reaches zero only when the day before today was missed too.
 * @param longest the best run so far, kept because it is the one number a player who has just lost
 *   a streak still owns.
 * @param share the last board solved, for as long as it is still the day it was solved on - see
 *   [ShareCard]. One slot, like [board], and read through [shareFor] for the same reason.
 */
data class DailyProgressState(
    val solved: Map<Long, DayResult> = emptyMap(),
    val pending: Map<Long, DayResult> = emptyMap(),
    val board: SavedBoard? = null,
    val share: ShareCard? = null,
    val streak: Int = 0,
    val longest: Int = 0,
) {
    fun isSolved(ordinal: Long): Boolean = ordinal in solved

    /**
     * The board [ordinal] was left in the middle of, or null when there is none to go back to.
     *
     * Part of the observed state rather than something to be fetched, because the menu has to know:
     * a tile that said "Play" over a board with three words already on it would be inviting the
     * player to start something they are in the middle of.
     *
     * One slot, so a board belonging to any other day is no board at all here. Boards are only ever
     * saved for the day being played, and that day stops being playable at midnight.
     */
    fun boardFor(ordinal: Long): SavedBoard? = board?.takeIf { it.ordinal == ordinal }

    /** Whether [ordinal] has a board part-way through: started, touched, and not finished. */
    fun isStarted(ordinal: Long): Boolean = !isSolved(ordinal) && boardFor(ordinal) != null

    /**
     * What [ordinal]'s solve looked like, or null when there is nothing to tell about it.
     *
     * Null on every day but the last one solved, which is what makes the share button a thing that
     * appears on the day and quietly stops being offered afterwards. That is deliberate rather than
     * a limitation being tolerated: a result is worth posting while everyone else is on the same
     * board, and a button that offered to publish a Tuesday in March would be offering something
     * nobody on the other end can play any more.
     *
     * Checked against [ordinal] rather than trusted, exactly as [boardFor] is: the slot holds
     * whatever was solved last, and yesterday's card laid over today's date would report a board
     * the player has not finished.
     */
    fun shareFor(ordinal: Long): ShareCard? = share?.takeIf { it.ordinal == ordinal }

    /** How [ordinal] went, or null when it was never finished. */
    fun resultOf(ordinal: Long): DayResult? = solved[ordinal]

    /**
     * What [ordinal] has cost so far, finished or not.
     *
     * The count belongs to the day and not to the board, because the board is thrown away every
     * time its screen closes. A tally kept on the board would let a player buy a word, back out and
     * come back to a counter at zero.
     */
    fun costOf(ordinal: Long): DayResult =
        solved[ordinal] ?: pending[ordinal] ?: DayResult()

    /**
     * Lifetime totals, summed on demand rather than kept as counters.
     *
     * A running total is a second copy of a number that is already in [solved], and the two can
     * disagree - which is the same argument that keeps the streak derived. Summing a few thousand
     * days costs nothing next to a frame.
     */
    val totalSolved: Int get() = solved.size
    val totalBoughtLetters: Int get() = solved.values.sumOf(DayResult::boughtLetters)
    val totalBoughtWords: Int get() = solved.values.sumOf(DayResult::boughtWords)
    val totalSeconds: Int get() = solved.values.sumOf { it.seconds ?: 0 }

    /**
     * Words found across every day finished, which is every word those boards held.
     *
     * Days whose count was never written down are skipped rather than read as zero - see
     * [DayResult.wordCount]. The arithmetic is identical either way, and that is fine: the point of
     * the change is not a different number but that the number is now a floor the type admits to,
     * rather than an exact figure that was quietly wrong. A screen that ever needs to say so can
     * ask `solved.values.all { it.wordCount != null }`; nothing does, so nothing here holds it.
     */
    val totalWordsFound: Int get() = solved.values.sumOf { it.wordCount ?: 0 }
}

/**
 * The player's history with the daily challenge, and whatever board they are in the middle of.
 *
 * An interface because there is exactly one thing every screen here needs and several ways to store
 * it, and because the storage is the part most likely to be replaced - a file today, an account
 * later. Nothing above this line knows which.
 *
 * The streak is derived from [DailyProgressState.solved] rather than stored beside it, because two
 * numbers that can disagree eventually will: a crash between writing "solved today" and writing
 * "streak is now four" would leave a player permanently one short, and there is no way to notice.
 * Counting backwards from today costs one map lookup per day of the streak, which is nothing.
 */
interface DailyProgress {
    val state: StateFlow<DailyProgressState>

    /**
     * Adds one bought [hint] to [ordinal]'s running cost.
     *
     * Recorded when it is paid for rather than when the board is finished, because a board that is
     * never finished still cost the player something. Days already solved are ignored: their cost
     * is settled, and a second visit cannot add to it.
     */
    fun recordHint(ordinal: Long, hint: Hint)

    /**
     * Adds [seconds] of screen time to [ordinal]'s running cost.
     *
     * Called with a chunk of time that has already elapsed, not with a clock to start, so the store
     * never has to know whether the app is in the foreground. Days already solved are ignored.
     */
    fun recordTime(ordinal: Long, seconds: Int)

    /**
     * Records the day [card] describes as solved, at whatever it has cost so far, and forgets its
     * saved board.
     *
     * @param card what the board that was just finished looked like. It names its own day rather
     *   than taking one beside it, which is the only method here that does: everything a caller
     *   would have passed separately - the day, the word count - is already on the card, and a
     *   second copy of either is a second copy that can disagree. It also carries the marks the
     *   share button needs, which is why this is one call and not two: a count written down by one
     *   method and a list of that many marks written by another are two chances to record a board
     *   nobody played.
     * @return false when it was already recorded, which is how the caller knows not to pay for it
     *   twice - a board can be finished, left and finished again, and the streak only ever buys the
     *   first time. The first finish is the one that happened, and a second pass with hints already
     *   revealed would only ever flatter it.
     */
    fun complete(card: ShareCard): Boolean

    /**
     * Writes down where the player has got to, so closing the screen is not losing the board.
     *
     * There is no matching load: what was saved is in [DailyProgressState.board] from the moment
     * the store is built, because the menu needs it as much as the board screen does.
     */
    fun saveBoard(board: SavedBoard)
}
