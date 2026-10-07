package com.rentnest.app.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rentnest.app.data.seed.DemoDataManager
import com.rentnest.app.domain.DEMO_USER_ID
import com.rentnest.app.domain.model.*
import com.rentnest.app.domain.repository.CatalogRepository
import com.rentnest.app.domain.repository.SessionRepository
import com.rentnest.app.ui.components.Avatar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(val user: User? = null, val provider: Provider? = null, val session: SessionState = SessionState(), val resetting: Boolean = false)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    catalog: CatalogRepository,
    private val session: SessionRepository,
    private val demo: DemoDataManager,
) : ViewModel() {
    private val resetting = MutableStateFlow(false)
    val state = combine(catalog.user(DEMO_USER_ID), catalog.providerForUser(DEMO_USER_ID), session.session, resetting, ::ProfileUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    fun switchMode(mode: AppMode, done: (AppMode) -> Unit) = viewModelScope.launch { session.chooseMode(mode); done(mode) }
    fun setTheme(pref: ThemePref) = viewModelScope.launch { session.setTheme(pref) }
    fun logOut(done: () -> Unit) = viewModelScope.launch { session.logOut(); done() }
    fun reset(done: () -> Unit) = viewModelScope.launch {
        resetting.value = true
        demo.reset()
        resetting.value = false
        done()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onModeSwitched: (AppMode) -> Unit, onLoggedOut: () -> Unit, viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirmReset by remember { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text("Profile") }) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(state.user?.name.orEmpty(), size = 64.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(state.user?.name.orEmpty(), style = MaterialTheme.typography.titleLarge)
                    Text(state.user?.phone.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (state.session.mode == AppMode.PROVIDER) state.provider?.let { p ->
                Card(shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Storefront, null)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.shopName, style = MaterialTheme.typography.titleMedium)
                            Text("${p.locationText} · ★ ${p.rating} (${p.reviewCount})", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            SettingCard("Mode", "Switch between renting and running your shop.") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    AppMode.entries.forEachIndexed { i, mode ->
                        SegmentedButton(
                            selected = state.session.mode == mode,
                            onClick = { if (state.session.mode != mode) viewModel.switchMode(mode, onModeSwitched) },
                            shape = SegmentedButtonDefaults.itemShape(i, AppMode.entries.size),
                            icon = { Icon(if (mode == AppMode.CUSTOMER) Icons.Rounded.Search else Icons.Rounded.Storefront, null, Modifier.size(18.dp)) },
                        ) { Text(if (mode == AppMode.CUSTOMER) "Customer" else "Provider") }
                    }
                }
            }
            SettingCard("Appearance", null) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ThemePref.entries.forEachIndexed { i, pref ->
                        SegmentedButton(
                            selected = state.session.theme == pref,
                            onClick = { viewModel.setTheme(pref) },
                            shape = SegmentedButtonDefaults.itemShape(i, ThemePref.entries.size),
                        ) { Text(pref.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    }
                }
            }
            SettingCard("Demo", "Restore all sample items, bookings and notifications.") {
                OutlinedButton(onClick = { confirmReset = true }, enabled = !state.resetting, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.RestartAlt, null); Spacer(Modifier.width(8.dp)); Text(if (state.resetting) "Restoring…" else "Reset demo data")
                }
            }
            TextButton(onClick = { viewModel.logOut(onLoggedOut) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Icon(Icons.AutoMirrored.Rounded.Logout, null); Spacer(Modifier.width(8.dp)); Text("Log out")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset demo data?") },
            text = { Text("All changes you made will be replaced with fresh sample data.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    viewModel.reset { scope.launch { snackbar.showSnackbar("Demo data restored") } }
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SettingCard(title: String, subtitle: String?, content: @Composable ColumnScope.() -> Unit) {
    Card(shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}
