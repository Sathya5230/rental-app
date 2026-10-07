package com.rentnest.app.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentnest.app.domain.model.AppMode
import com.rentnest.app.domain.repository.SessionRepository
import com.rentnest.app.ui.components.PrimaryButton
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminLoginUiState(val checking: Boolean = false, val error: String? = null, val failures: Int = 0, val lockedOut: Boolean = false)

@HiltViewModel
class AdminLoginViewModel @Inject constructor(private val session: SessionRepository) : ViewModel() {
    private val _state = MutableStateFlow(AdminLoginUiState())
    val state = _state.asStateFlow()

    fun unlock(pin: String, done: () -> Unit) {
        val s = _state.value
        if (s.checking || s.lockedOut) return
        _state.update { it.copy(checking = true, error = null) }
        viewModelScope.launch {
            if (session.unlockAdmin(pin)) {
                session.chooseMode(AppMode.ADMIN)
                _state.update { AdminLoginUiState() }
                done()
                return@launch
            }
            val failures = _state.value.failures + 1
            if (failures >= MAX_ATTEMPTS) {
                _state.update { it.copy(checking = false, failures = 0, lockedOut = true, error = "Too many wrong PINs. Try again in 30 seconds.") }
                delay(30_000)
                _state.update { it.copy(lockedOut = false, error = null) }
            } else {
                _state.update { it.copy(checking = false, failures = failures, error = "Wrong PIN. ${MAX_ATTEMPTS - failures} attempts left.") }
            }
        }
    }

    private companion object { const val MAX_ATTEMPTS = 5 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminLoginScreen(onBack: () -> Unit, onUnlocked: () -> Unit, viewModel: AdminLoginViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var pin by rememberSaveable { mutableStateOf("") }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Admin sign in") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Rounded.AdminPanelSettings, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
            Text("Store admin only", style = MaterialTheme.typography.headlineSmall)
            Text(
                "The admin dashboard manages inventory, approves rental requests and texts customers about late returns. Enter the admin PIN to continue.",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = pin,
                onValueChange = { v -> pin = v.filter(Char::isDigit).take(6) },
                label = { Text("Admin PIN") },
                singleLine = true,
                isError = state.error != null,
                supportingText = { state.error?.let { Text(it) } },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryButton(
                "Unlock dashboard",
                onClick = { viewModel.unlock(pin) { pin = ""; onUnlocked() } },
                enabled = pin.length >= 4 && !state.lockedOut,
                loading = state.checking,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
