package eu.andret.ravel.ui.shop

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import eu.andret.ravel.RavelApplication
import kotlinx.coroutines.flow.StateFlow

/**
 * The shop's view of the wallet.
 *
 * Nothing but the balance for now. It exists rather than the screen reading the wallet directly
 * because the two things the shop is going to do - play an ad for coins, take a payment - are both
 * long-running work with a result to apply, and that is not something a composable should own.
 */
class ShopViewModel(application: Application) : AndroidViewModel(application) {
    val balance: StateFlow<Int> = (application as RavelApplication).wallet.balance
}
