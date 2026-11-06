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

/** Which of the tutorial's four texts is on screen. */
internal enum class TutorialStep { FIRST, SECOND, LAST, DONE }

/**
 * The tutorial's board being played.
 *
 * The rules are the real ones - every move goes through [Puzzle.withPath] like on any other board -
 * so what the player learns here is what the game will actually accept. The only addition is the
 * outline on the next word's first letter for the first [Tutorial.GUIDED_WORDS] words, which is the
 * hint outline the board already knows how to draw, put there for free.
 *
 * A view model rather than remembered state, so turning the phone in the middle does not start the
 * lesson over. It belongs to the activity and so outlives a finished tutorial; what starts a replay
 * from the options tab on a fresh board is the collector in `init`, which deals a new one every time
 * the tutorial becomes due - in whichever language the game is in by then.
 */
class TutorialViewModel(application: Application) : AndroidViewModel(application) {
    private val ravel = application as RavelApplication

    private val tutorial = ravel.tutorial

    private val _puzzle = MutableStateFlow(fresh())

    /** The board as it stands, with the next guided word's first letter outlined. */
    val puzzle: StateFlow<Puzzle> = _puzzle.asStateFlow()

    /** Whether the board may buzz, as on every other board. */
    val hapticsEnabled: StateFlow<Boolean> = ravel.haptics.enabled

    init {
        viewModelScope.launch {
            tutorial.pending.collect { due -> if (due) _puzzle.value = fresh() }
        }
    }

    fun trace(cells: List<Int>) = _puzzle.update { guided(it.withPath(cells)) }

    fun tap(cell: Int) = _puzzle.update { guided(it.tapped(cell)) }

    /** Finishing and skipping are one answer: done, until asked for again. */
    fun finish() = tutorial.markDone()

    private fun fresh(): Puzzle = guided(Puzzle(Tutorial.boardFor(ravel.language.current.value)))

    internal companion object {
        /**
         * [puzzle] with the first letter of the next guided word outlined, or nothing outlined once
         * the guided words are found.
         *
         * "Next" is the first unfound word in teaching order, not the word the step text is about:
         * a player who finds the second word first has still found one, and is told about the
         * first one next. The outline goes on the start because a word is only accepted traced
         * from its start, which is the rule the second step puts into words.
         */
        fun guided(puzzle: Puzzle): Puzzle {
            val next = puzzle.unfound.firstOrNull()
            val outline = if (puzzle.found.size < Tutorial.GUIDED_WORDS && next != null) {
                mapOf(next to 1)
            } else {
                emptyMap()
            }
            return puzzle.copy(hints = outline)
        }

        /** The step a board is at, by how many words it has given up so far. */
        fun stepOf(puzzle: Puzzle): TutorialStep = when {
            puzzle.isComplete -> TutorialStep.DONE
            puzzle.found.isEmpty() -> TutorialStep.FIRST
            puzzle.found.size < Tutorial.GUIDED_WORDS -> TutorialStep.SECOND
            else -> TutorialStep.LAST
        }
    }
}
