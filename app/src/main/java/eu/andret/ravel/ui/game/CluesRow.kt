package eu.andret.ravel.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import eu.andret.ravel.game.Clue
import eu.andret.ravel.ui.theme.foundColors

/**
 * The word lengths, which are the entire clue set: how many words are hidden and how long each one
 * is. Anything more would be the answer.
 *
 * @param hidden hard mode: only the lengths already found are shown, each appearing in its place in
 *   the ascending order as its word is found, so the row says neither how many words are left nor
 *   how long they are. The full row is still laid out underneath, invisible and silent to screen
 *   readers, so the footer is the height it would be in an ordinary level - the board is sized from
 *   what the footer leaves, and a row growing as words were found would shrink the grid under the
 *   player at every word. What that keeps visible is the number of lines the row wraps to, which
 *   only ever tells a player with a very long list that the list is long.
 */
@Composable
internal fun CluesRow(clues: List<Clue>, hidden: Boolean = false) {
    if (!hidden) {
        ClueChips(clues)
        return
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        ClueChips(clues, modifier = Modifier.alpha(0f).clearAndSetSemantics {})
        ClueChips(clues.filter(Clue::found))
    }
}

@Composable
private fun ClueChips(clues: List<Clue>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        clues.forEach { clue -> ClueChip(clue) }
    }
}

/**
 * A found length turns the same green the word wears on the board, so the row and the grid agree on
 * what "done" looks like.
 *
 * It is struck through as well, which is not decoration: color alone is the one cue a color-blind
 * player cannot use, and this row is how anyone tracks what is left.
 *
 * Nothing is removed from the row either way - it keeps the shape the player has been counting
 * against for the whole board.
 */
@Composable
private fun ClueChip(clue: Clue) {
    Box(
        modifier = Modifier
            .background(
                color = if (clue.found) {
                    foundColors.container
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = clue.length.toString(),
            style = MaterialTheme.typography.titleSmall,
            color = if (clue.found) {
                foundColors.onContainer
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            },
            textDecoration = if (clue.found) TextDecoration.LineThrough else null,
        )
    }
}
