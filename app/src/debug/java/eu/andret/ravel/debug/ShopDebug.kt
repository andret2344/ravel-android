package eu.andret.ravel.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.andret.ravel.RavelApplication
import eu.andret.ravel.game.Rewards

/**
 * Today's ad watches and gift, set by hand, at the bottom of the shop.
 *
 * A pair with the empty twin in `src/release`, for every reason [LevelJump] gives: the controls and
 * their English strings are absent from a release build rather than unreachable inside it. Edit both
 * files or neither.
 *
 * Applied as they are pressed, without a confirmation: unlike the level jump, nothing here destroys
 * anything a developer could not set back with the same controls.
 */
@Composable
fun ShopDebug(modifier: Modifier = Modifier) {
    val ravel = LocalContext.current.applicationContext as RavelApplication
    val left by ravel.adAllowance.remaining.collectAsStateWithLifecycle()
    val gift by ravel.dailyGift.available.collectAsStateWithLifecycle()

    OutlinedCard(modifier = modifier.widthIn(max = 280.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Debug: today", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Ads left", modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { ravel.adAllowance.setRemaining(left - 1) }, enabled = left > 0) {
                    Text("-")
                }
                Text("$left/${Rewards.ADS_PER_DAY}")
                OutlinedButton(
                    onClick = { ravel.adAllowance.setRemaining(left + 1) },
                    enabled = left < Rewards.ADS_PER_DAY,
                ) {
                    Text("+")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Gift available", modifier = Modifier.weight(1f))
                Switch(checked = gift, onCheckedChange = ravel.dailyGift::setAvailable)
            }
        }
    }
}
