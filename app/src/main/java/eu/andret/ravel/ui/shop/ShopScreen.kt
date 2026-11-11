package eu.andret.ravel.ui.shop

import androidx.activity.compose.LocalActivity
import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.andret.ravel.R
import eu.andret.ravel.ads.RewardedAds
import eu.andret.ravel.debug.ShopDebug
import eu.andret.ravel.game.Rewards
import eu.andret.ravel.ui.CoinAmount

/** How wide a tile is allowed to get, so a phone screen is not one edge-to-edge slab per offer. */
private val TILE_WIDTH = 280.dp

/** The glyph at the top of a daily tile, and the box the loading spinner takes its place in. */
private val GLYPH_SIZE = 56.dp

/** Smaller than the glyph's box: a ring that size would read heavier than the glyph it replaces. */
private val SPINNER_SIZE = 40.dp

/** How long the balance takes to count up to a new figure. */
private const val COUNT_MILLIS = 600

/** How far the balance and a changing glyph swell before springing back. */
private const val PULSE_SCALE = 1.25f

/** The swell itself; the settle is a spring and takes what it takes. */
private const val PULSE_UP_MILLIS = 120

/**
 * Where coins come from.
 *
 * Two kinds of offer, in two places. "Daily rewards" holds the two that come back every day - the
 * ad and the gift - under one heading, which is what tells the player they reset: the heading says
 * it once for both, so neither tile has to carry a "today" or a "come back tomorrow" of its own.
 * Below it, the coin packs that do not exist yet.
 *
 * Solving used to have a card here too, stating what a finished board pays; it is gone, because the
 * player reading this screen is looking for a way to get coins that is not the game they just came
 * out of. Nothing about [Rewards.FOR_COMPLETING] changed - it is simply not shop business.
 *
 * The hint prices went the same way, on a weaker version of the same argument. They are on the hint
 * buttons, with the coin beside them, and the usual way into this screen is one of those buttons
 * pressed without the coins for it - so a line repeating them here repeated what the player had
 * read a second earlier.
 *
 * This screen is one of the two places in the game an ad can start; the other is the streak repair
 * dialog on the daily menu. The privacy policy commits to every ad being one the player asked for,
 * in exchange for something, and both are exactly that - a button the player presses for a reward
 * named on it. The "stuck?" dialog on a board only points here; it starts nothing.
 */
@Composable
fun ShopScreen(modifier: Modifier = Modifier, viewModel: ShopViewModel = viewModel()) {
    val balance by viewModel.balance.collectAsStateWithLifecycle()
    val adState by viewModel.adState.collectAsStateWithLifecycle()
    val watchesLeft by viewModel.watchesLeft.collectAsStateWithLifecycle()
    val giftAvailable by viewModel.giftAvailable.collectAsStateWithLifecycle()

    // The day's allowance and gift are cached answers and this process can outlive a midnight, so
    // they are re-counted every time the tab is opened rather than only when the app started.
    LaunchedEffect(Unit) { viewModel.refresh() }

    // One arrangement on either side, so no AdaptiveScreen: the daily pair is a row everywhere and
    // the coin packs sit under it.
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Scrolls even where it fits. This is a page of offers and the list of them grows -
                // there is a coin pack behind the last card - so the tab that must never clip the
                // way to more coins is one that scrolls before it has to.
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.shop_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Balance(balance)

            SectionHeading(stringResource(R.string.shop_daily))
            // Side by side in both arrangements: the tiles are a glyph and a number each, narrow
            // enough for two on a phone, and the same height so the pair reads as one row of offers
            // even when one of them is carrying a status line. The gift first: it is the one every
            // visit can take, and the ad beside it is the offer it brings the player to.
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            ) {
                GiftTile(
                    available = giftAvailable,
                    onClaim = viewModel::claimGift,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxHeight(),
                )
                AdTile(
                    state = adState,
                    watchesLeft = watchesLeft,
                    viewModel = viewModel,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxHeight(),
                )
            }

            CoinPacksHeading()

            ShopDebug()
        }
    }
}

/**
 * The balance, counting up to a new figure and swelling once as it does.
 *
 * The shop is where coins arrive while the player is watching - a gift tapped, an ad finished - and
 * a number that simply changed would make them look for what moved. Counted up rather than jumped,
 * and a pulse on top, so the eye is taken to it. Only on the way up: spending happens on a board,
 * not here, and a balance opening the tab lower than it was is old news rather than an event.
 *
 * The figure read aloud is the final one; only the digits drawn are animated, so TalkBack never
 * announces the numbers in between.
 */
@Composable
private fun Balance(balance: Int) {
    val shown by animateIntAsState(
        targetValue = balance,
        animationSpec = tween(durationMillis = COUNT_MILLIS),
        label = "balance",
    )
    val pulse = remember { Animatable(1f) }
    var previous by remember { mutableIntStateOf(balance) }
    LaunchedEffect(balance) {
        val rose = balance > previous
        previous = balance
        if (rose) {
            pulse.animateTo(PULSE_SCALE, tween(durationMillis = PULSE_UP_MILLIS))
            pulse.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
    }
    CoinAmount(
        amount = balance,
        text = shown.toString(),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.graphicsLayer {
            scaleX = pulse.value
            scaleY = pulse.value
        },
    )
}

/** A section title on this page, announced as a heading so TalkBack can jump between them. */
@Composable
private fun SectionHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.semantics { heading() },
    )
}

/**
 * The coin packs that are not here yet: their heading, with a "coming soon" pill beside it.
 *
 * Kept on screen rather than left out, on the argument of the locked leaderboards tile: a player who
 * cannot see that coins will be buyable has no way of knowing the daily rewards are not the only
 * route, and a shop with one kind of thing in it reads as a shop that is finished. A heading and a
 * pill rather than the card that was here: an empty card under its own heading would be a box drawn
 * around the words "not yet".
 */
@Composable
private fun CoinPacksHeading() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionHeading(stringResource(R.string.shop_buy))
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Text(
                text = stringResource(R.string.shop_coming_soon),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

/**
 * The ad tile: what it pays, how many are left, and why it is dead when the reason is one the
 * player can act on. Drawn by [DailyTile]; this decides what goes into it.
 *
 * The two reasons that get a line are the ones a count cannot explain.
 * [RewardedAds.State.BLOCKED] is the player's own privacy choice, undone from the options tab, and
 * [RewardedAds.State.UNAVAILABLE] is worth a tap to retry. Neither is shown once the day is spent:
 * somebody with nothing left does not care whether an ad could have been loaded, and 0/3 already
 * says why the tile is dead.
 */
@Composable
private fun AdTile(
    state: RewardedAds.State,
    watchesLeft: Int,
    viewModel: ShopViewModel,
    modifier: Modifier = Modifier,
) {
    // Null while the composable is being previewed or torn down; the tile goes dead rather than
    // taking a press - a full-screen ad has nowhere to go without an activity.
    val activity = LocalActivity.current

    /*
     * What a press does, or null when the tile is dead.
     *
     * One value rather than a boolean for "enabled" and a lambda that decides what it meant: those
     * two can disagree, and the way they disagree is a tile that takes a press and does nothing.
     * Null is both answers at once.
     */
    val press: (() -> Unit)? = when {
        watchesLeft <= 0 || activity == null -> null
        state == RewardedAds.State.READY -> ({ viewModel.watch(activity) })
        state == RewardedAds.State.UNAVAILABLE -> ({ viewModel.retry() })
        else -> null
    }

    val status = when {
        watchesLeft <= 0 -> null
        state == RewardedAds.State.BLOCKED -> stringResource(
            R.string.shop_ad_blocked,
            stringResource(R.string.tab_settings),
        )

        state == RewardedAds.State.UNAVAILABLE -> stringResource(R.string.shop_ad_unavailable)
        else -> null
    }

    DailyTile(
        glyph = R.drawable.ic_ad,
        glyphDescription = stringResource(R.string.shop_earn_ads),
        // Not while the day is spent: the next ad is fetched the moment the last one closes, and a
        // spinner on a tile that cannot be pressed would promise something that is not coming.
        loading = watchesLeft > 0 &&
            (state == RewardedAds.State.LOADING || state == RewardedAds.State.SHOWING),
        reward = Rewards.FOR_WATCHING_AD,
        left = watchesLeft,
        perDay = Rewards.ADS_PER_DAY,
        leftDescription = stringResource(R.string.shop_ad_left_description, watchesLeft, Rewards.ADS_PER_DAY),
        status = status,
        press = press,
        modifier = modifier,
    )
}

/**
 * Today's gift: [Rewards.DAILY_GIFT], one tap, once a day. Nothing can go wrong with it that the
 * player could act on, so it never has a status line - it is there, or it is 0/1 until tomorrow.
 *
 * Taken, the box is drawn open, and it stays open for the rest of the day: the open lid is the
 * thing that says "already had it" before the count in the corner is read.
 */
@Composable
private fun GiftTile(available: Boolean, onClaim: () -> Unit, modifier: Modifier = Modifier) {
    val left = if (available) 1 else 0
    DailyTile(
        glyph = if (available) R.drawable.ic_gift else R.drawable.ic_gift_open,
        glyphDescription = stringResource(if (available) R.string.shop_gift_claim else R.string.shop_gift_claimed),
        loading = false,
        reward = Rewards.DAILY_GIFT,
        left = left,
        perDay = 1,
        leftDescription = stringResource(R.string.shop_gift_left_description, left, 1),
        status = null,
        press = if (available) onClaim else null,
        modifier = modifier,
    )
}

/**
 * One daily offer: a glyph, what it pays, and how many are left in the corner.
 *
 * The whole tile is the button. There is one thing to do with this card, so a button inside it
 * would be a second, smaller target for the same action - and a target the player has to aim at,
 * on a card their thumb is already over. Being disabled is drawn by the card itself rather than by
 * hiding anything: the tile stays exactly where it was, in the colors of something switched off,
 * which is what a player needs to see to understand that the offer exists and is not available
 * *right now*.
 *
 * Pared down to the glyph and the reward on purpose, the way most games draw the same offers: no
 * title and no day in the count, since the section heading says "daily" for every tile under it.
 * The glyph and the spinner carry in words what they show, for TalkBack, so nothing is lost to a
 * screen reader.
 *
 * The count sits in the corner, as a badge, in full - "2/3" and not "2" - because the denominator
 * is the rule. It is the number left, so it reads 3/3 first thing and 0/3 when the day is spent.
 *
 * @param press what a tap does, or null when the tile is dead.
 * @param status a line under the reward for a reason the count cannot give, or null for none -
 *   a real state rather than an empty string, so a working tile reserves no gap for it.
 */
@Composable
private fun DailyTile(
    @DrawableRes glyph: Int,
    glyphDescription: String,
    loading: Boolean,
    reward: Int,
    left: Int,
    perDay: Int,
    leftDescription: String,
    status: String?,
    press: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = { press?.invoke() },
        enabled = press != null,
        modifier = modifier.widthIn(max = TILE_WIDTH),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // What the tile is, before what it pays. While something is being fetched the glyph
                // gives way to a spinner of the same size, so the tile does not jump when it arrives.
                //
                // In the primary color while a press does something, and in the card's own
                // content color when it does not, so a dead tile still looks switched off all the
                // way up - a bright glyph on a grayed card would read as the one part still working.
                //
                // A glyph that changes while the tile is on screen - the gift opening - pops: it
                // swells and settles as the new picture fades in, so the change reads as the box
                // being opened rather than as one icon swapped for another. A glyph already in its
                // state when the tile appears does not move.
                val pop = remember { Animatable(1f) }
                var drawn by remember { mutableIntStateOf(glyph) }
                LaunchedEffect(glyph) {
                    if (glyph != drawn) {
                        drawn = glyph
                        pop.animateTo(PULSE_SCALE, tween(durationMillis = PULSE_UP_MILLIS))
                        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                    }
                }
                Box(
                    modifier = Modifier
                        .size(GLYPH_SIZE)
                        .graphicsLayer {
                            scaleX = pop.value
                            scaleY = pop.value
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (loading) {
                        val finding = stringResource(R.string.shop_ad_loading)
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(SPINNER_SIZE)
                                .semantics { contentDescription = finding },
                        )
                    } else {
                        val tint = if (press != null) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        Crossfade(targetState = glyph, label = "glyph") { shown ->
                            Icon(
                                painter = painterResource(shown),
                                contentDescription = glyphDescription,
                                tint = tint,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
                CoinAmount(
                    amount = reward,
                    // The plus is part of the offer and not part of the number: this is the one
                    // place in the game a coin figure is something arriving rather than held.
                    text = stringResource(R.string.shop_reward, reward),
                    style = MaterialTheme.typography.titleLarge,
                )
                status?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            // Read aloud as a sentence: "2/3" on its own is a fraction, not a count of what is left.
            Text(
                text = stringResource(R.string.shop_left, left, perDay),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 12.dp)
                    .clearAndSetSemantics { contentDescription = leftDescription },
            )
        }
    }
}
