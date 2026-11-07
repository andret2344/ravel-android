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

    /** The second word, outlined like the first. */
    SECOND,

    /** The last word, with no outline. */
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
 */
internal data class Lesson(
    val puzzle: Puzzle,
    val decoy: Tutorial.Decoy,
    val decoyState: DecoyState,
) {
    val step: TutorialStep get() = TutorialViewModel.stepOf(puzzle, decoy, decoyState)
}

/**
 * The tutorial's board being played.
 *
 * The rules are the real ones - every move goes through [Puzzle.withPath] like on any other board -
 * so what the player learns here is what the game will actually accept. The only additions are the
 * outline on the next word's first letter for the first [Tutorial.GUIDED_WORDS] words, which is the
 * hint outline the board already knows how to draw, put there for free, and the decoy step between
 * the two guided words.
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

    /** The button under the decoy's explanation: the refused path goes, and the second word is next. */
    fun dismissDecoy() = _lesson.update { lesson ->
        val dismissed = lesson.copy(decoyState = DecoyState.DISMISSED)
        dismissed.copy(puzzle = guided(lesson.puzzle.cleared(), dismissed.step))
    }

    /** Finishing and skipping are one answer: done, until asked for again. */
    fun finish() = tutorial.markDone()

    private fun move(change: (Puzzle) -> Puzzle) = _lesson.update { lesson ->
        val puzzle = change(lesson.puzzle)
        val decoyState = traced(lesson.decoyState, lesson.decoy, puzzle)
        val next = lesson.copy(puzzle = puzzle, decoyState = decoyState)
        next.copy(puzzle = guided(puzzle, next.step))
    }

    private fun fresh(): Lesson {
        val language = ravel.language.current.value
        val puzzle = Puzzle(Tutorial.boardFor(language))
        val lesson = Lesson(puzzle, Tutorial.decoyFor(language), DecoyState.WAITING)
        return lesson.copy(puzzle = guided(puzzle, lesson.step))
    }

    internal companion object {
        /**
         * [puzzle] with the first letter of the next guided word outlined, or nothing outlined.
         *
         * Outlined only on the two steps that guide a word. While the decoy is being asked for,
         * an outline on a real word's first letter would be pointing somewhere else than the text,
         * and the decoy cannot be outlined itself: the outline is a hint, and hints belong to the
         * answer.
         *
         * "Next" is the first unfound word in teaching order, not the word the step text is about:
         * a player who finds the second word first has still found one, and is told about the
         * first one next. The outline goes on the start because a word is only accepted traced
         * from its start, which is the rule the second step puts into words.
         */
        fun guided(puzzle: Puzzle, step: TutorialStep): Puzzle {
            val next = puzzle.unfound.firstOrNull()
            val outline = if (
                (step == TutorialStep.FIRST || step == TutorialStep.SECOND) &&
                next != null &&
                next < Tutorial.GUIDED_WORDS
            ) {
                mapOf(next to 1)
            } else {
                emptyMap()
            }
            return puzzle.copy(hints = outline)
        }

        /**
         * The step a board is at, by how many words it has given up and where the decoy stands.
         *
         * The decoy step only comes up while the decoy can still be traced and still means
         * something: none of its cells taken by a found word, and a word of its length still to
         * find. A player who wanders off and finds the other words first simply never meets it,
         * rather than being asked for something the board no longer allows - the lesson bends to
         * the order the player chose, as the outline already does.
         */
        fun stepOf(puzzle: Puzzle, decoy: Tutorial.Decoy, decoyState: DecoyState): TutorialStep = when {
            puzzle.isComplete -> TutorialStep.DONE
            puzzle.found.isEmpty() -> TutorialStep.FIRST
            decoyState == DecoyState.TRACED -> TutorialStep.DECOY_TRACED
            decoyState == DecoyState.WAITING && stillTraceable(puzzle, decoy) -> TutorialStep.DECOY
            puzzle.found.size < Tutorial.GUIDED_WORDS -> TutorialStep.SECOND
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
