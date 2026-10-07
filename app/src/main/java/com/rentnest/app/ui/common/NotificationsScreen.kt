package com.rentnest.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rentnest.app.domain.ADMIN_USER_ID
import com.rentnest.app.domain.DEMO_USER_ID
import com.rentnest.app.domain.format.DateFormats
import com.rentnest.app.domain.model.AppMode
import com.rentnest.app.domain.model.AppNotification
import com.rentnest.app.domain.model.audience
import com.rentnest.app.domain.repository.NotificationRepository
import com.rentnest.app.domain.repository.SessionRepository
import com.rentnest.app.domain.time.TimeProvider
import com.rentnest.app.ui.components.EmptyState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationsUiState(val loading: Boolean = true, val mode: AppMode = AppMode.CUSTOMER, val items: List<AppNotification> = emptyList(), val now: Long = 0)

private val AppMode.recipient get() = if (this == AppMode.ADMIN) ADMIN_USER_ID else DEMO_USER_ID

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NotificationsViewModel @Inject constructor(
    session: SessionRepository,
    notifications: NotificationRepository,
    time: TimeProvider,
) : ViewModel() {
    private val mode = session.session.map { it.mode }.distinctUntilChanged()
    val state = mode.flatMapLatest { m ->
        notifications.notifications(m.recipient, m.audience).map { NotificationsUiState(false, m, it, time.nowMillis()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationsUiState())

    init {
        viewModelScope.launch {
            val m = mode.first()
            delay(1_500) // let the unread dots register before clearing them
            notifications.markAllRead(m.recipient, m.audience)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(onBack: () -> Unit, onOpenBooking: (AppMode) -> Unit, viewModel: NotificationsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Notifications") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
        )
    }) { padding ->
        if (!state.loading && state.items.isEmpty()) {
            EmptyState(Icons.Rounded.NotificationsNone, "All caught up", "Booking updates will show up here.", Modifier.padding(padding))
        } else {
            LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(state.items, key = { it.id }) { n ->
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = n.bookingId != null) { onOpenBooking(state.mode) }.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Notifications, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(n.title, style = MaterialTheme.typography.titleSmall)
                            Text(n.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(DateFormats.relative(n.createdAt, state.now), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (!n.isRead) Box(Modifier.padding(top = 6.dp).size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                    }
                }
            }
        }
    }
}
