package com.rentnest.app.ui.navigation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import com.rentnest.app.domain.model.AppMode

data class TopLevelDestination(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val route: Any,
    val matches: (NavDestination) -> Boolean,
)

val CustomerTabs = listOf(
    TopLevelDestination("Home", Icons.Outlined.Home, Icons.Rounded.Home, HomeRoute) { it.hasRoute<HomeRoute>() },
    TopLevelDestination("Search", Icons.Outlined.Search, Icons.Rounded.Search, SearchRoute()) { it.hasRoute<SearchRoute>() },
    TopLevelDestination("Rentals", Icons.Outlined.EventAvailable, Icons.Rounded.EventAvailable, RentalsRoute) { it.hasRoute<RentalsRoute>() },
    TopLevelDestination("Saved", Icons.Outlined.FavoriteBorder, Icons.Rounded.Favorite, SavedRoute) { it.hasRoute<SavedRoute>() },
    TopLevelDestination("Profile", Icons.Outlined.Person, Icons.Rounded.Person, ProfileRoute) { it.hasRoute<ProfileRoute>() },
)

val ProviderTabs = listOf(
    TopLevelDestination("Dashboard", Icons.Outlined.SpaceDashboard, Icons.Rounded.SpaceDashboard, DashboardRoute) { it.hasRoute<DashboardRoute>() },
    TopLevelDestination("Inventory", Icons.Outlined.Inventory2, Icons.Rounded.Inventory2, InventoryRoute) { it.hasRoute<InventoryRoute>() },
    TopLevelDestination("Bookings", Icons.Outlined.CalendarMonth, Icons.Rounded.CalendarMonth, ProviderBookingsRoute) { it.hasRoute<ProviderBookingsRoute>() },
    TopLevelDestination("Earnings", Icons.Outlined.Payments, Icons.Rounded.Payments, EarningsRoute) { it.hasRoute<EarningsRoute>() },
    TopLevelDestination("Profile", Icons.Outlined.Person, Icons.Rounded.Person, ProfileRoute) { it.hasRoute<ProfileRoute>() },
)

fun tabsFor(mode: AppMode) = if (mode == AppMode.CUSTOMER) CustomerTabs else ProviderTabs

fun NavDestination?.isTab(tab: TopLevelDestination) = this?.hierarchy?.any(tab.matches) == true

@Composable
fun RentNestBottomBar(tabs: List<TopLevelDestination>, current: NavDestination?, badges: Map<String, Int>, onSelect: (TopLevelDestination) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        tabs.forEach { tab ->
            val selected = current.isTab(tab)
            val scale by animateFloatAsState(if (selected) 1.12f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow), label = "tab")
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(tab) },
                icon = {
                    BadgedBox(badge = { badges[tab.label]?.takeIf { it > 0 }?.let { Badge { Text("$it") } } }) {
                        Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null, modifier = Modifier.scale(scale))
                    }
                },
                label = { Text(tab.label) },
            )
        }
    }
}
