package com.rentnest.app.ui

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cottage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rentnest.app.domain.model.AppMode
import com.rentnest.app.domain.model.ThemePref
import com.rentnest.app.ui.navigation.*
import com.rentnest.app.ui.theme.RentNestTheme
import com.rentnest.app.ui.theme.isDark

@Composable
fun RentNestApp(viewModel: AppViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ready = state as? AppUiState.Ready
    val mode = ready?.session?.mode ?: AppMode.CUSTOMER
    val themePref = ready?.session?.theme ?: ThemePref.SYSTEM
    val dark = themePref.isDark()
    val activity = LocalActivity.current as? ComponentActivity
    DisposableEffect(dark) {
        val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
        activity?.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        onDispose {}
    }
    RentNestTheme(mode, themePref) {
        if (ready == null) Splash() else AppScaffold(ready)
    }
}

@Composable
private fun AppScaffold(state: AppUiState.Ready) {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val destination = entry?.destination
    val mode = state.session.mode
    val tabs = tabsFor(mode)
    val showBar = tabs.any { destination.isTab(it) }
    val badges = if (mode == AppMode.PROVIDER) mapOf("Bookings" to state.pendingRequests) else emptyMap()
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBar) RentNestBottomBar(tabs, destination, badges) { navController.navigateToTab(it.route, mode) }
        },
    ) { padding ->
        AppNavHost(
            navController = navController,
            session = state.session,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        )
    }
}

@Composable
private fun Splash() {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Cottage, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(12.dp))
            Text("RentNest", style = MaterialTheme.typography.headlineMedium)
        }
    }
}
