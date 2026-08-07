package eu.andret.ravel.ui.shop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.andret.ravel.R
import eu.andret.ravel.game.Hint
import eu.andret.ravel.game.Rewards

/**
 * Where coins come from, and what they go on.
 *
 * Two of the three sources are not built yet, and they say so rather than being hidden: a player
 * sent here by an empty balance is owed an answer about how to fill it, even when part of the
 * answer is "not yet".
 */
@Composable
fun ShopScreen(modifier: Modifier = Modifier, viewModel: ShopViewModel = viewModel()) {
    val balance by viewModel.balance.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.shop_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = pluralStringResource(R.plurals.coins_balance, balance, balance),
            style = MaterialTheme.typography.titleLarge,
        )

        Source(
            title = stringResource(R.string.shop_earn_solving),
            detail = pluralStringResource(
                R.plurals.shop_earn_solving_detail,
                Rewards.FOR_COMPLETING,
                Rewards.FOR_COMPLETING,
            ),
        )
        Source(
            title = stringResource(R.string.shop_earn_ads),
            detail = stringResource(R.string.shop_coming_soon),
        )
        Source(
            title = stringResource(R.string.shop_buy),
            detail = stringResource(R.string.shop_coming_soon),
        )

        Text(
            text = stringResource(R.string.shop_prices, Hint.LETTER.cost, Hint.WORD.cost),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Source(title: String, detail: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
