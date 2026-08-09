package eu.andret.ravel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import eu.andret.ravel.R

/**
 * An amount of coins: the figure, then the coin.
 *
 * **Every visible coin figure in the game goes through here.** That is the point of the composable
 * existing at all - before it, the balance over a board was a number after a `◈` glyph, the balance
 * on a menu was a number followed by the word "coins", and the price on a hint button was a number
 * after a middle dot. Three renderings of one idea, none of which taught a player what the other two
 * meant. A player who learns the coin on the shop tile should recognise it on the hint button they
 * are about to press, and that only holds while there is one thing to learn.
 *
 * The noun is gone with the glyph. "100 coins" beside a coin is the label read twice, and the
 * declension of it in Polish - moneta, monety, monet - was four plural forms of a word the icon says
 * without any. Screen readers still get the counted noun: the whole row carries it as its
 * description, replacing what the digits alone would have been read as.
 *
 * The icon is sized from [style] rather than given its own dimension, so it tracks the text at every
 * call site and at every system font scale. A fixed size would be right on the tile it was tuned
 * against and wrong on the corner of a board.
 *
 * @param text how the figure is written, for the places that are not simply stating a balance -
 *   the shop's ad tile pays "+100" and the plus sign is part of the offer.
 */
@Composable
internal fun CoinAmount(
    amount: Int,
    modifier: Modifier = Modifier,
    text: String = amount.toString(),
    style: TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = Color.Unspecified,
) {
    val description = pluralStringResource(R.plurals.coins_balance, amount, amount)
    val size = with(LocalDensity.current) { style.fontSize.toDp() }

    Row(
        // Cleared rather than merged: the digits and the icon's own description would otherwise be
        // announced one after the other, as "100, 100 coins".
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(size * COIN_GAP),
    ) {
        Text(text = text, style = style, color = color)
        Icon(
            painter = painterResource(R.drawable.ic_coin),
            contentDescription = null,
            // Follows the text when the caller has not said otherwise, which is what puts the coin
            // in the muted colour of a top bar and the bright one of a hint button without either
            // call site having to know.
            tint = color.takeOrElse { LocalContentColor.current },
            modifier = Modifier.size(size),
        )
    }
}

/** The gap between figure and coin, as a fraction of the type size, so it scales with it too. */
private const val COIN_GAP = 0.25f
