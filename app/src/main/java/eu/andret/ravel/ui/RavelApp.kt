package eu.andret.ravel.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailDefaults
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
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
import eu.andret.ravel.game.DailyDate
import eu.andret.ravel.ui.about.LicenseRoute
import eu.andret.ravel.ui.about.LicenseScreen
import eu.andret.ravel.ui.about.LicensesScreen
import eu.andret.ravel.ui.daily.DailyMenuScreen
import eu.andret.ravel.ui.daily.DailyRoute
import eu.andret.ravel.ui.daily.DailyScreen
import eu.andret.ravel.ui.level.LevelMenuScreen
import eu.andret.ravel.ui.level.LevelRoute
import eu.andret.ravel.ui.level.LevelScreen
import eu.andret.ravel.ui.settings.SettingsScreen
import eu.andret.ravel.ui.shop.ShopScreen
import eu.andret.ravel.ui.stats.StatsScreen
import eu.andret.ravel.ui.tutorial.TutorialScreen

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
 * @param icon the tab's shape while it is not selected: a Material Symbols outline, vectors copied
 *   into `res/drawable` and not the `material-icons` artifacts, which are no longer developed and
 *   would bring thousands of icons to use five.
 *   The tabs used to be Unicode geometric characters (a square with lines for the stats, a dotted
 *   circle for the daily), which failed twice. They came from whatever fallback font the device
 *   had, so they sat at a different size and height on every phone and filled barely half of the
 *   24 dp an icon is given. They were also abstract on purpose, and a diamond does not say "shop" at
 *   any size, so the labels underneath did all the work. Pictographs were avoided at the time
 *   because the daily's sun was a color emoji that ignored the bar's tint; a vector takes the tint
 *   like any other icon, so that reason applies to emoji only, and a cogwheel is fine.
 * @param selectedIcon the same symbol filled, for the selected tab, the way Material 3 marks it: the
 *   selection then changes the shape as well as the color, which still reads for a player who
 *   cannot tell the two tints apart.
 */
enum class RavelTab(
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
    @param:DrawableRes val selectedIcon: Int,
    val enabled: Boolean,
    val route: String,
    val prefix: String = route,
) {
    STATS(
        R.string.tab_stats,
        R.drawable.ic_tab_stats,
        R.drawable.ic_tab_stats_selected,
        enabled = true,
        route = STATS_ROUTE,
    ),
    DAILY(
        R.string.tab_daily,
        R.drawable.ic_tab_daily,
        R.drawable.ic_tab_daily_selected,
        enabled = true,
        route = DailyRoute.MENU,
    ),
    MAIN(
        R.string.tab_main,
        R.drawable.ic_tab_main,
        R.drawable.ic_tab_main_selected,
        enabled = true,
        route = LevelRoute.MENU,
    ),

    // Enabled because the hint buttons send players here when they run out of coins, and a
    // destination that cannot be reached is not a destination.
    SHOP(
        R.string.tab_shop,
        R.drawable.ic_tab_shop,
        R.drawable.ic_tab_shop_selected,
        enabled = true,
        route = SHOP_ROUTE,
    ),
    SETTINGS(
        R.string.tab_settings,
        R.drawable.ic_tab_settings,
        R.drawable.ic_tab_settings_selected,
        enabled = true,
        route = SETTINGS_ROUTE,
    ),
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
    val tutorial by viewModel.tutorial.collectAsStateWithLifecycle()
    val openDaily by viewModel.openDaily.collectAsStateWithLifecycle()
    val offer by viewModel.offer.collectAsStateWithLifecycle()
    val dailyWaiting by viewModel.dailyWaiting.collectAsStateWithLifecycle()
    val giftWaiting by viewModel.giftWaiting.collectAsStateWithLifecycle()

    // Which tabs carry a dot, as what the dot says to TalkBack, or null for no dot. Worked out once,
    // here, so the bar and the rail cannot disagree.
    val dotOf = { tab: RavelTab ->
        when (tab) {
            RavelTab.DAILY -> R.string.tab_dot_daily.takeIf { dailyWaiting }
            RavelTab.SHOP -> R.string.tab_dot_gift.takeIf { giftWaiting }
            else -> null
        }
    }

    // The dots follow the date while the game is on screen and stop with it, so nothing ticks for
    // an app nobody is looking at - the same arrangement as the daily menu's own.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, viewModel) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.followToday() }
    }

    // The tutorial is laid over the shell rather than swapped in for it, so the tabs keep their back
    // stacks underneath: replayed from the options tab, closing it lands back on the options tab.
    Box(modifier = modifier.fillMaxSize()) {
        /*
         * Where the five destinations live depends on the shape of the window, not on the device.
         *
         * A bottom bar spends height, and height is the dimension a wide window has least of: on a
         * phone lying on its side the bar and its labels are about a fifth of the screen, taken off
         * the top of the one thing a board screen is for. Down the side it costs width instead,
         * which the same window has to spare - and which the board cannot use anyway, being square.
         *
         * The question is [AdaptiveScreen]'s, the same one every screen with two arrangements asks.
         */
        AdaptiveScreen(modifier = Modifier) { wide ->
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
                                    icon = {
                                        TabIcon(tab, tab == selected, dotOf(tab), NavigationBarDefaults.containerColor)
                                    },
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
                        // accept - so the icon, which is the part with a decision behind it, is shared
                        // and the four wiring arguments are not.
                        NavigationRail {
                            // Centered by the two spacers rather than by an arrangement, so it does not
                            // matter which end Material's own default hangs the items from.
                            Spacer(Modifier.weight(1f))
                            RavelTab.entries.forEach { tab ->
                                NavigationRailItem(
                                    selected = tab == selected,
                                    onClick = { navController.switchTo(tab) },
                                    enabled = tab.enabled,
                                    icon = {
                                        TabIcon(tab, tab == selected, dotOf(tab), NavigationRailDefaults.ContainerColor)
                                    },
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
                                onDaily = { navController.switchTo(RavelTab.DAILY) },
                                modifier = content,
                            )
                        }
                        composable(SHOP_ROUTE) { ShopScreen(modifier = content) }
                        composable(STATS_ROUTE) { StatsScreen(modifier = content) }
                        composable(SETTINGS_ROUTE) {
                            SettingsScreen(
                                onLicenses = { navController.navigate(LicenseRoute.LIST) },
                                modifier = content,
                            )
                        }
                        composable(LicenseRoute.LIST) {
                            LicensesScreen(
                                onLicense = { navController.navigate(LicenseRoute.of(it)) },
                                onBack = { navController.popBackStack() },
                                modifier = content,
                            )
                        }
                        composable(
                            route = LicenseRoute.DETAIL,
                            arguments = listOf(
                                navArgument(LicenseRoute.ARG_LICENSE) { type = NavType.StringType },
                            ),
                        ) { entry ->
                            LicenseScreen(
                                license = LicenseRoute.from(entry),
                                onBack = { navController.popBackStack() },
                                modifier = content,
                            )
                        }
                    }
                    // Here, after the host, rather than at the top of the shell: the host is
                    // composed inside this adaptive layout's measuring, and navigating before it has
                    // set its graph is a crash. Effects run once the composition they sit in is
                    // applied, and this is the same one.
                    LaunchedEffect(openDaily) {
                        if (openDaily) {
                            // The same two steps as the launch prompt's "Play", for the same reason:
                            // backing out of the board lands on the daily tab, not where they were.
                            navController.switchTo(RavelTab.DAILY)
                            navController.navigate(DailyRoute.board(DailyDate.today(), review = false)) {
                                launchSingleTop = true
                            }
                            viewModel.openedDaily()
                        }
                    }
                }
            }
        }

        if (tutorial) TutorialScreen()
    }

    // Not while the tutorial is up. A dialog is a window of its own and would sit on top of the
    // lesson, asking a player who has not yet traced a word whether they want today's puzzle.
    if (prompt && !tutorial) {
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

    // The system's half of saying yes, on Android 13 and later. A no here leaves them off; the
    // options tab says they are blocked and opens the system settings from there.
    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) viewModel.enableNotifications() }

    // Never over a board: the question follows a solve, and the solve has its own dialog. Leaving
    // the board - by the completion dialog's own button or any other way - is when it comes up.
    // Not over the tutorial or the launch prompt either, for the reason the prompt gives above.
    val onBoard = entry?.destination?.route == DailyRoute.BOARD
    offer?.takeIf { !onBoard && !tutorial && !prompt }?.let { current ->
        NotificationOfferDialog(
            offer = current,
            onYes = {
                viewModel.closeOffer(current)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    viewModel.notificationsPermitted()
                ) {
                    viewModel.enableNotifications()
                } else {
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onNo = { viewModel.closeOffer(current) },
        )
    }
}

/**
 * One tab's shape, drawn the same whichever container it is sitting in, with a dot on it when
 * something there is waiting for the player - today's daily unsolved, today's gift untaken.
 *
 * Shared rather than written out twice, because the icon is the part of an item with a decision
 * behind it - see [RavelTab.icon] on why these are vectors and not characters - and a rail whose
 * icons had drifted from the bar's would be five tabs that looked like a different app on its side.
 *
 * No content description on the icon: the item's label is right under it and already names the
 * tab, and a description would have TalkBack read the name twice. The dot does get one, since it
 * is the one thing on the item the label does not say.
 *
 * The dot is [AttentionDot], with no number in it: there is only ever one thing waiting per tab,
 * and a "1" would be a count of something that cannot be more. Still hung on Material's
 * [BadgedBox], which knows where a badge goes on an icon in both containers.
 *
 * @param ring the color of the container the item sits in, for the dot's ring.
 */
@Composable
private fun TabIcon(tab: RavelTab, selected: Boolean, @StringRes dot: Int?, ring: Color) {
    BadgedBox(
        badge = {
            if (dot != null) {
                val description = stringResource(dot)
                AttentionDot(ring = ring, modifier = Modifier.semantics { contentDescription = description })
            }
        },
    ) {
        Icon(
            painter = painterResource(if (selected) tab.selectedIcon else tab.icon),
            contentDescription = null,
        )
    }
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
 * same tab pair traveled the other way has to reverse. Anything that stays inside one tab - a menu
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
