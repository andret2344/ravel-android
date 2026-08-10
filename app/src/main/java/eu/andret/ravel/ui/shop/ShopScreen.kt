package eu.andret.ravel.ui.shop

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.andret.ravel.R
import eu.andret.ravel.ads.RewardedAds
import eu.andret.ravel.game.Hint
import eu.andret.ravel.game.Rewards
import eu.andret.ravel.ui.AdaptiveScreen
import eu.andret.ravel.ui.CoinAmount

/** How wide a tile is allowed to get, so a phone screen is not one edge-to-edge slab per offer. */
private val TILE_WIDTH = 280.dp

/**
 * Where coins come from, and what they go on.
 *
 * One thing to do and one thing that does not exist yet. Solving used to have a card here too,
 * stating what a finished board pays; it is gone, because the player reading this screen is looking
 * for a way to get coins that is not the game they just came out of, and a card telling them to go
 * back and play is a card that answers a question nobody asked. Nothing about
 * [Rewards.FOR_COMPLETING] changed - it is simply not shop business.
 *
 * This screen is the only place in the game an ad can start. That is a commitment made in the
 * privacy policy - every ad is one the player asked for, in exchange for something - and it is kept
 * structurally rather than by discipline: nothing else in the app can reach [RewardedAds.show].
 */
@Composable
fun ShopScreen(modifier: Modifier = Modifier, viewModel: ShopViewModel = viewModel()) {
    val balance by viewModel.balance.collectAsStateWithLifecycle()
    val adState by viewModel.adState.collectAsStateWithLifecycle()
    val watchesLeft by viewModel.watchesLeft.collectAsStateWithLifecycle()

    // The day's allowance is a cached number and this process can outlive a midnight, so it is
    // re-counted every time the tab is opened rather than only when the app started.
    LaunchedEffect(Unit) { viewModel.refresh() }

    AdaptiveScreen(modifier = modifier) { wide ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Scrolls even where it fits. This is a page of offers and the list of them grows -
                // there is a coin pack behind that second card - so the tab that must never clip the
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
            CoinAmount(amount = balance, style = MaterialTheme.typography.titleLarge)

            if (wide) {
                // The offers side by side, top-aligned, each still capped at a tile's width. The
                // heading and the balance stay across the top because they are the shop and not one
                // of its offers - and this is a screen where the two cards are read against each
                // other, which is what putting them on one line is for. Top rather than stretched:
                // the ad card is much the taller of the two and a "coming soon" placeholder blown up
                // to match it would promise something bigger than what is arriving.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.Top,
                ) {
                    AdTile(
                        state = adState,
                        watchesLeft = watchesLeft,
                        viewModel = viewModel,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    BuyCard(modifier = Modifier.weight(1f, fill = false))
                }
            } else {
                AdTile(state = adState, watchesLeft = watchesLeft, viewModel = viewModel)
                BuyCard()
            }

            Text(
                text = stringResource(R.string.shop_prices, Hint.LETTER.cost, Hint.WORD.cost),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * The offer that is not here yet, kept on screen as a card rather than left out.
 *
 * Same argument as the locked leaderboards tile: a player who cannot see that coins will be buyable
 * has no way of knowing the ad is not the only route, and a shop with one thing in it reads as a shop
 * that is finished.
 */
@Composable
private fun BuyCard(modifier: Modifier = Modifier) {
    Card(modifier = modifier.widthIn(max = TILE_WIDTH)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.shop_buy),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.shop_coming_soon),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The ad tile: what it pays, how many are left of today's, and why it is dead when it is.
 *
 * The whole tile is the button. There is one thing to do with this card, so a button inside it
 * would be a second, smaller target for the same action - and a target the player has to aim at,
 * on a card their thumb is already over. Being disabled is drawn by the card itself rather than by
 * hiding anything: the tile stays exactly where it was, in the colours of something switched off,
 * which is what a player needs to see to understand that the offer exists and is not available
 * *right now*.
 *
 * The count is always shown, in full - "2/3 today" and not "2 left" - because the denominator is
 * the rule. A player who sees 2/3 knows what tomorrow looks like; a player who sees "2 left" finds
 * out the game has a daily cap by hitting it.
 *
 * The reasons for being dead are kept apart on purpose. A used-up day is the game saying no and
 * comes back tomorrow; [RewardedAds.State.BLOCKED] is the player's own privacy choice and is undone
 * from the options tab. Collapsing them into one "not available" would leave a player who refused
 * consent waiting for a tomorrow that changes nothing. The day is checked first, because somebody
 * with nothing left today does not care whether an ad happens to be loaded.
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

    /*
     * The line under the count, or null when there is nothing to add.
     *
     * Null is a real state and not an empty string: a tile that is simply working has nothing to
     * explain, and reserving a line for the explanation would leave a gap that only ever fills up
     * when something is wrong.
     */
    val status = when {
        watchesLeft <= 0 -> stringResource(R.string.shop_ad_none_left)
        state == RewardedAds.State.BLOCKED -> stringResource(
            R.string.shop_ad_blocked,
            stringResource(R.string.tab_settings),
        )

        state == RewardedAds.State.UNAVAILABLE -> stringResource(R.string.shop_ad_unavailable)
        state == RewardedAds.State.LOADING || state == RewardedAds.State.SHOWING ->
            stringResource(R.string.shop_ad_loading)

        else -> null
    }

    Card(
        onClick = { press?.invoke() },
        enabled = press != null,
        modifier = modifier.widthIn(max = TILE_WIDTH),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CoinAmount(
                amount = Rewards.FOR_WATCHING_AD,
                // The plus is part of the offer and not part of the number: this is the one place
                // in the game a coin figure is something arriving rather than something held.
                text = stringResource(R.string.shop_ad_reward, Rewards.FOR_WATCHING_AD),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = stringResource(R.string.shop_earn_ads),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(
                    R.string.shop_ad_left_today,
                    watchesLeft,
                    Rewards.ADS_PER_DAY,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    }
}
