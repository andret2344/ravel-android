package eu.andret.ravel.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * The "something here is waiting for you" dot: on the daily tab while today's board is unsolved,
 * on the shop tab and on the gift itself while today's gift is untaken.
 *
 * **One dot for the whole game.** The tab and the gift tile draw the same one, so the dot that
 * brought the player to the shop is recognizably the one they then find on the gift.
 *
 * Not Material's `Badge`, which was the first version and read as timid:
 * - **6 dp** is a small badge's size, a fleck at the corner of a 24 dp icon. This is 10 dp, about
 *   two fifths of the icon, which is a mark the eye goes to rather than one it finds.
 * - **Its color is `colorScheme.error`, and this palette does not set one**, so it was Material's
 *   baseline: a dark brick red in the light theme that sat too close to the brown primary, and a
 *   pale pink (`#F2B8B5`) in the dark theme that looked like decoration. The red here is one
 *   saturated color for both themes on purpose - the dot means the same thing in both.
 *
 * **The ring** is the background the dot sits on, drawn around it, so that a dot this size cutting
 * into an icon's outline reads as a dot in front of the icon rather than as a blot merged into it.
 * Hence [ring] is a parameter: it has to be whatever is behind, which is the bar, the rail or a card.
 *
 * **It pops once when it appears** - at launch, at the midnight that brings it back, or when the
 * screen it sits on opens - and then stays still. Never a continuous pulse: something blinking at
 * the edge of the eye all day while the player is solving a board is the pressure the daily
 * notification's rules already turn down, "no guilt", in another form.
 *
 * No semantics of its own; the caller says what it means where that is not said already.
 */
@Composable
fun AttentionDot(ring: Color, modifier: Modifier = Modifier) {
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        pop.animateTo(POP_PEAK, tween(durationMillis = POP_UP_MILLIS))
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
    }
    Box(
        modifier = modifier
            .size(DOT_SIZE + RING_WIDTH * 2)
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            }
            .background(ring, CircleShape)
            .padding(RING_WIDTH)
            .background(ATTENTION_RED, CircleShape),
    )
}

/** The red part alone; the ring adds to it rather than eating into it. */
private val DOT_SIZE = 10.dp

private val RING_WIDTH = 2.dp

/** Material's red 600: saturated enough to read on both the cream and the near-black surfaces. */
private val ATTENTION_RED = Color(0xFFE53935)

/** How far past full size the dot swells on arriving, before it settles. */
private const val POP_PEAK = 1.35f

private const val POP_UP_MILLIS = 160
