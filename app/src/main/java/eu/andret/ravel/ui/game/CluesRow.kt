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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import eu.andret.ravel.game.Clue
import eu.andret.ravel.ui.theme.foundColors

/**
 * The word lengths, which are the entire clue set: how many words are hidden and how long each one
 * is. Anything more would be the answer.
 */
@Composable
internal fun CluesRow(clues: List<Clue>) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
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
 * It is struck through as well, which is not decoration: colour alone is the one cue a colour-blind
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
