package eu.andret.ravel.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import eu.andret.ravel.R
import eu.andret.ravel.ui.about.LicenceRoute
import eu.andret.ravel.ui.about.LicenceScreen
import eu.andret.ravel.ui.daily.DailyMenuScreen
import eu.andret.ravel.ui.daily.DailyRoute
import eu.andret.ravel.ui.daily.DailyScreen
import eu.andret.ravel.ui.level.LevelMenuScreen
import eu.andret.ravel.ui.level.LevelRoute
import eu.andret.ravel.ui.level.LevelScreen
import eu.andret.ravel.ui.settings.SettingsScreen
import eu.andret.ravel.ui.shop.ShopScreen
import eu.andret.ravel.ui.stats.StatsScreen

/** The routes with no screen file of their own, kept beside [DailyRoute] for the same reason. */
private const val SHOP_ROUTE = "shop"
private const val STATS_ROUTE = "stats"
private const val SETTINGS_ROUTE = "settings"

/**
 * The five destinations, in the order they sit on the bar.
 *
 * The order is not arbitrary and is not alphabetical: the two game modes are in the middle, where a
 * thumb reaches without moving, and the three tabs that are about the game rather than playing it -
 * the record, the coins, the switches - sit at the ends. [STATS] and [SETTINGS] are the outermost
 * because they are the two a player opens deliberately and rarely.
 *
 * [enabled] is false for anything with no screen behind it yet. Showing a tab that cannot be opened
 * is deliberate: it tells the player what the game will be, and it keeps the bar's shape from
 * changing under returning players when a tab grows a screen.
 *
 * @param prefix what a route has to start with to belong to this tab. A tab is not one screen -
 *   both game tabs are a menu and a board - and the bar has to stay lit on the right item while the
 *   player is two screens deep inside it.
 * @param glyph one shape per tab, all from the same family: geometric outlines with something inside
 *   them, and never pictographs. The sun that used to stand for the daily was a colour emoji on most
 *   devices, which put one bright yellow icon beside four that took the bar's own tint and dimmed
 *   properly when a tab was not selected. A cogwheel for the settings would do the same, which is
 *   why the settings are a dial.
 */
enum class RavelTab(
    @param:StringRes val label: Int,
    val glyph: String,
    val enabled: Boolean,
    val route: String,
    val prefix: String = route,
) {
    STATS(R.string.tab_stats, "▤", enabled = true, route = STATS_ROUTE),
    DAILY(R.string.tab_daily, "◉", enabled = true, route = DailyRoute.MENU),
    MAIN(R.string.tab_main, "▦", enabled = true, route = LevelRoute.MENU),

    // Enabled because the hint buttons send players here when they run out of coins, and a
    // destination that cannot be reached is not a destination.
    SHOP(R.string.tab_shop, "◈", enabled = true, route = SHOP_ROUTE),
    SETTINGS(R.string.tab_settings, "◎", enabled = true, route = SETTINGS_ROUTE),
}

@Composable
fun RavelApp(
    modifier: Modifier = Modifier,
    viewModel: RavelAppViewModel = viewModel(),
) {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val selected = tabOf(entry?.destination?.route)
    val prompt by viewModel.prompt.collectAsStateWithLifecycle()
    val remind by viewModel.remind.collectAsStateWithLifecycle()

    /*
     * Where the five destinations live depends on the shape of the window, not on the device.
     *
     * A bottom bar spends height, and height is the dimension a wide window has least of: on a phone
     * lying on its side the bar and its labels are about a fifth of the screen, taken off the top of
     * the one thing a board screen is for. Down the side it costs width instead, which the same
     * window has to spare - and which the board cannot use anyway, being square.
     *
     * The question is [AdaptiveScreen]'s, the same one every screen with two arrangements asks.
     */
    AdaptiveScreen(modifier = modifier) { wide ->
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            // Emitting nothing rather than not being there at all. Scaffold measures an empty slot
            // at no height and still reports the system's own bottom inset in its padding, so the
            // screens below keep clear of the gesture bar in both arrangements.
            bottomBar = {
                if (!wide) {
                    NavigationBar {
                        RavelTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = tab == selected,
                                onClick = { navController.switchTo(tab) },
                                enabled = tab.enabled,
                                icon = { TabGlyph(tab) },
                                label = { Text(stringResource(tab.label)) },
                            )
                        }
                    }
                }
            },
        ) { insets ->
            // The rail sits inside the content area and covers the start edge, applying the system's
            // start inset itself - so the screens beside it are given every inset except that one.
            // Handing them the whole set would indent them a second time, by the width of a cutout
            // the rail is already standing in front of.
            val direction = LocalLayoutDirection.current
            val content = if (wide) {
                Modifier.padding(
                    top = insets.calculateTopPadding(),
                    bottom = insets.calculateBottomPadding(),
                    end = insets.calculateEndPadding(direction),
                )
            } else {
                Modifier.padding(insets)
            }

            Row(modifier = Modifier.fillMaxSize()) {
                if (wide) {
                    // The same five items in the same order, in the other of Material's two
                    // containers for them. It is a second copy of the loop because a bar arranges its
                    // items across a row and a rail down a column, and there is no shape both scopes
                    // accept - so the glyph, which is the part with a decision behind it, is shared
                    // and the four wiring arguments are not.
                    NavigationRail {
                        // Centred by the two spacers rather than by an arrangement, so it does not
                        // matter which end Material's own default hangs the items from.
                        Spacer(Modifier.weight(1f))
                        RavelTab.entries.forEach { tab ->
                            NavigationRailItem(
                                selected = tab == selected,
                                onClick = { navController.switchTo(tab) },
                                enabled = tab.enabled,
                                icon = { TabGlyph(tab) },
                                label = { Text(stringResource(tab.label)) },
                            )
                        }
                        Spacer(Modifier.weight(1f))
                    }
                }
                NavHost(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    navController = navController,
                    // The game opens on the main mode and not on the daily, because the ladder is
                    // the mode a player can always spend an evening in, while the daily is one board
                    // that is often already done. The day they have not done it, the prompt below
                    // says so - which reaches them wherever they were going, rather than putting a
                    // solved board in front of everyone who was not.
                    startDestination = LevelRoute.MENU,
                    enterTransition = { tabShift()?.let(::slideIn) ?: DEEPER_IN },
                    exitTransition = { tabShift()?.let(::slideOut) ?: DEEPER_OUT },
                    popEnterTransition = { tabShift()?.let(::slideIn) ?: BACK_IN },
                    popExitTransition = { tabShift()?.let(::slideOut) ?: BACK_OUT },
                ) {
                    composable(DailyRoute.MENU) {
                        DailyMenuScreen(
                            onOpen = { ordinal, review ->
                                navController.navigate(DailyRoute.board(ordinal, review))
                            },
                            onShop = { navController.switchTo(RavelTab.SHOP) },
                            modifier = content,
                        )
                    }
                    composable(
                        route = DailyRoute.BOARD,
                        arguments = listOf(
                            navArgument(DailyRoute.ARG_ORDINAL) { type = NavType.LongType },
                            navArgument(DailyRoute.ARG_REVIEW) { type = NavType.BoolType },
                        ),
                    ) {
                        DailyScreen(
                            onBack = { navController.popBackStack() },
                            onShop = { navController.switchTo(RavelTab.SHOP) },
                            modifier = content,
                        )
                    }
                    composable(LevelRoute.MENU) {
                        LevelMenuScreen(
                            onPlay = { level -> navController.navigate(LevelRoute.board(level)) },
                            onShop = { navController.switchTo(RavelTab.SHOP) },
                            modifier = content,
                        )
                    }
                    composable(
                        route = LevelRoute.BOARD,
                        arguments = listOf(
                            navArgument(LevelRoute.ARG_LEVEL) { type = NavType.IntType },
                        ),
                    ) {
                        LevelScreen(
                            // The finished board takes the next one's place instead of stacking
                            // under it, so backing out of level eight lands on the ladder rather
                            // than walking back down through every grid already solved.
                            onNext = { level ->
                                navController.navigate(LevelRoute.board(level)) {
                                    popUpTo(LevelRoute.MENU)
                                }
                            },
                            onMenu = { navController.popBackStack() },
                            onShop = { navController.switchTo(RavelTab.SHOP) },
                            modifier = content,
                        )
                    }
                    composable(SHOP_ROUTE) { ShopScreen(modifier = content) }
                    composable(STATS_ROUTE) { StatsScreen(modifier = content) }
                    composable(SETTINGS_ROUTE) {
                        SettingsScreen(
                            onLicence = { navController.navigate(LicenceRoute.of(it)) },
                            modifier = content,
                        )
                    }
                    composable(
                        route = LicenceRoute.DETAIL,
                        arguments = listOf(
                            navArgument(LicenceRoute.ARG_LICENCE) { type = NavType.StringType },
                        ),
                    ) { entry ->
                        LicenceScreen(
                            licence = LicenceRoute.from(entry),
                            onBack = { navController.popBackStack() },
                            modifier = content,
                        )
                    }
                }
            }
        }
    }

    if (prompt) {
        DailyPromptDialog(
            started = viewModel.started,
            remind = remind,
            onRemind = viewModel::remind,
            onPlay = {
                viewModel.dismissPrompt()
                // The tab first, then the board on top of it. The player never sees the menu on the
                // way in - the second navigation lands in the same frame - but it is under the
                // board afterwards, so backing out of today's puzzle leaves them in the mode they
                // just agreed to play rather than back on the ladder they were sent from.
                navController.switchTo(RavelTab.DAILY)
                navController.navigate(DailyRoute.board(viewModel.today, review = false))
            },
            onDismiss = viewModel::dismissPrompt,
        )
    }
}

/**
 * One tab's shape, drawn the same whichever container it is sitting in.
 *
 * Shared rather than written out twice, because the glyph is the part of an item with a decision
 * behind it - see [RavelTab.glyph] on why these are outlines and never pictographs - and a rail whose
 * icons had drifted from the bar's would be five tabs that looked like a different app on its side.
 */
@Composable
private fun TabGlyph(tab: RavelTab) {
    Text(tab.glyph, style = MaterialTheme.typography.titleMedium)
}

/**
 * Moves to [tab] the way a bottom bar is expected to.
 *
 * Each tab keeps its own back stack across a switch: a player who leaves a half-solved board to
 * check the shop comes back to that board rather than to the menu in front of it, which matters
 * most for the one trip the game itself sends them on - the hint they could not afford.
 *
 * Popping back to the start destination keeps the stack from growing a tab deeper every time the
 * player touches the bar, which is what would otherwise make the system's back gesture walk through
 * a history of tab presses nobody remembers making.
 */
private fun NavHostController.switchTo(tab: RavelTab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Which tab [route] belongs to, by the prefix each one owns, or null for a route with no tab. */
private fun tabOf(route: String?): RavelTab? =
    route?.let { name -> RavelTab.entries.firstOrNull { name.startsWith(it.prefix) } }

/**
 * Which way along the bar this transition is going, or null when it is not going along it at all.
 *
 * The sign is the whole point: a tab two places to the right has to arrive from the right, and the
 * same tab pair travelled the other way has to reverse. Anything that stays inside one tab - a menu
 * opening its board, a board closing - answers null here and is animated as depth instead. Sliding
 * a board in from the side would say "this is beside the menu" about a screen that is inside it.
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabShift(): Int? {
    val from = tabOf(initialState.destination.route) ?: return null
    val to = tabOf(targetState.destination.route) ?: return null
    return (to.ordinal - from.ordinal).takeIf { it != 0 }
}

/** The screen arriving from the side [shift] points to. */
private fun slideIn(shift: Int): EnterTransition =
    slideInHorizontally(LATERAL_OFFSET) { width -> if (shift > 0) width else -width } +
        fadeIn(LATERAL)

/** The screen leaving the other way, so the two move together rather than crossing. */
private fun slideOut(shift: Int): ExitTransition =
    slideOutHorizontally(LATERAL_OFFSET) { width -> if (shift > 0) -width else width } +
        fadeOut(LATERAL)

/**
 * Sideways for tabs, in and out of the screen for everything else.
 *
 * Both are short. The default is 700 ms of fade, which is long enough that a player who taps two
 * tabs quickly watches three screens overlap - and a bottom bar is tapped quickly by definition.
 */
private val LATERAL = tween<Float>(durationMillis = 250)
private val LATERAL_OFFSET = tween<IntOffset>(durationMillis = 250)
private val DEPTH = tween<Float>(durationMillis = 200)

/** Going deeper: the new screen grows the last of the way in. */
private val DEEPER_IN = scaleIn(DEPTH, initialScale = 0.92f) + fadeIn(DEPTH)

/** The screen behind it, pushed slightly further off than it will come back from. */
private val DEEPER_OUT = scaleOut(DEPTH, targetScale = 1.06f) + fadeOut(DEPTH)

/** Coming back: the same movement run backwards, so back undoes what forward did. */
private val BACK_IN = scaleIn(DEPTH, initialScale = 1.06f) + fadeIn(DEPTH)

private val BACK_OUT = scaleOut(DEPTH, targetScale = 0.92f) + fadeOut(DEPTH)
