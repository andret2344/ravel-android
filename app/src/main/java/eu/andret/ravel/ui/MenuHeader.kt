package eu.andret.ravel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.andret.ravel.ui.game.BalanceButton

/**
 * The heading of a game tab's menu: the title on the left, the balance on the right.
 *
 * **One composable for both menus, the ladder's and the daily's.** They were two private copies of
 * the same row, which drifted: the daily's title has the date under it, both rows centered their
 * contents vertically, and a two-line block and a one-line title center differently against a
 * 48 dp button. Measured on a 420 dpi emulator, switching tabs moved the title by 8 dp and the coins
 * by 2 dp - small enough to read as a pixel and large enough to see.
 *
 * **Aligned by baseline, not centered.** The coin figure sits on the title's line of type, which is
 * what the two say together ("Game ... 775"), and it stays there whatever is under the title and
 * whatever the font scale: a fixed top padding computed from 48 dp minus the title's height was the
 * other way to get the same picture, and it comes apart the moment the player enlarges the text.
 *
 * @param subtitle a line under the title, such as the daily's date, or null for none. It never moves
 *   the title or the balance, only adds height below them.
 */
@Composable
fun MenuHeader(
    title: String,
    balance: Int,
    onShop: () -> Unit,
    subtitle: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // A column's first baseline is its first line's, so the subtitle has no say in where the
        // row lines up.
        Column(modifier = Modifier.alignByBaseline()) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        BalanceButton(balance = balance, onShop = onShop, modifier = Modifier.alignByBaseline())
    }
}
