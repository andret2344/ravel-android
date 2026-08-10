package eu.andret.ravel.ui.level

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.andret.ravel.R
import eu.andret.ravel.debug.LevelJump
import eu.andret.ravel.settings.LevelProgressState
import eu.andret.ravel.settings.SavedBoard
import eu.andret.ravel.ui.AdaptiveScreen
import eu.andret.ravel.ui.game.BalanceButton
import eu.andret.ravel.ui.game.asClock
import eu.andret.ravel.ui.theme.foundColors

/**
 * The main mode's front page: the one level that can be played, and the run it belongs to.
 *
 * There is no list of levels to pick from, because there is no picking. The mode is a ladder walked
 * in order - a level already beaten cannot be replayed and one further up cannot be reached - so a
 * grid of taps would be a screen mostly made of things that refuse to happen. What is on offer is
 * one thing, and it gets the whole card.
 *
 * The road under it is the same fact drawn as a position rather than a number: a few behind, this
 * one, a few ahead. It is not a control and nothing on it can be tapped. What it buys is the sense
 * of a road, which "Level 7" alone does not give - and it makes the rule visible without a sentence
 * explaining it, because the levels ahead are plainly there and plainly not offered.
 *
 * It is drawn twice, because a road is a shape and the two windows have opposite shapes to spend.
 * Tall gets a strip of seven across the width, which is the dimension there is to spare; wide gets
 * three rows of three beside the card, which turns the same levels into a block that fills a column
 * the card cannot use.
 *
 * Both counts are odd, and that is the one thing they have to agree on: an odd road has a middle
 * square, the middle square is the level being offered, and the same number of levels sit behind it
 * as ahead of it. The strip used to be eight and put the player one place left of centre, which is a
 * road they are standing slightly off.
 *
 * @param onPlay the level to open, which is only ever the current one.
 * @param onShop where the balance in the corner leads, as it does on every board.
 */
@Composable
fun LevelMenuScreen(
    onPlay: (level: Int) -> Unit,
    onShop: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LevelMenuViewModel = viewModel(),
) {
    val progress by viewModel.state.collectAsStateWithLifecycle()
    val balance by viewModel.balance.collectAsStateWithLifecycle()

    AdaptiveScreen(modifier = modifier) { wide ->
        if (wide) {
            WideMenu(progress = progress, balance = balance, onPlay = onPlay, onShop = onShop)
        } else {
            TallMenu(progress = progress, balance = balance, onPlay = onPlay, onShop = onShop)
        }
    }
}

@Composable
private fun TallMenu(
    progress: LevelProgressState,
    balance: Int,
    onPlay: (level: Int) -> Unit,
    onShop: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            // Scrolls, unlike the board this menu leads to. Nothing here is traced over, so there is
            // no gesture for a scroll to steal - and on a short phone the card, the road and the
            // debug button can outgrow the screen, where a menu that clipped its own play button
            // would be a tab with no way into the game.
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Header(balance = balance, onShop = onShop)
        PlayCard(progress = progress, onPlay = { onPlay(progress.current) })
        LadderStrip(progress.current)
        // Draws a button in a debug build and literally nothing in a release one - there are two
        // files behind this call and the release one has an empty body, so the shipped APK does not
        // contain a way to skip the ladder rather than containing one nobody can reach. See
        // eu.andret.ravel.debug.LevelJump.
        //
        // Called from here because this is the screen the ladder is on: a level set from anywhere
        // else would be a developer changing a number and then going to look at what it did.
        LevelJump()
    }
}

/**
 * The card down one side and the road down the other, neither of them scrolling.
 *
 * The card takes the height rather than a slice of it. It is the only thing on this tab anybody came
 * for, and a wide window is one where an invitation can be the size of a hand without costing
 * anything else its place - the road beside it is nine small squares that were never going to fill a
 * column on their own.
 *
 * The heading stays across the top instead of joining either side. It names the app and holds the
 * balance, which belong to the tab rather than to one half of it.
 */
@Composable
private fun WideMenu(
    progress: LevelProgressState,
    balance: Int,
    onPlay: (level: Int) -> Unit,
    onShop: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Header(balance = balance, onShop = onShop)
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            PlayCard(
                progress = progress,
                onPlay = { onPlay(progress.current) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LadderGrid(current = progress.current, modifier = Modifier.weight(1f))
                LevelJump()
            }
        }
    }
}

@Composable
private fun Header(balance: Int, onShop: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.main_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        BalanceButton(balance = balance, onShop = onShop)
    }
}

/**
 * The way into the level, which is two different offers wearing one card.
 *
 * - untouched: an invitation, in the primary container.
 * - part-way through: the same invitation turned up. Full primary rather than the container, a badge
 *   on the corner, the count of words already found, and a label that says finish rather than start.
 *   Three signals and not one, because the difference matters most to the player who opens the app
 *   for two minutes and needs to know at a glance whether there is something waiting.
 *
 * The level number carries the card. It is the player's position in the game and the only thing here
 * worth reading from across a room; the word under it says what tapping does, and the caption says
 * how far in they already are.
 */
@Composable
private fun PlayCard(
    progress: LevelProgressState,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val started = progress.boardFor(progress.current)
    // The three ways the card answers "has this been started" are read here, at the top, rather
    // than at the four points inside the layout that used to ask. They are one question with one
    // answer, and asking it again beside every widget it changes buries that: what the card is
    // doing only becomes visible once the branches sit next to each other.
    val container = if (started == null) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.primary
    }
    val content = if (started == null) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onPrimary
    }
    val label = if (started == null) R.string.level_play else R.string.level_resume
    Card(
        onClick = onPlay,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        // Centred rather than hung from the top, which costs nothing where the card is the height of
        // its own contents and is the whole difference where it is not: given a column to fill, a
        // top-aligned invitation would sit above a hand's depth of empty card.
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(text = "▶", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = stringResource(R.string.level_number, progress.current),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(label),
                    style = MaterialTheme.typography.labelLarge,
                )
                if (started != null) {
                    ResumeCaption(board = started, seconds = progress.secondsFor(progress.current))
                }
            }
            if (started != null) NotificationDot(modifier = Modifier.align(Alignment.TopEnd))
        }
    }
}

/**
 * How far in, rather than a word: "2/5" is the reason to come back, and it is the only thing on the
 * card a player cannot already guess from the colour.
 *
 * The clock beside it is the other half of that reason. Two words out of five is a different
 * proposition after forty seconds than after nine minutes, and the time already spent is the part a
 * player coming back tomorrow will not remember.
 */
@Composable
private fun ResumeCaption(board: SavedBoard, seconds: Int) {
    val counted = "${board.found.size}/${board.wordCount}"
    Text(
        // Zero while the level has been opened but not yet timed - the board reports its time when
        // it closes - and "0:00" would read as a stopwatch that is broken rather than one that has
        // not started.
        text = if (seconds > 0) "$counted · ${asClock(seconds)}" else counted,
        style = MaterialTheme.typography.labelMedium,
    )
}

/**
 * The notification dot, in the corner every notification dot has ever been in. It is redundant with
 * the colour and the label on purpose: this is the one thing on the screen that has to survive being
 * seen out of the corner of an eye.
 *
 * No content description: the caption beside it already says how far in the level is, and a screen
 * reader announcing a dot as well would be reading the redundancy out loud, which is the one place
 * redundancy stops helping.
 */
@Composable
private fun NotificationDot(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(8.dp)
            .size(14.dp)
            .background(MaterialTheme.colorScheme.surface, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .background(MaterialTheme.colorScheme.error, CircleShape),
        )
    }
}

/**
 * The road as a strip across the width: a few levels behind, the one on offer, a few ahead.
 *
 * A fixed window rather than the whole ladder, because the whole ladder has no end - the mode goes
 * on for as long as the player does - and a strip that grew by one square a level would be a strip
 * of unreadable squares by level fifty.
 *
 * The window is anchored on the current level and clipped at one, so a new player sees the road
 * starting rather than a row of blanks where their history would be. Nothing here is clickable, and
 * that is the point being made: the levels behind are done and the ones ahead are not open yet.
 *
 * Seven and not the eight it was, for the reason the block is nine: an odd count has a middle, and
 * the middle is where the level being offered belongs. At eight the current level sat one place left
 * of centre, which is a road with the player slightly off it - and the difference costs nothing,
 * since seven squares across a phone are larger and no harder to count than eight.
 */
@Composable
private fun LadderStrip(current: Int, modifier: Modifier = Modifier) {
    val first = firstOf(current, STRIP)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DoneSoFar(current)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (first until first + STRIP).forEach { level ->
                Rung(
                    level = level,
                    current = current,
                    modifier = Modifier.weight(1f).aspectRatio(1f),
                )
            }
        }
    }
}

/**
 * The same road as a block of three by three, for a window with height to fill and no width to spare.
 *
 * Nine rather than the strip's eight, and the difference is the whole reason this exists rather than
 * the strip being wrapped: three by three has a middle square, and putting the level on offer in it
 * gives the road the same number of levels behind and ahead. Read as a block that is what the shape
 * says - the player is in the middle of something - where a strip says a queue with the player near
 * the front of it.
 *
 * **The block is one square and the nine rungs divide it**, rather than nine squares laid out in
 * whatever space each of them lands in. Three rows of equally spaced squares is a square, so this is
 * the same trick the board uses: take the largest square that fits and let the contents share it out.
 * Sized instead off the column's width the three rows would come to more height than a column has and
 * the bottom row would be cut - the row holding the levels still to come - and sized off the height
 * alone the rungs would sit in a narrow band down the middle of a wide column with gaps twice their
 * own width between them, which reads as three columns of numbers rather than as one block.
 */
@Composable
private fun LadderGrid(current: Int, modifier: Modifier = Modifier) {
    val first = firstOf(current, GRID_SIDE * GRID_SIDE)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DoneSoFar(current)
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.aspectRatio(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(GRID_SIDE) { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        repeat(GRID_SIDE) { column ->
                            Rung(
                                level = first + row * GRID_SIDE + column,
                                current = current,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** How much of the ladder is behind the player, which is the one number the road does not draw. */
@Composable
private fun DoneSoFar(current: Int) {
    Text(
        text = pluralStringResource(R.plurals.levels_done, current - 1, current - 1),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * The first level a road of [size] shows: half of it behind [current], clipped at level one.
 *
 * Half and not a number of its own, because both roads have an odd number of squares and the level
 * being offered goes in the middle of each - that is what made the strip seven instead of eight. The
 * arithmetic is then the same sentence for both and cannot drift apart when either count changes.
 *
 * The clipping is the part with a decision in it. A player on level two gets the road beginning at
 * one rather than a window starting below it, so a new account sees a road that starts instead of a
 * row of blanks where its history would be. The cost is that the current level is not central for the
 * first few levels, which is the honest picture: there is nothing behind them yet.
 */
private fun firstOf(current: Int, size: Int) = maxOf(1, current - (size - 1) / 2)

/**
 * One square of the road, coloured by where the player stands relative to it.
 *
 * Green for done, matching every other "done" in the game. The current level is outlined as well as
 * filled, so it is findable without colour and still findable once the fill beside it is the same
 * family of brown. Everything ahead is the flat surface variant and, unlike the other two, its
 * number is dimmed - a locked level is a fact about the future, not a thing to read.
 */
@Composable
private fun Rung(level: Int, current: Int, modifier: Modifier = Modifier) {
    val done = level < current
    val now = level == current
    val description = stringResource(
        when {
            done -> R.string.level_rung_done
            now -> R.string.level_rung_current
            else -> R.string.level_rung_locked
        },
        level,
    )
    val background = when {
        done -> foundColors.container
        now -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val content = when {
        done -> foundColors.onContainer
        now -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val shape = RoundedCornerShape(10.dp)

    Box(
        modifier = modifier
            .background(background, shape)
            .border(
                width = 2.dp,
                color = if (now) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = shape,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = level.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = content,
            fontWeight = if (now) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/** The strip's length. Odd, so the level on offer has a middle square to sit in. */
private const val STRIP = 7

/** The block is square, and three a side is what makes nine levels read as a block at all. */
private const val GRID_SIDE = 3
