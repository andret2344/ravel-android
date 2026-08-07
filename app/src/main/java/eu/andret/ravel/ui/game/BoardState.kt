package eu.andret.ravel.ui.game

import eu.andret.ravel.game.Puzzle

/**
 * What a board screen can be showing at any moment, whichever mode it belongs to.
 *
 * Shared rather than one enum per mode, because the three states are facts about the generator and
 * not about the mode: a search is running, it found a board, or it did not. A daily challenge and a
 * level fail in exactly the same way and are waited for in exactly the same way.
 */
sealed interface BoardState {
    /** The board is being searched for, which can take a moment on a cold start. */
    object Generating : BoardState

    data class Playing(val puzzle: Puzzle) : BoardState

    /** [reason] is null when the generator simply ran out of time. */
    data class Failed(val reason: String?) : BoardState
}
