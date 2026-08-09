package eu.andret.ravel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.andret.ravel.R

/**
 * The screen the game opens on, while the consent question is being put to the device.
 *
 * It exists because that question has to be answered before anything else happens, and the
 * alternative was what this replaced: the game drew its first tab, the player started reading it,
 * and a consent form arrived on top a second later. A dialog that interrupts is a dialog that gets
 * dismissed by whichever button is nearest, which is the wrong way to collect an answer the app is
 * legally standing on.
 *
 * The name and a spinner, and nothing else. There is nothing here for the player to do and no
 * information they need - they are not waiting for their game, they are waiting for a question -
 * so a progress bar with a percentage would be inventing detail about a wait that is usually over
 * inside half a second. On a device that has already answered, this frame is never drawn at all.
 *
 * It is not an Android splash screen (`SplashScreen` API, `postSplashScreenTheme`) on purpose. That
 * one is drawn by the system before the activity exists, which is exactly when there is no activity
 * for a consent form to sit in front of, so it cannot cover the wait it would need to cover.
 */
@Composable
internal fun LoadingScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            CircularProgressIndicator()
        }
    }
}
