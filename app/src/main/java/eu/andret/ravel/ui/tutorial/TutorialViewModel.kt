package eu.andret.ravel.ui.tutorial

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import eu.andret.ravel.RavelApplication
import eu.andret.ravel.game.Puzzle
import eu.andret.ravel.game.Tutorial
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which of the tutorial's texts is on screen. */
internal enum class TutorialStep {
    /** The first word, its first letter outlined. */
    FIRST,

    /** The player is asked to trace the decoy. */
    DECOY,

    /** The decoy was traced and refused; the text says why, and a button moves on. */
    DECOY_TRACED,

    /**
     * How to correct a path, with a path on the board to correct - the refused decoy, when there
     * was one. Passed by cutting a path back ([TutorialViewModel.cutBack]), not by finding a word;
     * the second word's first letter is outlined from here on.
     */
    SECOND,

    /** The rest of the board, alone - the second word still outlined until it is found. */
    LAST,

    DONE,
}

/** How far the player has got with the decoy - the one part of the lesson the board cannot hold. */
internal enum class DecoyState { WAITING, TRACED, DISMISSED }

/**
 * Everything the tutorial screen draws from, in one value so the board, the text and the button can
 * never come from two different moments.
 *
 * @param puzzle the board as it stands, with the next guided word's first letter outlined.
 * @param corrected whether the player has cut a path back on the correction step, which is what
 *   moves the lesson from [TutorialStep.SECOND] to [TutorialStep.LAST].
 */
internal data class Lesson(
    val puzzle: Puzzle,
    val decoy: Tutorial.Decoy,
    val decoyState: DecoyState,
    val corrected: Boolean = false,
) {
    val step: TutorialStep get() = TutorialViewModel.stepOf(puzzle, decoy, decoyState, corrected)
}

/**
 * The tutorial's board being played.
 *
 * The rules are the real ones - every move goes through [Puzzle.withPath] like on any other board -
 * so what the player learns here is what the game will actually accept. The only additions are the
 * outline on the next word's first letter for the first [Tutorial.GUIDED_WORDS] words, which is the
 * hint outline the board already knows how to draw, put there for free, the decoy step after the
 * first word, and the correction step after it, passed by doing what its text says.
 *
 * A view model rather than remembered state, so turning the phone in the middle does not start the
 * lesson over. It belongs to the activity and so outlives a finished tutorial; what starts a replay
 * from the options tab on a fresh board is the collector in `init`, which deals a new one every time
 * the tutorial becomes due - in whichever language the game is in by then.
 */
class TutorialViewModel(application: Application) : AndroidViewModel(application) {
    private val ravel = application as RavelApplication

    private val tutorial = ravel.tutorial

    private val _lesson = MutableStateFlow(fresh())

    internal val lesson: StateFlow<Lesson> = _lesson.asStateFlow()

    /** Whether the board may buzz, as on every other board. */
    val hapticsEnabled: StateFlow<Boolean> = ravel.haptics.enabled

    init {
        viewModelScope.launch {
            tutorial.pending.collect { due -> if (due) _lesson.value = fresh() }
        }
    }

    fun trace(cells: List<Int>) = move { it.withPath(cells) }

    fun tap(cell: Int) = move { it.tapped(cell) }

    /** See [Puzzle.cleared]. */
    fun deselect() = move { it.cleared() }

    /** The button under the decoy's explanation; see [dismissed]. */
    fun dismissDecoy() = _lesson.update(::dismissed)

    /** Finishing and skipping are one answer: done, until asked for again. */
    fun finish() = tutorial.markDone()

    private fun move(change: (Puzzle) -> Puzzle) = _lesson.update { lesson ->
        val next = moved(lesson, change(lesson.puzzle))
        next.copy(puzzle = guided(next.puzzle, next.step))
    }

    private fun fresh(): Lesson {
        val language = ravel.language.current.value
        val puzzle = Puzzle(Tutorial.boardFor(language))
        val lesson = Lesson(puzzle, Tutorial.decoyFor(language), DecoyState.WAITING)
        return lesson.copy(puzzle = guided(puzzle, lesson.step))
    }

    internal companion object {
        /**
         * [lesson] past the decoy's explanation. The refused path stays on the board: the next
         * step is about correcting a path, and this one is there to be corrected. It used to be
         * cleared here, which left the correction step explaining a gesture with nothing on the
         * board to try it on.
         */
        fun dismissed(lesson: Lesson): Lesson {
            val dismissed = lesson.copy(decoyState = DecoyState.DISMISSED)
            return dismissed.copy(puzzle = guided(lesson.puzzle, dismissed.step))
        }

        /**
         * [lesson] after a move left its board as [puzzle], with the decoy and the correction
         * brought up to date. The outline is left to [guided], which needs the step this decides.
         */
        fun moved(lesson: Lesson, puzzle: Puzzle): Lesson = lesson.copy(
            puzzle = puzzle,
            decoyState = traced(lesson.decoyState, lesson.decoy, puzzle),
            corrected = lesson.corrected ||
                lesson.step == TutorialStep.SECOND && cutBack(lesson.puzzle.path, puzzle.path),
        )

        /**
         * Whether [after] is [before] cut back: a shorter start of it, the empty path included.
         *
         * That is exactly what the two gestures the correction step names produce - sliding back
         * along the trail and tapping one of its letters - and what tapping its first letter does.
         * Clearing the path with a tap off the board passes too, since an empty path cannot say how
         * it got empty; so does finding a word, which empties the path the same way. A new path
         * drawn somewhere else does not: it replaced the old one rather than correcting it, and it
         * is itself a path that can now be cut.
         *
         * Not "a letter of the decoy went": that is true of nearly every move, and the step would
         * pass without the player ever making the correction it teaches.
         */
        fun cutBack(before: List<Int>, after: List<Int>): Boolean =
            after.size < before.size && before.subList(0, after.size) == after

        /**
         * [puzzle] with the first letter of the next guided word outlined, or nothing outlined.
         *
         * Outlined on every step where the player is looking for a word, the last included: the
         * second word's outline comes up with the correction step and stays until the word is
         * found, so passing that step does not take the help away halfway through a word. Not on
         * the decoy's two steps: while the decoy is being asked for, an outline on a real word's
         * first letter would be pointing somewhere else than the text, and the decoy cannot be
         * outlined itself: the outline is a hint, and hints belong to the answer.
         *
         * "Next" is the first unfound word in teaching order, not the word the step text is about:
         * a player who finds the second word first has still found one, and is told about the
         * first one next. The outline goes on the start because a word is only accepted traced
         * from its start, which is the rule the second step puts into words.
         */
        fun guided(puzzle: Puzzle, step: TutorialStep): Puzzle {
            val next = puzzle.unfound.firstOrNull()
            val outline = if (
                step in OUTLINED_STEPS &&
                next != null &&
                next < Tutorial.GUIDED_WORDS
            ) {
                mapOf(next to 1)
            } else {
                emptyMap()
            }
            return puzzle.copy(hints = outline)
        }

        /** The steps that outline the next guided word's first letter; see [guided]. */
        private val OUTLINED_STEPS = setOf(TutorialStep.FIRST, TutorialStep.SECOND, TutorialStep.LAST)

        /**
         * The step a board is at: whether it has given up a word at all, and after that where the
         * decoy and the correction stand.
         *
         * Past the first word, every step comes from the lesson's own state and none from how many
         * words are found. It used to be counted - one word found meant the correction step, two
         * meant the last - and a player who found a second word while the decoy's explanation was
         * up, or straight after the first, never saw the correction step at all. Its text is the
         * one place the tutorial says how to undo a path.
         *
         * The decoy step only comes up while the decoy can still be traced and still means
         * something: none of its cells taken by a found word, and a word of its length still to
         * find. A player who wanders off and finds the other words first simply never meets it,
         * rather than being asked for something the board no longer allows - the lesson bends to
         * the order the player chose, as the outline already does. The correction step does not
         * bend: it waits for a cut, on any path. A player who never makes one can still finish the
         * board, and [TutorialStep.DONE] comes before every other step.
         */
        fun stepOf(
            puzzle: Puzzle,
            decoy: Tutorial.Decoy,
            decoyState: DecoyState,
            corrected: Boolean,
        ): TutorialStep = when {
            puzzle.isComplete -> TutorialStep.DONE
            puzzle.found.isEmpty() -> TutorialStep.FIRST
            decoyState == DecoyState.TRACED -> TutorialStep.DECOY_TRACED
            decoyState == DecoyState.WAITING && stillTraceable(puzzle, decoy) -> TutorialStep.DECOY
            !corrected -> TutorialStep.SECOND
            else -> TutorialStep.LAST
        }

        /**
         * Whether [puzzle]'s marked path is the decoy, which moves the lesson on.
         *
         * Read off the path the board kept after refusing the word: [Puzzle.withPath] keeps a
         * traced path that is not an answer marked on the board, so a refused decoy is exactly a
         * path equal to one of its placements. Only noticed while the step is asking for it - a
         * player who traces it by accident on the first step has not been told anything yet.
         */
        fun traced(state: DecoyState, decoy: Tutorial.Decoy, puzzle: Puzzle): DecoyState =
            if (
                state == DecoyState.WAITING &&
                stillTraceable(puzzle, decoy) &&
                puzzle.found.isNotEmpty() &&
                puzzle.path in decoy.placements
            ) {
                DecoyState.TRACED
            } else {
                state
            }

        private fun stillTraceable(puzzle: Puzzle, decoy: Tutorial.Decoy): Boolean =
            puzzle.unfound.any { puzzle.board.solution[it].word.length == decoy.word.length } &&
                decoy.placements.any { cells -> cells.none(puzzle.solvedCells::contains) }
    }
}
