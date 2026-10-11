package eu.andret.ravel.ui.game

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.andret.ravel.R
import eu.andret.ravel.game.Hint
import eu.andret.ravel.game.Puzzle
import eu.andret.ravel.ui.CoinAmount
import eu.andret.ravel.ui.board.BoardGrid

/**
 * Everything a board screen is made of except the board and the mode's own headline.
 *
 * Shared because the two modes are the same screen with a different thing to say at the top. A
 * player who has learned where the close button, the clock and the hint prices are on the daily
 * challenge should not have to learn it again on level one - and two copies of this drift apart on
 * the first change to either.
 */

/**
 * The way out on the left, the clock in the middle, the balance on the right.
 *
 * These are the three things that belong over a board: how to leave it, how long it has taken, and
 * what there is to spend on it. The mode's own title is not up here - it goes under the bar - which
 * keeps the least urgent thing on the screen out of its most prominent corner.
 *
 * The close button is a filled circle rather than a word or a bare glyph. A board fills the screen
 * and is the only thing on it worth looking at, so anything that has to be findable over it has to
 * be findable as a shape - a lone "✕" on the same background as the grid is a character among a
 * grid full of characters.
 *
 * The balance is the figure alone. "1000 coins" is a sentence to parse; over a puzzle, next to two
 * priced buttons, the number is unambiguous and the noun is furniture. The clock is the same idea.
 *
 * @param elapsed seconds on the board, or null to leave the clock out entirely - a board being
 *   reread is not being timed, and a frozen counter over it would read as one that had stopped
 *   working.
 * @param onShop where the balance leads. A number the player is watching go down while they look at
 *   two prices they cannot afford is a question, and the shop is the answer - so it is a button
 *   rather than a label, in the one place they are already looking when they ask.
 */
@Composable
internal fun BoardTopBar(balance: Int, elapsed: Int?, onClose: () -> Unit, onShop: () -> Unit) {
    val close = stringResource(R.string.board_close)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Never primary. Primary in this game means "the move to make now", and the button that
        // leaves the board is not a move to make.
        FilledTonalIconButton(
            onClick = onClose,
            modifier = Modifier
                .size(44.dp)
                .semantics { contentDescription = close },
        ) {
            Text(
                text = "✕",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        if (elapsed != null) {
            Text(
                text = asClock(elapsed),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BalanceButton(balance = balance, onShop = onShop)
    }
}

/** The grid itself, with the one thing a screen reader needs to know about it. */
@Composable
internal fun PlayableBoard(
    puzzle: Puzzle,
    onPathTraced: (List<Int>) -> Unit,
    onTap: (Int) -> Unit,
    hapticsEnabled: Boolean,
) {
    val description = stringResource(
        R.string.board_description,
        puzzle.board.width,
        puzzle.board.height,
    )
    BoardGrid(
        puzzle = puzzle,
        onPathTraced = onPathTraced,
        onTap = onTap,
        hapticsEnabled = hapticsEnabled,
        modifier = Modifier.semantics { contentDescription = description },
    )
}

/**
 * The spinner, and - once [slow] - why the player need not stare at it.
 *
 * The way out is a concrete place rather than "you can leave": the build only runs while the app is
 * in front, so the honest offer is something else *in* the game, and the daily is the one thing
 * there is always something to do on. Null [onElsewhere] shows the text without the button.
 */
@Composable
internal fun GeneratingBoard(slow: Boolean = false, onElsewhere: (() -> Unit)? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator()
        Text(
            text = stringResource(if (slow) R.string.board_slow else R.string.board_generating),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            // The text changes under a spinner nobody is touching, so a screen reader would never
            // hear the "you need not wait" half without being told it changed.
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (slow && onElsewhere != null) {
            OutlinedButton(onClick = onElsewhere) { Text(stringResource(R.string.board_slow_daily)) }
        }
    }
}

@Composable
internal fun FailedBoard(reason: String?, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = reason ?: stringResource(R.string.board_failed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRetry) { Text(stringResource(R.string.board_retry)) }
    }
}

/**
 * The two ways to stop looking for a word, and what each of them costs.
 *
 * Flowing rather than a plain row, so the second button drops onto its own line instead of squeezing
 * the first. Side by side these need about 260dp, which every phone has across and none has to spare
 * beside a board - and a button too narrow for its own price does not shrink, it wraps: "Word 100"
 * came out as "Word" over "10" over "0", which is a price the player cannot read.
 */
@Composable
internal fun HintRow(complete: Boolean, onHint: (Hint) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Nothing left to reveal once every word is found; the buttons stay and go quiet rather
        // than disappearing, so the row under the board keeps its height.
        HintButton(R.string.hint_letter, Hint.LETTER, complete, onHint)
        HintButton(R.string.hint_word, Hint.WORD, complete, onHint)
    }
}

/**
 * A hint and what it costs, on one button.
 *
 * The price is on the button rather than behind a confirmation because it is the only thing that
 * distinguishes the two: both say "show me", and the choice being made is entirely about how much
 * of the puzzle the player is willing to hand over and at what price.
 *
 * A button the player cannot afford is still a button. Disabling it would answer "why not?" with
 * silence, where a tap can answer it with the shop.
 */
@Composable
private fun HintButton(
    @StringRes label: Int,
    hint: Hint,
    complete: Boolean,
    onHint: (Hint) -> Unit,
) {
    OutlinedButton(
        onClick = { onHint(hint) },
        enabled = !complete,
        // Tighter than Material's own 24dp, which is sized for a button carrying one word. These
        // carry two things - what is bought and what it costs - and the pair has to fit on one line
        // beside a board: at the default they came to about 270dp against the 222dp the column has
        // on a phone on its side, so the second one wrapped and sat half off the bottom of the
        // screen. The row [HintRow] flows, so a narrower window still gets two readable buttons
        // rather than two clipped ones - but a control below the fold is a control the player has to
        // find, and these are the only two on the screen. The vertical padding is Material's, so the
        // buttons stay as tall as every other one in the game.
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = stringResource(label), style = MaterialTheme.typography.labelLarge)
            // The price wears the same coin as the balance the player is spending from, three
            // centimetres above it. It used to be a number after a middle dot, which asked them to
            // work out for themselves that the two figures were in the same units.
            CoinAmount(amount = hint.cost, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * The running tally of what this board has cost - not this visit to it.
 *
 * Counted against the puzzle and not the screen, because the screen is thrown away every time the
 * player backs out of it. Counting the visit would let a player buy a word, leave, come back to a
 * counter at zero and finish on the record as though they had solved it unaided.
 *
 * Shown from the first frame, at zero, rather than appearing on the first purchase. It is directly
 * under a board that must never scroll, so a line that came and went would move the grid above it
 * mid-solve - and a player who has bought nothing is exactly the player the line is reassuring.
 */
@Composable
internal fun BoughtLine(letters: Int, words: Int) {
    Text(
        text = stringResource(
            R.string.hints_used,
            pluralStringResource(R.plurals.hints_letters, letters, letters),
            pluralStringResource(R.plurals.hints_words, words, words),
        ),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
