package eu.andret.ravel.debug

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import eu.andret.ravel.RavelApplication
import eu.andret.ravel.settings.StoredLevelProgress

/**
 * A way to put the ladder wherever a developer needs it, on the screen the ladder lives on.
 *
 * ## Why this is a source set and not a flag
 *
 * There is a twin of this file in `src/release` whose body is empty, so the release variant compiles
 * a call to a function that does nothing and the dialog, the store cast and the strings are simply
 * not in the APK. The alternative - one file in `main` wrapped in `if (BuildConfig.DEBUG)` - relies
 * on R8 folding a constant and dropping the branch, and this project has already been bitten once by
 * treating R8's judgement as a given (see `proguard-rules.pro`). A cheat that sets the player's
 * progress is the wrong place to find out it survived the fold. It would also mean turning
 * `buildConfig` on, which is a whole build feature enabled to carry one boolean.
 *
 * The cost is that the two files must keep the same signature, which is what makes them a pair. A
 * change here that is not made in `src/release` breaks `assembleRelease` and nothing else, so the
 * failure is loud and lands on the person making the change.
 *
 * ## Why the text is hard-coded English
 *
 * Every user-facing string in this app is in `values/` and `values-pl/`, and `lintDebug` fails on a
 * missing translation. None of that applies here: no player sees this, and a debug-only string in
 * `values/` would be a translation obligation invented for a button that will never ship. The same
 * argument keeps `printPuzzle` out of the APK.
 *
 * ## Why the store is read from the context
 *
 * Screens reach the singletons through their view model, and this is not a screen - it is one button
 * that makes one synchronous write and holds nothing across a rotation but the text in a field. The
 * rule exists to stop a second store being built over the same preference file; `applicationContext`
 * hands back the one that already exists, so there is nothing to keep it away from.
 *
 * The cast is to [StoredLevelProgress] because [eu.andret.ravel.settings.LevelProgress] deliberately
 * has no way to set the level - see [StoredLevelProgress.jumpTo] for why the hatch is on the class.
 */
@Composable
fun LevelJump(modifier: Modifier = Modifier) {
    val levels = LocalContext.current.applicationContext.let { it as RavelApplication }.levels
    var asking by remember { mutableStateOf(false) }

    OutlinedButton(onClick = { asking = true }, modifier = modifier.fillMaxWidth()) {
        Text("Debug: jump to level")
    }
    if (asking) {
        JumpDialog(
            onDismiss = { asking = false },
            onConfirm = { level ->
                (levels as StoredLevelProgress).jumpTo(level)
                asking = false
            },
        )
    }
}

/**
 * The number, and the sentence saying what taking it will do.
 *
 * The warning is spelled out rather than assumed because the write is not undoable: the level below
 * the one typed becomes the highest completed, whatever it was before, and a part-finished board on
 * the old current level goes with it. A developer three levels into a board they were debugging
 * should have to read that before losing it.
 */
@Composable
private fun JumpDialog(onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var typed by remember { mutableStateOf("") }
    val level = typed.toIntOrNull()
    val valid = level != null && level >= 1

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Jump to level") },
        text = {
            OutlinedTextField(
                value = typed,
                // Filtered rather than validated, so there is never a character in the field that
                // the parse below is going to reject. A soft keyboard set to numbers is a hint and
                // not a guarantee - a hardware keyboard ignores it entirely.
                onValueChange = { entered -> typed = entered.filter(Char::isDigit).take(MAX_DIGITS) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                label = { Text("Level") },
                supportingText = {
                    Text(
                        text = "Marks every level below it as completed. Pays nothing: no coins, " +
                            "no words, no time. Discards the current level's board and hints.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(checkNotNull(level)) }, enabled = valid) {
                Text("Jump")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/**
 * Nine digits, which is every level anyone will ever type and short of `Int` overflowing.
 *
 * A cap rather than a `Long` parse: the level is an `Int` everywhere else in the game, and a field
 * that accepted twenty digits would turn `toIntOrNull` into the validation, which reads as a typo
 * being silently refused rather than as a limit.
 */
private const val MAX_DIGITS = 9
