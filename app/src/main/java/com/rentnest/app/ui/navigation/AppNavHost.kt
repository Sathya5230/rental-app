package com.rentnest.app.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.rentnest.app.domain.model.AppMode
import com.rentnest.app.domain.model.SessionState
import com.rentnest.app.ui.common.ChooseModeScreen
import com.rentnest.app.ui.common.LoginScreen
import com.rentnest.app.ui.common.NotificationsScreen
import com.rentnest.app.ui.common.OnboardingScreen
import com.rentnest.app.ui.common.ProfileScreen
import com.rentnest.app.ui.components.LocalNavAnimatedScope
import com.rentnest.app.ui.components.LocalSharedTransitionScope
import com.rentnest.app.ui.customer.*
import com.rentnest.app.ui.provider.*

fun NavController.enterMode(mode: AppMode) = navigate(if (mode == AppMode.CUSTOMER) HomeRoute else DashboardRoute) {
    popUpTo(graph.id) { inclusive = true }
    launchSingleTop = true
}

fun NavController.navigateToTab(route: Any, mode: AppMode) = navigate(route) {
    popUpTo(if (mode == AppMode.CUSTOMER) HomeRoute else DashboardRoute) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

private fun startFor(s: SessionState): Any = when {
    !s.onboarded -> OnboardingRoute
    !s.loggedIn -> LoginRoute
    !s.modeChosen -> ChooseModeRoute
    s.mode == AppMode.CUSTOMER -> HomeRoute
    else -> DashboardRoute
}

inline fun <reified T : Any> NavGraphBuilder.screen(noinline content: @Composable (NavBackStackEntry) -> Unit) {
    composable<T> { entry -> CompositionLocalProvider(LocalNavAnimatedScope provides this) { content(entry) } }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppNavHost(navController: NavHostController, session: SessionState, modifier: Modifier = Modifier) {
    val start = remember { startFor(session) }
    val nav = navController
    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            NavHost(
                navController = nav,
                startDestination = start,
                modifier = modifier,
                enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 10 } },
                exitTransition = { fadeOut(tween(160)) },
                popEnterTransition = { fadeIn(tween(220)) },
                popExitTransition = { fadeOut(tween(160)) + slideOutHorizontally(tween(220)) { it / 10 } },
            ) {
                screen<OnboardingRoute> { OnboardingScreen(onFinished = { nav.navigate(LoginRoute) { popUpTo(OnboardingRoute) { inclusive = true } } }) }
                screen<LoginRoute> { LoginScreen(onLoggedIn = { nav.navigate(ChooseModeRoute) { popUpTo(LoginRoute) { inclusive = true } } }) }
                screen<ChooseModeRoute> { ChooseModeScreen(onModeChosen = { nav.enterMode(it) }) }
                screen<ProfileRoute> {
                    ProfileScreen(
                        onModeSwitched = { nav.enterMode(it) },
                        onLoggedOut = { nav.navigate(LoginRoute) { popUpTo(nav.graph.id) { inclusive = true } } },
                    )
                }
                screen<NotificationsRoute> {
                    NotificationsScreen(
                        onBack = { nav.popBackStack() },
                        onOpenBooking = { mode -> nav.navigateToTab(if (mode == AppMode.CUSTOMER) RentalsRoute else ProviderBookingsRoute, mode) },
                    )
                }

                // Customer
                screen<HomeRoute> {
                    HomeScreen(
                        onOpenItem = { nav.navigate(ItemDetailsRoute(it)) },
                        onOpenSearch = { nav.navigate(SearchRoute(it ?: -1L)) },
                        onOpenNotifications = { nav.navigate(NotificationsRoute) },
                    )
                }
                screen<SearchRoute> { SearchScreen(onOpenItem = { nav.navigate(ItemDetailsRoute(it)) }) }
                screen<ItemDetailsRoute> {
                    ItemDetailsScreen(onBack = { nav.popBackStack() }, onSelectDates = { nav.navigate(BookingDatesRoute(it)) })
                }
                screen<BookingDatesRoute> {
                    BookingDatesScreen(
                        onBack = { nav.popBackStack() },
                        onContinue = { item, start, end -> nav.navigate(CheckoutRoute(item, start, end)) },
                    )
                }
                screen<CheckoutRoute> {
                    CheckoutScreen(
                        onBack = { nav.popBackStack() },
                        onBooked = { id -> nav.navigate(BookingSuccessRoute(id)) { popUpTo(HomeRoute) } },
                    )
                }
                screen<BookingSuccessRoute> {
                    BookingSuccessScreen(
                        onViewRentals = { nav.navigateToTab(RentalsRoute, AppMode.CUSTOMER) },
                        onHome = { nav.navigateToTab(HomeRoute, AppMode.CUSTOMER) },
                    )
                }
                screen<RentalsRoute> { RentalsScreen(onOpenItem = { nav.navigate(ItemDetailsRoute(it)) }) }
                screen<SavedRoute> {
                    SavedScreen(onOpenItem = { nav.navigate(ItemDetailsRoute(it)) }, onExplore = { nav.navigateToTab(SearchRoute(), AppMode.CUSTOMER) })
                }

                // Provider
                screen<DashboardRoute> {
                    DashboardScreen(
                        onOpenNotifications = { nav.navigate(NotificationsRoute) },
                        onAddItem = { nav.navigate(ItemEditorRoute()) },
                        onOpenItem = { nav.navigate(ItemEditorRoute(it)) },
                        onOpenBookings = { nav.navigateToTab(ProviderBookingsRoute, AppMode.PROVIDER) },
                    )
                }
                screen<InventoryRoute> {
                    InventoryScreen(onAddItem = { nav.navigate(ItemEditorRoute()) }, onOpenItem = { nav.navigate(ItemEditorRoute(it)) })
                }
                screen<ItemEditorRoute> { ItemEditorScreen(onBack = { nav.popBackStack() }) }
                screen<ProviderBookingsRoute> {
                    ProviderBookingsScreen(onHandover = { id, isReturn -> nav.navigate(HandoverRoute(id, isReturn)) })
                }
                screen<HandoverRoute> { HandoverScreen(onBack = { nav.popBackStack() }) }
                screen<EarningsRoute> { EarningsScreen() }
            }
        }
    }
}
