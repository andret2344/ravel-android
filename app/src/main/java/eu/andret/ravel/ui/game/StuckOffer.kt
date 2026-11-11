package eu.andret.ravel.ui.game

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.andret.ravel.R
import eu.andret.ravel.RavelApplication
import eu.andret.ravel.ads.RewardedAds
import eu.andret.ravel.game.Puzzle
import eu.andret.ravel.game.Rewards
import eu.andret.ravel.ui.CoinAmount
import kotlinx.coroutines.flow.StateFlow

/**
 * How long a player has to go without finding a word before the board points them at the shop.
 *
 * Counted in seconds of the board on screen - the solve clock's seconds, so time with the app in the
 * background or the board still being built never counts - and from the last word found, or from
 * the board coming up in this sitting.
 *
 * 23 was the first figure, and it was a guess. A player on a 6x6 or larger spends a minute or two
 * looking before the next word shows itself, which is the game working as designed; a dialog after
 * 23 seconds would have arrived on nearly every board, interrupting exactly the thinking the board is
 * asking for. 99 is past the ordinary pause and well short of a player who has given up.
 */
internal const val STUCK_AFTER_SECONDS = 99

/** The board screens' view of what the "stuck?" dialog depends on. All of it lives on the application. */
class StuckOfferViewModel(application: Application) : AndroidViewModel(application) {
    private val ravel = application as RavelApplication

    /** Whether today's one showing is still unused. */
    val available: StateFlow<Boolean> = ravel.stuckOffer.available

    /** Watches left today: no point sending a player to an ad tile that is spent. */
    val watchesLeft: StateFlow<Int> = ravel.adAllowance.remaining

    /** Whether ads may be shown at all - a blocked consent means the shop has nothing to offer. */
    val adState: StateFlow<RewardedAds.State> = ravel.ads.state

    /** Re-counts the day, since the process can outlive a midnight with a board open. */
    fun refresh() {
        ravel.stuckOffer.refresh()
        ravel.adAllowance.refresh()
    }

    fun shown() = ravel.stuckOffer.shown()
}

/**
 * Points a stuck player at the ad in the shop: once a day at most, after [STUCK_AFTER_SECONDS] on
 * the board without a word.
 *
 * ## Why a dialog that leads to the shop, and not an ad
 *
 * The ad itself still starts only where the player asks for it - the shop's tile, or the streak
 * repair - so this is a pointer and nothing more: two buttons, one of them "not now". It is the one
 * thing in the game that interrupts a player unasked, which is why it is capped at once a day rather
 * than once a board, and why it is not shown when it would lead nowhere: with the day's watches
 * spent, or with ads refused at the consent form, the shop has nothing for it to point at.
 *
 * Not on a board opened for review or on the tutorial: neither can be stuck on. A board that is
 * finished is not either - the check runs against the puzzle every time.
 *
 * @param puzzle the board, or null while it is still being built.
 * @param elapsed the board's solve clock, which only runs while the board is on screen and playable.
 */
@Composable
internal fun StuckOffer(
    puzzle: Puzzle?,
    elapsed: Int,
    onShop: () -> Unit,
    viewModel: StuckOfferViewModel = viewModel(),
) {
    val available by viewModel.available.collectAsStateWithLifecycle()
    val watchesLeft by viewModel.watchesLeft.collectAsStateWithLifecycle()
    val adState by viewModel.adState.collectAsStateWithLifecycle()
    var open by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    // When the last word was found, on the solve clock. Reset by each new word and by the board
    // arriving, so the count is always "since anything happened" and never since the screen opened.
    // Taken in composition rather than in an effect: an effect would run a frame late, and for that
    // frame a board resumed with minutes already on its clock would read as minutes without a word.
    val found = puzzle?.found?.size
    val since = remember(found) { elapsed }

    val due = puzzle != null && !puzzle.isComplete && elapsed - since >= STUCK_AFTER_SECONDS
    val leads = available && watchesLeft > 0 && adState != RewardedAds.State.BLOCKED
    LaunchedEffect(due, leads) {
        if (due && leads) {
            open = true
            // Used up on showing rather than on answering, so a dialog dismissed by turning the
            // phone or by the back button still counts as today's one.
            viewModel.shown()
        }
    }

    if (open) {
        StackedDialog(
            title = stringResource(R.string.stuck_title),
            primary = stringResource(R.string.stuck_go),
            onPrimary = {
                open = false
                onShop()
            },
            secondary = stringResource(R.string.stuck_later),
            onSecondary = { open = false },
            onDismiss = { open = false },
        ) {
            Text(text = stringResource(R.string.stuck_body), textAlign = TextAlign.Center)
            CoinAmount(
                amount = Rewards.FOR_WATCHING_AD,
                text = stringResource(R.string.shop_reward, Rewards.FOR_WATCHING_AD),
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}
