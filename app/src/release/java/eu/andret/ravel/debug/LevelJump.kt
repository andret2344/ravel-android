package eu.andret.ravel.debug

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Nothing, which is the point.
 *
 * The twin of this file in `src/debug` draws a button that sets the player's level outright. This
 * variant is what a release build compiles instead, so the dialog, the cast to the concrete store
 * and the developer-only English strings are absent from the shipped APK rather than merely
 * unreachable inside it - see that file for why the difference is worth two files.
 *
 * The signature has to match its twin exactly. If this stops compiling, the debug file changed and
 * this one did not; that is the pair working, not a build to be silenced with a parameter default.
 *
 * The unused [modifier] is deliberate. Dropping it would let a call site pass something this variant
 * cannot accept, and the mismatch would only surface in a release build.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun LevelJump(modifier: Modifier = Modifier) = Unit
