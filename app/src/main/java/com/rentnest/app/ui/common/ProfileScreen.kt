package com.rentnest.app.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rentnest.app.data.repository.DataStoreSessionRepository
import com.rentnest.app.data.seed.DemoDataManager
import com.rentnest.app.domain.ADMIN_USER_ID
import com.rentnest.app.domain.DEMO_USER_ID
import com.rentnest.app.domain.model.*
import com.rentnest.app.domain.repository.CatalogRepository
import com.rentnest.app.domain.repository.SessionRepository
import com.rentnest.app.ui.components.Avatar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(val user: User? = null, val provider: Provider? = null, val session: SessionState = SessionState(), val resetting: Boolean = false)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProfileViewModel @Inject constructor(
    catalog: CatalogRepository,
    private val session: SessionRepository,
    private val demo: DemoDataManager,
) : ViewModel() {
    private val resetting = MutableStateFlow(false)
    private val user = session.session.map { it.mode }.distinctUntilChanged().flatMapLatest { mode ->
        catalog.user(if (mode == AppMode.ADMIN) ADMIN_USER_ID else DEMO_USER_ID)
    }
    val state = combine(user, catalog.providerForUser(ADMIN_USER_ID), session.session, resetting, ::ProfileUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    fun exitAdmin(done: () -> Unit) = viewModelScope.launch { session.lockAdmin(); done() }
    fun changePin(current: String, new: String, done: (Boolean) -> Unit) = viewModelScope.launch { done(session.changeAdminPin(current, new)) }
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
fun ProfileScreen(onAdminSignIn: () -> Unit, onAdminExited: () -> Unit, onLoggedOut: () -> Unit, viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirmReset by remember { mutableStateOf(false) }
    var changingPin by remember { mutableStateOf(false) }
    val isAdmin = state.session.mode == AppMode.ADMIN
    Scaffold(topBar = { TopAppBar(title = { Text(if (isAdmin) "Admin" else "Profile") }) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(state.user?.name.orEmpty(), size = 64.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(state.user?.name.orEmpty(), style = MaterialTheme.typography.titleLarge)
                    Text(state.user?.phone.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (isAdmin) {
                state.provider?.let { p ->
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
                SettingCard("Admin security", "Only people with the PIN can open the admin dashboard. It locks again when the app restarts.") {
                    OutlinedButton(onClick = { changingPin = true }, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Password, null); Spacer(Modifier.width(8.dp)); Text("Change admin PIN")
                    }
                    Button(onClick = { viewModel.exitAdmin(onAdminExited) }, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Lock, null); Spacer(Modifier.width(8.dp)); Text("Lock & exit admin")
                    }
                }
            } else {
                SettingCard("Store admin", "Manage inventory, approve requests and handle returns.") {
                    OutlinedButton(onClick = onAdminSignIn, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.AdminPanelSettings, null); Spacer(Modifier.width(8.dp)); Text("Open admin dashboard")
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
            if (isAdmin) SettingCard("Demo", "Restore all sample items, bookings and notifications.") {
                OutlinedButton(onClick = { confirmReset = true }, enabled = !state.resetting, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.RestartAlt, null); Spacer(Modifier.width(8.dp)); Text(if (state.resetting) "Restoring…" else "Reset demo data")
                }
            }
            if (!isAdmin) TextButton(onClick = { viewModel.logOut(onLoggedOut) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
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
    if (changingPin) {
        ChangePinDialog(onDismiss = { changingPin = false }) { current, new ->
            viewModel.changePin(current, new) { ok ->
                if (ok) changingPin = false
                scope.launch { snackbar.showSnackbar(if (ok) "Admin PIN changed" else "Current PIN is wrong") }
            }
        }
    }
}

@Composable
private fun ChangePinDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val valid = DataStoreSessionRepository.isValidPin(new) && new == confirm && current.isNotEmpty()
    @Composable fun pinField(label: String, value: String, error: Boolean = false, onChange: (String) -> Unit) = OutlinedTextField(
        value, { onChange(it.filter(Char::isDigit).take(6)) }, label = { Text(label) }, singleLine = true, isError = error,
        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth(),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change admin PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                pinField("Current PIN", current) { current = it }
                pinField("New PIN (4–6 digits)", new) { new = it }
                pinField("Repeat new PIN", confirm, error = confirm.isNotEmpty() && confirm != new) { confirm = it }
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onSave(current, new) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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
