package eu.andret.ravel.ui.level

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.andret.ravel.R
import eu.andret.ravel.debug.LevelJump
import eu.andret.ravel.game.Rewards
import eu.andret.ravel.settings.LevelProgressState
import eu.andret.ravel.settings.SavedBoard
import eu.andret.ravel.ui.AdaptiveScreen
import eu.andret.ravel.ui.CoinAmount
import eu.andret.ravel.ui.game.BalanceButton
import eu.andret.ravel.ui.game.asClock
import eu.andret.ravel.ui.theme.foundColors
import eu.andret.ravel.ui.theme.hardColors

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
 * Tall gets a strip of five across the width, which is the dimension there is to spare; wide gets
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
    val hard by viewModel.hard.collectAsStateWithLifecycle()
    val mode = MenuMode(progress = progress, chosen = hard, onChange = viewModel::setHard)

    AdaptiveScreen(modifier = modifier) { wide ->
        if (wide) {
            WideMenu(progress = progress, balance = balance, mode = mode, onPlay = onPlay, onShop = onShop)
        } else {
            TallMenu(progress = progress, balance = balance, mode = mode, onPlay = onPlay, onShop = onShop)
        }
    }
}

/**
 * The hard-mode switch's state as the menu needs it: what the player chose, and what that means
 * for the level on the card.
 *
 * @param chosen the player's choice for levels not yet opened.
 */
private class MenuMode(
    progress: LevelProgressState,
    val chosen: Boolean,
    val onChange: (Boolean) -> Unit,
) {
    /** The mode the current level is already frozen in, or null while it has not been opened. */
    private val frozen: Boolean? = progress.hardFor(progress.current)

    /** Whether the level on the card will be played without lengths. */
    val current: Boolean = frozen ?: chosen

    /**
     * Whether the switch says something the card's level will not do: that level has been opened
     * and its mode is fixed, so the choice waits for the next one.
     */
    val pending: Boolean = frozen != null && frozen != chosen
}

@Composable
private fun TallMenu(
    progress: LevelProgressState,
    balance: Int,
    mode: MenuMode,
    onPlay: (level: Int) -> Unit,
    onShop: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                // Scrolls, unlike the board this menu leads to. Nothing here is traced over, so
                // there is no gesture for a scroll to steal - and on a short phone the card, the road
                // and the debug button can outgrow the screen, where a menu that clipped its own play
                // button would be a tab with no way into the game.
                .verticalScroll(rememberScrollState())
                // At least the window's height, so on a tall phone the spacers below share out the
                // room instead of the whole tab bunching under the heading with a hand's depth of
                // nothing under the road. Past that height it simply scrolls, spacers at zero.
                .heightIn(min = maxHeight)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Header(balance = balance, onShop = onShop)
            // Three groups - the heading, the way in, the road - with the spare height between
            // them rather than inside any of them. The card and its switch stay together, closer to
            // each other than to anything else, because the switch is a choice about that card.
            Spacer(modifier = Modifier.weight(1f))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PlayCard(progress = progress, hard = mode.current, onPlay = { onPlay(progress.current) })
                HardModeSwitch(mode)
            }
            Spacer(modifier = Modifier.weight(1f))
            LadderStrip(progress.current)
            Spacer(modifier = Modifier.weight(1f))
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
    mode: MenuMode,
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
                hard = mode.current,
                onPlay = { onPlay(progress.current) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HardModeSwitch(mode)
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
 * Hard mode, on the tab it belongs to rather than among the options.
 *
 * It changes what a level pays and what the player is shown, which makes it a choice about playing
 * and not a preference - and a choice that pays more is one a player has to be able to see to make.
 * Beside the card, so it is read as a choice about the level on it.
 *
 * Toggleable on the whole row, like the haptics switch in the options tab, for the same reason: a
 * finger that lands on the words means the switch. Under the name, three lines: what the switch is
 * set to do (lengths and count shown or hidden), the fine print that it is for levels only, and - in
 * orange, once the level on the card has been opened and its mode is fixed - that the change waits
 * for the next level, which is the one thing about this switch a player could not guess. The coin
 * figure is what the mode adds to a level's pay.
 *
 * **Its height never changes.** Flipping it used to swap the first line for a longer one, which
 * wrapped, which grew the card and moved the switch from under the finger that flipped it. Now both
 * variants of the first line are laid out on top of each other, and the orange line is laid out
 * always and only drawn when it applies.
 */
@Composable
private fun HardModeSwitch(mode: MenuMode) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = mode.chosen, onValueChange = mode.onChange, role = Role.Switch)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = stringResource(R.string.hard_mode), style = MaterialTheme.typography.bodyLarge)
                Box {
                    listOf(true, false).forEach { on ->
                        Text(
                            text = stringResource(
                                if (on) R.string.hard_mode_on else R.string.hard_mode_off,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = if (on == mode.chosen) {
                                Modifier
                            } else {
                                Modifier.alpha(0f).clearAndSetSemantics {}
                            },
                        )
                    }
                }
                // A note on the note, and drawn as one: smaller, slanted and fainter, so it reads as
                // the fine print under what the switch does rather than as a second thing it does.
                Text(
                    text = stringResource(R.string.hard_mode_scope),
                    style = MaterialTheme.typography.labelSmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
                Text(
                    text = stringResource(R.string.hard_mode_pending),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = hardColors.notice,
                    modifier = if (mode.pending) Modifier else Modifier.alpha(0f).clearAndSetSemantics {},
                )
            }
            // The extra, not the total: "+45" beside a switch was read as forty-five on top of the
            // ordinary pay. "+20" is what flipping it adds, which is the question a switch answers.
            val extra = Rewards.FOR_COMPLETING_HARD - Rewards.FOR_COMPLETING
            CoinAmount(
                amount = extra,
                text = stringResource(R.string.done_gained, extra),
                style = MaterialTheme.typography.labelLarge,
            )
            // Drawn, not clicked: the row already took the press.
            Switch(checked = mode.chosen, onCheckedChange = null)
        }
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
    hard: Boolean,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Started once the board has been seen, not once something was traced on it: a player who
    // opened the level and looked has begun it, the clock has run, and in hard mode its mode is
    // already fixed. The board, when there is one, is only what the caption counts from.
    val started = progress.started
    val board = progress.boardFor(progress.current)
    // The three ways the card answers "has this been started" are read here, at the top, rather
    // than at the four points inside the layout that used to ask. They are one question with one
    // answer, and asking it again beside every widget it changes buries that: what the card is
    // doing only becomes visible once the branches sit next to each other.
    val container = if (!started) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.primary
    }
    val content = if (!started) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onPrimary
    }
    val label = if (!started) R.string.level_play else R.string.level_resume
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
                // On the card as well as beside the switch, because the card is what the player
                // presses: a level frozen hard while the switch now says otherwise still opens hard,
                // and this is where that has to be visible. Always laid out and only shown when it
                // applies, so flipping the switch does not grow the card and push the switch itself
                // down from under the finger that flipped it.
                HardBadge(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .alpha(if (hard) 1f else 0f)
                        .then(if (hard) Modifier else Modifier.clearAndSetSemantics {}),
                )
                if (started) {
                    ResumeCaption(
                        board = board.takeIf { !hard },
                        seconds = progress.secondsFor(progress.current),
                    )
                }
            }
            if (started) NotificationDot(modifier = Modifier.align(Alignment.TopEnd))
        }
    }
}

/** The red stamp a hard level wears on its card - see [eu.andret.ravel.ui.theme.HardColors]. */
@Composable
private fun HardBadge(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.hard_badge),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.5.sp,
        color = hardColors.onFill,
        modifier = modifier
            .background(hardColors.fill, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/**
 * How far in, rather than a word: "2/5" is the reason to come back, and it is the only thing on the
 * card a player cannot already guess from the colour.
 *
 * Without the count on a hard level, where "2/5" is exactly what the mode hides - the time alone is
 * left, and nothing at all for a level opened and not yet timed.
 *
 * The clock beside it is the other half of that reason. Two words out of five is a different
 * proposition after forty seconds than after nine minutes, and the time already spent is the part a
 * player coming back tomorrow will not remember.
 */
@Composable
private fun ResumeCaption(board: SavedBoard?, seconds: Int) {
    val counted = board?.let { "${it.found.size}/${it.wordCount}" }
    // Zero while the level has been opened but not yet timed - the board reports its time when it
    // closes - and "0:00" would read as a stopwatch that is broken rather than one that has not
    // started.
    val clock = asClock(seconds).takeIf { seconds > 0 }
    val text = listOfNotNull(counted, clock).joinToString(" · ")
    if (text.isNotEmpty()) Text(text = text, style = MaterialTheme.typography.labelMedium)
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
 * An odd count, for the reason the block is nine: an odd count has a middle, and the middle is where
 * the level being offered belongs. At eight the current level sat one place left of centre, which is
 * a road with the player slightly off it.
 *
 * Five rather than the seven it was, because the squares carry numbers and the ladder has no end. At
 * seven a square on a 360dp phone was about 39dp wide, and a four-digit level is about 33dp of
 * `labelLarge` - so level 1000 sat on the edge at the default font and wrapped onto a second line at
 * a larger system font or on a narrower phone. Five squares are about 57dp on the same phone, which
 * holds four digits at 1.3x font scale even on a 320dp screen. Two fewer levels either side of the
 * player cost the road nothing it needed: it says "behind, here, ahead", and two of each says that.
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
 * being offered goes in the middle of each - that is what made the strip odd instead of eight. The
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
private const val STRIP = 5

/** The block is square, and three a side is what makes nine levels read as a block at all. */
private const val GRID_SIDE = 3
