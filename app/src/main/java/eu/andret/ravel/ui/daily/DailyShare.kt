package eu.andret.ravel.ui.daily

import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import eu.andret.ravel.R
import eu.andret.ravel.game.DailyDate
import eu.andret.ravel.game.WordMark
import eu.andret.ravel.game.WordResult
import eu.andret.ravel.settings.ShareCard
import eu.andret.ravel.ui.game.asClock
import java.util.Date

/**
 * A solved day as a line of text somebody can paste anywhere.
 *
 * ## Why a picture and not a sentence
 *
 * This is the one mechanism in the game by which a player can bring another one, and the shape it
 * takes is not decoration. What spread the game this copies was not the numbers - it was a block of
 * coloured squares that read as a *result* at a glance, in a feed, to somebody who had not played.
 * A line saying "solved in 4:12" is a fact about a stranger and gets scrolled past; a row of squares
 * with one red one in it is a story, and the story is what gets a reply.
 *
 * ## What it is allowed to contain
 *
 * Nothing that gives the board away. That rules out the grid, any path, and any letter - between
 * them they are the answer.
 *
 * The **lengths are already public**, which is the useful part: every player of a given day is shown
 * the same list of them and nothing else, so a row of marks laid out in that order gives away
 * nothing while being directly comparable between two people. Position three means the same word to
 * both of them. See [eu.andret.ravel.game.marks], which is what fixes that order.
 *
 * ## Why the language is printed
 *
 * Because the same date in Polish and in English is two unrelated grids - the id and the generator
 * fix the spec, the dictionary decides where it lands - so two players on different settings who
 * compare times are comparing nothing at all. The code goes out as `PL` / `EN` rather than the
 * language's name on purpose: it is read the same by both of them, whichever of the two they are
 * reading it in, and it is not a fresh translation obligation for a two-letter marker.
 *
 * It comes off the [ShareCard] rather than the current setting, so switching language after solving
 * relabels nothing.
 *
 * ## Why the link is not optional
 *
 * Somebody who has never played this sees coloured squares and a date. Without a way to get to the
 * game from the same message, the whole thing is an ornament rather than the one route by which a
 * player arrives.
 */
internal object DailyShare {
    /**
     * The row: each word's length, then how much of it the player found.
     *
     * Colours and not glyphs, and three of them and not five, because this is read at thumbnail size
     * in somebody else's feed. Green is "I found it", red is "it was showing by the end", yellow is
     * the middle - which is the whole vocabulary needed to make a row tell a story.
     *
     * They are here rather than in a string resource because they are not language: a translator has
     * nothing to say about which colour means "found it", and putting them in `strings.xml` would
     * invite two locales to disagree about what the same row of squares means.
     *
     * All three are Unicode 12, so a device from before Android 10 has no glyph for them and shows
     * boxes. That is the sender's own preview looking wrong on a phone from 2019; the message
     * arrives intact and renders wherever it lands. Not worth a second format to avoid.
     *
     * **The number goes beside its square and not on a line under it.** A second line was the
     * obvious layout and does not survive contact with a proportional font: an emoji is about twice
     * the width of a digit, so the columns line up nowhere except the editor they were written in,
     * and a row of numbers that has slid one place along is worse than no numbers - it attaches
     * every mark to the wrong word. Pairing them removes the alignment from the problem entirely.
     */
    fun row(words: List<WordResult>): String = words.joinToString(" ") { word ->
        val square = when (word.mark) {
            WordMark.UNAIDED -> "🟩"
            WordMark.PARTLY_REVEALED -> "🟨"
            WordMark.REVEALED -> "🟥"
        }
        "${word.length}$square"
    }

    /**
     * The whole message, assembled from the layout in `share_text`.
     *
     * The layout is a translated resource and not built here, so a translator can move the
     * separators and the line breaks. That matters more than it looks: this is the one string in the
     * app that gets read outside it.
     *
     * The date goes through [DateFormat.getDateFormat], which is the phone's own short style, rather
     * than the long one the calendar card uses. A share line is read in one glance beside a row of
     * squares, and "10 August 2026" is most of that line.
     *
     * @param seconds what the day took, or null for the one case that can produce it: a board
     *   finished inside a single second of foreground time, which banks nothing. Read as zero, which
     *   is what it nearly was - unlike a null [eu.andret.ravel.settings.DayResult.seconds] on an old
     *   day, which means nobody timed it and which no card can be built for anyway.
     */
    fun text(context: Context, card: ShareCard, seconds: Int?): String = context.getString(
        R.string.share_text,
        DateFormat.getDateFormat(context).format(Date(DailyDate.startOfDay(card.ordinal))),
        card.language.code.uppercase(),
        card.size,
        row(card.words),
        context.resources.getQuantityString(
            R.plurals.share_words,
            card.wordCount,
            card.wordCount,
        ),
        asClock(seconds ?: 0),
        context.getString(R.string.share_link),
    )

    /**
     * The system's own share sheet, which is the only sharing this app does.
     *
     * Plain text and a chooser rather than a per-app integration: the player already has whichever
     * app they talk to people in, and a list of buttons for the ones this build happened to know
     * about would be a list that is wrong on somebody's phone from the first day.
     */
    fun intent(context: Context, card: ShareCard, seconds: Int?): Intent = Intent.createChooser(
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.share_subject))
            putExtra(Intent.EXTRA_TEXT, text(context, card, seconds))
        },
        null,
    )
}

/**
 * The one way to share a day, so both screens that offer it offer the same thing.
 *
 * Two call sites - the dialog the moment a board is finished, and the calendar's card for the rest
 * of the day - and neither builds the intent itself. The alternative is two buttons whose text drifts
 * apart and, worse, two places deciding what goes into a message the player publishes.
 *
 * Tonal rather than filled wherever it lands. It is a real action and has to look pressable, but it
 * is never the thing the screen is for: the dialog is about the reward and the calendar card is
 * about getting into a day.
 */
@Composable
internal fun ShareButton(card: ShareCard, seconds: Int?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    FilledTonalButton(
        onClick = { context.startActivity(DailyShare.intent(context, card, seconds)) },
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.share_action))
    }
}
