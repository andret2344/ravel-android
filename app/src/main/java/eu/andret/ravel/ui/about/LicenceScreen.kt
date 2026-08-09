package eu.andret.ravel.ui.about

import android.content.Context
import androidx.annotation.RawRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.andret.ravel.R

/**
 * One source, what it is used under, and the licence itself when the game carries it.
 *
 * A page rather than a dialog. Two of these are a screen of legal text, and a dialog that scrolls
 * further than the screen it dims is a worse reading surface than the screen would have been.
 */
@Composable
fun LicenceScreen(licence: Licence, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val links = LocalUriHandler.current
    val back = stringResource(R.string.licence_back)

    // Read once per licence rather than per frame. These are one or two kilobytes off the APK's own
    // resources, so the read is measured in microseconds and does not deserve a loading state - but
    // it is still a file read, and running it on every recomposition would be one per scrolled row.
    val body = remember(licence) { licence.fullText?.let { textOf(context, it) } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // No background: this is a back affordance on a reading page, and a filled tonal button
            // gives it the weight of an action the page is about. The chevron is drawn rather than
            // imported because the app carries no icon artifact at all - the tab bar uses glyphs -
            // and one shape is not worth the several hundred kilobytes material-icons would add.
            IconButton(
                onClick = onBack,
                modifier = Modifier.semantics { contentDescription = back },
            ) {
                val stroke = MaterialTheme.colorScheme.onSurface
                Canvas(modifier = Modifier.size(20.dp)) {
                    val path = Path().apply {
                        moveTo(size.width * 0.62f, size.height * 0.16f)
                        lineTo(size.width * 0.34f, size.height * 0.5f)
                        lineTo(size.width * 0.62f, size.height * 0.84f)
                    }
                    drawPath(
                        path = path,
                        color = stroke,
                        style = Stroke(
                            width = size.minDimension * 0.1f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                }
            }
            Text(
                text = stringResource(licence.source),
                style = MaterialTheme.typography.headlineSmall,
            )
        }

        Text(
            text = stringResource(licence.licence),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(licence.notice),
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(
            onClick = { links.openUri(licence.url) },
            // Flush with the text above it: a button indented by its own default padding reads as
            // belonging to something further right, and there is nothing further right.
            contentPadding = PaddingValues(0.dp),
        ) {
            Text(
                text = stringResource(R.string.licence_open),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Start,
            )
        }

        body?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The bundled licence, re-wrapped for a phone.
 *
 * The files are stored exactly as their source ships them, which means hard line breaks at about 72
 * columns - a width no phone has. Left alone they wrap twice, once by the file and once by the
 * screen, and every second line is three words long.
 *
 * Only the line breaks inside a paragraph go. Blank lines stay, because they are the only structure
 * these texts have, and no word, no punctuation mark and no order changes - which is what "retain
 * the notice" is asking for. Reformatting is not editing; the alternative on offer was a horizontal
 * scroll bar over a copyright notice.
 */
private fun textOf(context: Context, @RawRes id: Int): String =
    context.resources.openRawResource(id).bufferedReader().use { it.readText() }
        .trim()
        .split(PARAGRAPH)
        .joinToString("\n\n") { paragraph -> paragraph.lines().joinToString(" ") { it.trim() } }

private val PARAGRAPH = Regex("\n[ \t]*\n")
