package eu.andret.ravel.debug

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Nothing: the release twin of the shop's debug controls in `src/debug`, which set today's ad
 * watches and gift by hand. See [LevelJump] in this source set for why a pair of files rather than a
 * flag, and why the unused [modifier] stays. Edit both files or neither.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun ShopDebug(modifier: Modifier = Modifier) = Unit
