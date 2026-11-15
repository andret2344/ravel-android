package eu.andret.ravel.ui.game

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import eu.andret.ravel.R
import eu.andret.ravel.ui.CoinAmount
import eu.andret.ravel.ui.board.rememberBoardPalette
import eu.andret.ravel.ui.theme.goldColors
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * What a finished board paid, large and gold, the way the completion dialogs open.
 *
 * With [celebrate] - the board was just finished - the figure counts up from zero and a ring of dots
 * in the board's and the coin's colors bursts out from behind it, once. Owner's choice, 2026-11-15:
 * the dialog read as a settings screen, not a reward. Once, and short: the dialog is still something
 * to read and leave, and an effect that looped would be the thing a player stares past. No sound -
 * the game has none - and the system's animation scale applies, so a phone with animations off
 * shows the figure as it is.
 *
 * Without [celebrate] - "Show summary" brought the dialog back - the figure is simply there.
 */
@Composable
internal fun CelebratedTotal(total: Int, celebrate: Boolean) {
    val count = remember { Animatable(if (celebrate) 0f else total.toFloat()) }
    val burst = remember { Animatable(if (celebrate) 0f else 1f) }
    LaunchedEffect(total) {
        if (celebrate) {
            count.animateTo(total.toFloat(), tween(COUNT_MILLIS, easing = FastOutSlowInEasing))
        }
    }
    LaunchedEffect(Unit) {
        if (celebrate) burst.animateTo(1f, tween(BURST_MILLIS, easing = LinearOutSlowInEasing))
    }
    val colors = listOf(goldColors.fill, rememberBoardPalette().solved, MaterialTheme.colorScheme.primary)
    Box(
        modifier = Modifier.drawBehind { drawBurst(burst.value, colors) },
        contentAlignment = Alignment.Center,
    ) {
        val shown = count.value.roundToInt()
        CoinAmount(
            // The final figure for screen readers, which would otherwise hear every step of the count.
            amount = total,
            text = stringResource(R.string.done_gained, shown),
            style = MaterialTheme.typography.displaySmall,
            color = goldColors.fill,
        )
    }
}

/**
 * The ring of dots behind the total at [progress] (0 to 1): they fly out from close behind the coin
 * figure, shrink and fade. Placed by angle, evenly, so the burst is the same shape every time - a
 * random one would be different on every board for no reason anybody could see.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBurst(progress: Float, colors: List<Color>) {
    if (progress <= 0f || progress >= 1f) return
    val from = size.minDimension * 0.3f
    val to = size.maxDimension * 0.5f + BURST_REACH.toPx()
    val distance = from + (to - from) * progress
    val radius = BURST_DOT.toPx() * (1f - progress * 0.6f)
    for (index in 0 until BURST_DOTS) {
        val angle = (index * 2 * Math.PI / BURST_DOTS).toFloat()
        // Alternate dots run a little short, so the ring reads as a spray rather than a circle.
        val reach = if (index % 2 == 0) distance else distance * 0.8f
        drawCircle(
            color = colors[index % colors.size].copy(alpha = 1f - progress),
            radius = radius,
            center = center + Offset(cos(angle) * reach, sin(angle) * reach),
        )
    }
}

/**
 * One part of what a board paid, for the row of chips under the total: an icon or a word, and its
 * coins. The total says how much; these say what for - on a level, the board and each modifier that
 * paid, which is the one place a modifier's bonus is seen being earned.
 *
 * @param icon a modifier's icon, read out as [label]; null shows [label] as text instead.
 */
internal class EarnedPart(val label: String, val amount: Int, @param:DrawableRes val icon: Int? = null)

/**
 * What the total was for, as soft chips centered under it: [base], what the board itself paid, alone
 * on the first line, and [extras] - a level's modifiers, the daily's streak day - in a row under it
 * (owner's layout). The base is what every finish pays; the extras are what this one added, and the
 * two lines keep them from reading as one list of equal things.
 *
 * @param base null when the board paid nothing of its own.
 */
@Composable
internal fun EarnedParts(base: EarnedPart?, extras: List<EarnedPart>) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (base != null) EarnedChip(base)
        if (extras.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                extras.forEach { part -> EarnedChip(part) }
            }
        }
    }
}

@Composable
private fun EarnedChip(part: EarnedPart) {
    val colors = MaterialTheme.colorScheme
    val spoken = stringResource(R.string.earned_part, part.label, part.amount)
    Row(
        modifier = Modifier
            .clearAndSetSemantics { contentDescription = spoken }
            .background(colors.secondaryContainer, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (part.icon != null) {
            Icon(
                painter = painterResource(part.icon),
                contentDescription = null,
                tint = colors.onSecondaryContainer,
                modifier = Modifier.size(16.dp),
            )
        } else {
            Text(text = part.label, style = MaterialTheme.typography.labelLarge, color = colors.onSecondaryContainer)
        }
        CoinAmount(
            amount = part.amount,
            text = stringResource(R.string.done_gained, part.amount),
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSecondaryContainer,
        )
    }
}

private const val COUNT_MILLIS = 650
private const val BURST_MILLIS = 750
private const val BURST_DOTS = 14
private val BURST_DOT = 3.5.dp
private val BURST_REACH = 28.dp
