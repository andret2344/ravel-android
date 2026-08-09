package eu.andret.ravel.ui.game

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import eu.andret.ravel.ui.CoinAmount

/**
 * The player's coins, in the top right corner, and the way to buy more of them.
 *
 * A button and not a label, everywhere the balance appears. The number is the one thing on screen
 * the player watches go down, and "how do I get more" is the question it raises - so the answer is
 * under the question rather than two taps away on a tab they have to think of first.
 *
 * Quiet on purpose: text button, the surface's own muted colour, no container. It is worth a tap but
 * it is never the move to make, and a filled button in that corner would compete with the board.
 *
 * The padding is tighter than a text button's default so the figure stays in the corner it has
 * always been in rather than sliding inwards to make room for its own touch target.
 *
 * It takes the balance and not a string, which it used to. Every screen that showed one wrote it its
 * own way - a glyph and a number over a board, the counted noun on a menu - and the caller choosing
 * between them is the caller inventing a currency notation. There is one, in [CoinAmount], and this
 * button asks for it like everything else does.
 */
@Composable
internal fun BalanceButton(balance: Int, onShop: () -> Unit) {
    TextButton(
        onClick = onShop,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        CoinAmount(amount = balance, style = MaterialTheme.typography.titleMedium)
    }
}
