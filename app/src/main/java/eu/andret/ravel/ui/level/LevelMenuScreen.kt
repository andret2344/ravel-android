package eu.andret.ravel.ui.level

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.andret.ravel.R
import eu.andret.ravel.debug.LevelJump
import eu.andret.ravel.game.LevelModifier
import eu.andret.ravel.game.Rewards
import eu.andret.ravel.settings.LevelProgressState
import eu.andret.ravel.settings.SavedBoard
import eu.andret.ravel.ui.AdaptiveScreen
import eu.andret.ravel.ui.CoinAmount
import eu.andret.ravel.ui.MenuHeader
import eu.andret.ravel.ui.game.asClock
import eu.andret.ravel.ui.theme.noLengthsColors
import eu.andret.ravel.ui.theme.foundColors
import kotlinx.coroutines.launch

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
 * as ahead of it. The strip used to be eight and put the player one place left of center, which is a
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
    val chosen by viewModel.chosen.collectAsStateWithLifecycle()
    // Here and not inside the tile, because the tile is drawn in a different place in each
    // arrangement: turning the phone with the popup open would otherwise close it. Saveable, so a
    // turn that keeps the arrangement does not close it either.
    var choosing by rememberSaveable { mutableStateOf(false) }
    val mode = MenuMode(progress = progress, chosen = chosen, onOpen = { choosing = true })

    if (choosing) {
        ModifiersDialog(mode = mode, onChange = viewModel::setModifier, onDismiss = { choosing = false })
    }

    AdaptiveScreen(modifier = modifier) { wide ->
        if (wide) {
            WideMenu(progress = progress, balance = balance, mode = mode, onPlay = onPlay, onShop = onShop)
        } else {
            TallMenu(progress = progress, balance = balance, mode = mode, onPlay = onPlay, onShop = onShop)
        }
    }
}

/**
 * The modifiers' state as the menu needs it: what the player chose, and what that means for the
 * level on the card.
 *
 * @param chosen the player's choice for levels not yet opened.
 * @param onOpen opens the popup the choice is made in.
 */
private class MenuMode(
    progress: LevelProgressState,
    val chosen: Set<LevelModifier>,
    val onOpen: () -> Unit,
) {
    /** The modifiers the current level is already frozen with, or null while it has not been opened. */
    private val frozen: Set<LevelModifier>? = progress.modifiersFor(progress.current)

    /** The modifiers the level on the card will be played with. */
    val current: Set<LevelModifier> = frozen ?: chosen

    /**
     * Whether the choice says something the card's level will not do: that level has been opened
     * and its modifiers are fixed, so the choice waits for the next one.
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
            // them rather than inside any of them. The card and the modifiers stay together, closer
            // to each other than to anything else, because the modifiers are a choice about that card.
            Spacer(modifier = Modifier.weight(1f))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PlayCard(progress = progress, modifiers = mode.current, onPlay = { onPlay(progress.current) })
                ModifiersTile(mode)
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
                modifiers = mode.current,
                onPlay = { onPlay(progress.current) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ModifiersTile(mode)
                LadderGrid(current = progress.current, modifier = Modifier.weight(1f))
                LevelJump()
            }
        }
    }
}

@Composable
private fun Header(balance: Int, onShop: () -> Unit) {
    MenuHeader(title = stringResource(R.string.main_title), balance = balance, onShop = onShop)
}

/**
 * How many tiles the modifiers popup shows marked "coming soon", after the real ones.
 *
 * Tiles and not [LevelModifier] entries: they have no name, no bonus and no stored key, so there is
 * nothing for the rest of the game to read. They say the popup is a set that will grow. Each one is
 * replaced by an entry as that modifier is added - "Ukryte litery" took the second, "Słowa wspak"
 * the third - so the count only ever goes down, and with the entries it fills the 2x2 grid.
 */
private const val COMING_SOON_MODIFIERS = 1

/** A modifier's name, icon and (i) text - exhaustive, so a new one cannot ship without them. */
internal class ModifierLabels(
    @param:StringRes val name: Int,
    @param:DrawableRes val icon: Int,
    @param:StringRes val description: Int,
)

internal fun labelsOf(modifier: LevelModifier): ModifierLabels = when (modifier) {
    LevelModifier.NO_LENGTHS -> ModifierLabels(
        name = R.string.no_lengths_mode,
        icon = R.drawable.ic_modifier_no_lengths,
        description = R.string.no_lengths_description,
    )
    LevelModifier.HIDDEN_LETTERS -> ModifierLabels(
        name = R.string.hidden_letters_mode,
        icon = R.drawable.ic_modifier_hidden_letters,
        description = R.string.hidden_letters_description,
    )
    LevelModifier.REVERSED_WORDS -> ModifierLabels(
        name = R.string.reversed_words_mode,
        icon = R.drawable.ic_modifier_reversed_words,
        description = R.string.reversed_words_description,
    )
}

/**
 * The modifiers, on the tab they belong to rather than among the options, as one tile that opens
 * the popup they are chosen in ([ModifiersDialog]).
 *
 * They change what a level pays and what the player is shown, which makes them a choice about
 * playing and not a preference - and a choice that pays more is one a player has to be able to see
 * to make. Beside the card, so it is read as a choice about the level on it.
 *
 * One tile rather than a switch per modifier: a row each would push the road off a short phone as
 * the list grows, and the tile says what matters at a glance anyway - every modifier, the ones on in
 * a soft fill and the ones off grayed out in an outline, and what those on add to a level's pay. Soft
 * on purpose, by the owner's choice: the red stamp shouted from a tile that is a setting, and stays on
 * the level card only, where it marks the level itself. Every modifier rather than only those on, so
 * the tile also says what there is to choose. The extra, not the total: "+20" beside a modifier was
 * read as twenty on top of the ordinary pay - and only what the board acts on, so the figure is what
 * the level will really pay.
 *
 * In orange, once the level on the card has been opened and its modifiers are fixed, a line saying
 * the change waits for the next level - the one thing about this choice a player could not guess.
 *
 * **Its height never changes.** Every chip is always there, the same size on or off, and the orange
 * line is laid out always and only drawn when it applies, so a change made in the popup does not move
 * the road under the card.
 */
@Composable
private fun ModifiersTile(mode: MenuMode) {
    Card(onClick = mode.onOpen, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = stringResource(R.string.modifiers_title), style = MaterialTheme.typography.bodyLarge)
                // Wraps rather than squeezes: two names side by side already fill a phone's tile.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    LevelModifier.entries.forEach { ModifierChip(it, on = it in mode.chosen) }
                }
                PendingLine(shown = mode.pending)
            }
            val bonus = Rewards.forCompleting(mode.chosen) - Rewards.FOR_COMPLETING
            if (bonus > 0) {
                CoinAmount(
                    amount = bonus,
                    text = stringResource(R.string.done_gained, bonus),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            // No description: the card is already announced as a button with the title in it.
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Where the modifiers are switched on and off: a 2x2 grid of tiles, every real modifier first and
 * then [COMING_SOON_MODIFIERS] locked ones.
 *
 * Tiles and not a row with a switch each, by the owner's choice: a grid reads as a set of things to
 * pick from, and two columns are what a phone-wide dialog has room for - four across would leave a
 * tile too narrow for its name and its bonus. The grid is filled in rows of [MODIFIER_COLUMNS], and a
 * last row with one tile keeps the empty half rather than stretching the tile across it, so every
 * tile is the same size whatever the count.
 *
 * A change applies the moment a tile is pressed, like every other setting in the app; the button only
 * closes the popup, so there is no "cancel" that would have to mean something. The fine print that
 * modifiers are for levels only, and the orange line about the level already opened, sit above the
 * grid, laid out whether shown or not, so pressing a tile never moves the next one from under the
 * finger.
 */
@Composable
private fun ModifiersDialog(
    mode: MenuMode,
    onChange: (LevelModifier, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.modifiers_ok)) }
        },
        title = { Text(stringResource(R.string.modifiers_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column {
                    // A note on the whole grid, and drawn as one: smaller, slanted and fainter, so it
                    // reads as the fine print rather than as part of a tile.
                    Text(
                        text = stringResource(R.string.modifiers_scope),
                        style = MaterialTheme.typography.labelSmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                    PendingLine(shown = mode.pending)
                }
                // Null for a "coming soon" tile: it has no modifier behind it.
                val tiles: List<LevelModifier?> =
                    LevelModifier.entries + List(COMING_SOON_MODIFIERS) { null }
                tiles.chunked(MODIFIER_COLUMNS).forEach { row ->
                    // As tall as the tallest tile in it, so a tile with a bonus and one without line
                    // up as one row of equal cards rather than a ragged edge.
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { modifier ->
                            val layout = Modifier.weight(1f).fillMaxHeight()
                            if (modifier == null) {
                                ModifierToggle(
                                    name = stringResource(R.string.modifier_coming_soon),
                                    icon = R.drawable.ic_lock,
                                    description = null,
                                    bonus = null,
                                    checked = false,
                                    onCheckedChange = null,
                                    modifier = layout,
                                )
                            } else {
                                val labels = labelsOf(modifier)
                                ModifierToggle(
                                    name = stringResource(labels.name),
                                    icon = labels.icon,
                                    description = stringResource(labels.description),
                                    bonus = modifier.bonus,
                                    checked = modifier in mode.chosen,
                                    onCheckedChange = { onChange(modifier, it) },
                                    modifier = layout,
                                )
                            }
                        }
                        repeat(MODIFIER_COLUMNS - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
            }
        },
    )
}

/** How many modifier tiles sit side by side in the popup. */
private const val MODIFIER_COLUMNS = 2

/**
 * One modifier in the popup's grid, pressed in or not: its own icon on top with the (i) level with it
 * in the right corner, its name under the icon and what it adds to a level's pay under that, all
 * centered - the owner's layout.
 *
 * Pressed in reads three ways at once, because the difference has to survive a glance and a player
 * who does not see color: the tile is filled, its outline is the primary color and twice as thick,
 * and a check sits on the icon's corner like a badge. Not pressed is an outline on the dialog's own
 * surface. A "coming soon" tile is a padlock and a name at the disabled alpha, and pressing it does
 * nothing.
 *
 * **The description is behind the (i), not on the tile.** A two-column tile has room for a name, an
 * icon and a bonus, not for a sentence. The (i) opens it in a tooltip over the tile, and the tooltip
 * stays until a tap elsewhere - a timed one would vanish while a slow reader was still on the second
 * word. It is the one tap target inside another: the button keeps Material's 48dp, so a finger aimed
 * at the (i) does not land on the tile and flip the modifier instead, and the tile's own semantics
 * leave it out, so a screen reader reaches it as a button of its own.
 *
 * @param icon the modifier's own drawable, or the padlock for a "coming soon" tile.
 * @param bonus what it adds to a level's pay, or null for a tile with no modifier behind it yet.
 * @param description what the modifier does, or null for a "coming soon" tile, which has no (i).
 * @param onCheckedChange null for a "coming soon" tile.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModifierToggle(
    name: String,
    @DrawableRes icon: Int,
    description: String?,
    bonus: Int?,
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val enabled = onCheckedChange != null
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    val content = if (checked) colors.onSecondaryContainer else colors.onSurface
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (checked) colors.secondaryContainer else Color.Transparent)
            .border(
                width = if (checked) 2.dp else 1.dp,
                color = if (checked) colors.primary else colors.outlineVariant,
                shape = shape,
            )
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = { onCheckedChange?.invoke(it) },
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 12.dp)
                .alpha(if (enabled) 1f else 0.38f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // The icon is centered in a band as tall as the (i)'s 48dp touch target, which is pinned
            // to the tile's top edge minus the same 8dp - so the two share a vertical center by
            // construction. The icon is narrow, so the (i) in the corner beside it collides with
            // nothing, and the name below gets the tile's whole width: with the name in the (i)'s row
            // instead, its side margins left "Bez długości" about 60dp and broke it in two, with the
            // (i) hanging between the lines. No descriptions on either icon: the name says what the
            // modifier is, and the tile's checked state is announced by its role.
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(modifier = Modifier.size(40.dp)) {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        tint = content,
                        modifier = Modifier.size(32.dp).align(Alignment.Center),
                    )
                    if (checked) {
                        Icon(
                            painter = painterResource(R.drawable.ic_check),
                            contentDescription = null,
                            tint = colors.onPrimary,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(18.dp)
                                .background(colors.primary, CircleShape)
                                .padding(2.dp),
                        )
                    }
                }
            }
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                color = content,
                textAlign = TextAlign.Center,
            )
            // A "coming soon" tile has no figure and keeps the line's height anyway, so the icons of
            // both tiles in a row sit at the same depth.
            Box(modifier = Modifier.heightIn(min = 20.dp), contentAlignment = Alignment.Center) {
                if (bonus != null) {
                    CoinAmount(
                        amount = bonus,
                        text = stringResource(R.string.done_gained, bonus),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
        if (description != null) {
            val tooltip = rememberTooltipState(isPersistent = true)
            val scope = rememberCoroutineScope()
            // Aligned through a Box of its own: TooltipBox lays its anchor out inside its own
            // layout, so an align passed as its modifier never reaches this tile's Box and the
            // button landed in the top left corner, over the name.
            // Down by the column's top padding, so it centers on the icon's band.
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp)) {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                    tooltip = { PlainTooltip { Text(description) } },
                    state = tooltip,
                ) {
                    IconButton(onClick = { scope.launch { tooltip.show() } }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_info),
                            contentDescription = stringResource(R.string.modifier_about, name),
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * "Takes effect from the next level", in orange, laid out always and drawn only when [shown] - so
 * the tile and the popup keep their height whichever way it goes.
 */
@Composable
private fun PendingLine(shown: Boolean) {
    Text(
        text = stringResource(R.string.modifiers_pending),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        color = noLengthsColors.notice,
        modifier = if (shown) Modifier else Modifier.alpha(0f).clearAndSetSemantics {},
    )
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
    modifiers: Set<LevelModifier>,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Started once the board has been seen, not once something was traced on it: a player who
    // opened the level and looked has begun it, the clock has run, and its modifiers are already
    // fixed. The board, when there is one, is only what the caption counts from.
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
    val noLengths = LevelModifier.NO_LENGTHS in modifiers
    Card(
        onClick = onPlay,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        // Centered rather than hung from the top, which costs nothing where the card is the height of
        // its own contents and is the whole difference where it is not: given a column to fill, a
        // top-aligned invitation would sit above a hand's depth of empty card.
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // No description: the label under the number already says play or resume.
                Icon(
                    painter = painterResource(R.drawable.ic_play),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                )
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
                // On the card as well as on the tile, because the card is what the player presses: a
                // level frozen with a modifier while the tile now says otherwise still opens with it,
                // and this is where that has to be visible. The line is always as tall as a badge -
                // an invisible one holds it open when none is shown - so a change of modifiers does
                // not grow the card and move the tile under it.
                Box(modifier = Modifier.padding(top = 4.dp), contentAlignment = Alignment.Center) {
                    ModifierBadge(
                        levelModifier = LevelModifier.NO_LENGTHS,
                        modifier = Modifier.alpha(0f).clearAndSetSemantics {},
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // In the enum's order, as everywhere modifiers are listed.
                        LevelModifier.entries.filter { it in modifiers }.forEach { ModifierBadge(it) }
                    }
                }
                if (started) {
                    ResumeCaption(
                        board = board.takeIf { !noLengths },
                        seconds = progress.secondsFor(progress.current),
                    )
                }
            }
            if (started) NotificationDot(modifier = Modifier.align(Alignment.TopEnd))
        }
    }
}

/**
 * The stamp a level frozen with [levelModifier] wears on its card: the modifier's icon, the same one
 * the popup, the board and the completion dialog show, read out by its name.
 *
 * Icons rather than the words they were (owner's choice): with a third modifier, three stamps in
 * capitals - "BEZ DŁUGOŚCI", "UKRYTE LITERY", "SŁOWA WSPAK" - no longer fit one line on a narrow
 * phone, and a second line would either grow the card when the choice changes or hold an empty line
 * open on every level. An icon is the same width whatever the language.
 *
 * Red for [LevelModifier.NO_LENGTHS] ([eu.andret.ravel.ui.theme.NoLengthsColors]), because hiding the
 * lengths changes how the whole board is read; the soft tonal fill of the tab's chips for the rest,
 * which change a few cells or the way a word is traced, not what the board is.
 */
@Composable
private fun ModifierBadge(levelModifier: LevelModifier, modifier: Modifier = Modifier) {
    val red = levelModifier == LevelModifier.NO_LENGTHS
    val labels = labelsOf(levelModifier)
    Icon(
        painter = painterResource(labels.icon),
        contentDescription = stringResource(labels.name),
        tint = if (red) noLengthsColors.onFill else MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier
            .background(
                if (red) noLengthsColors.fill else MaterialTheme.colorScheme.secondaryContainer,
                RoundedCornerShape(6.dp),
            )
            .padding(horizontal = 10.dp, vertical = 3.dp)
            .size(18.dp),
    )
}

/**
 * One modifier on the levels tab's tile: its name in a soft tonal fill when it is on, in a gray
 * outline and grayed text when it is off. The same size either way - the outline is drawn over the
 * edge, not added to it - so turning one on never moves the others.
 *
 * Read out as its name and its state ("Bez długości, włączony"): the tile is one button, and its
 * merged description is the only way a screen reader hears which chips are filled.
 */
@Composable
private fun ModifierChip(levelModifier: LevelModifier, on: Boolean) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    val name = stringResource(labelsOf(levelModifier).name)
    val spoken = stringResource(
        R.string.modifier_state,
        name,
        stringResource(if (on) R.string.modifier_on else R.string.modifier_off),
    )
    Text(
        text = name,
        style = MaterialTheme.typography.labelMedium,
        color = if (on) colors.onSecondaryContainer else colors.onSurfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier
            .clearAndSetSemantics { contentDescription = spoken }
            .then(
                if (on) {
                    Modifier.background(colors.secondaryContainer, shape)
                } else {
                    Modifier.border(1.dp, colors.outlineVariant, shape)
                },
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/**
 * How far in, rather than a word: "2/5" is the reason to come back, and it is the only thing on the
 * card a player cannot already guess from the color.
 *
 * Without the count on a level with no lengths, where "2/5" is exactly what the modifier hides - the
 * time alone is left, and nothing at all for a level opened and not yet timed.
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
 * the color and the label on purpose: this is the one thing on the screen that has to survive being
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
 * the level being offered belongs. At eight the current level sat one place left of center, which is
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
 * One square of the road, colored by where the player stands relative to it.
 *
 * Green for done, matching every other "done" in the game. The current level is outlined as well as
 * filled, so it is findable without color and still findable once the fill beside it is the same
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
