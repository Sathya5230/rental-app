package com.rentnest.app.ui.provider

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rentnest.app.domain.Outcome
import com.rentnest.app.domain.format.DateFormats
import com.rentnest.app.domain.format.MoneyFormatter
import com.rentnest.app.domain.message
import com.rentnest.app.domain.model.*
import com.rentnest.app.domain.repository.BookingRepository
import com.rentnest.app.domain.rules.AvailabilityCalculator
import com.rentnest.app.domain.time.TimeProvider
import com.rentnest.app.ui.components.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ProviderTab(val label: String) { REQUESTS("Requests"), UPCOMING("Upcoming"), ACTIVE("Active"), COMPLETED("Done") }

fun providerTabOf(s: BookingStatus) = when (s) {
    BookingStatus.REQUESTED -> ProviderTab.REQUESTS
    BookingStatus.ACCEPTED -> ProviderTab.UPCOMING
    BookingStatus.ACTIVE -> ProviderTab.ACTIVE
    else -> ProviderTab.COMPLETED
}

data class ProviderBookingUi(val booking: Booking, val item: Item?, val customerName: String, val freeUnits: List<ItemUnit>, val unitTag: String?, val isOverdue: Boolean)

data class ProviderBookingsUiState(val loading: Boolean = true, val byTab: Map<ProviderTab, List<ProviderBookingUi>> = emptyMap(), val message: String? = null)

@HiltViewModel
class ProviderBookingsViewModel @Inject constructor(
    observeShop: ObserveShop,
    private val bookings: BookingRepository,
    time: TimeProvider,
) : ViewModel() {
    private val message = MutableStateFlow<String?>(null)
    val state = combine(observeShop(), message) { shop, msg ->
        if (shop == null) return@combine ProviderBookingsUiState(loading = false)
        val today = time.today()
        val items = shop.items.associateBy { it.id }
        val unitsByItem = shop.units.groupBy { it.itemId }
        val tags = shop.units.associate { it.id to it.tag }
        val rows = shop.bookings.map { b ->
            val free = if (b.status == BookingStatus.REQUESTED) {
                AvailabilityCalculator.freeUnitsFor(b.range, unitsByItem[b.itemId].orEmpty(), shop.bookings.filter { it.itemId == b.itemId && it.id != b.id })
            } else emptyList()
            ProviderBookingUi(b, items[b.itemId], shop.userNames[b.customerId] ?: "Customer", free, b.unitId?.let { tags[it] }, b.status == BookingStatus.ACTIVE && b.endDate.isBefore(today))
        }
        val grouped = rows.groupBy { providerTabOf(it.booking.status) }.mapValues { (t, v) ->
            if (t == ProviderTab.COMPLETED) v.sortedByDescending { it.booking.endDate } else v.sortedBy { it.booking.startDate }
        }
        ProviderBookingsUiState(false, grouped, msg)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProviderBookingsUiState())

    fun accept(bookingId: Long, unitId: Long) = viewModelScope.launch {
        message.value = when (val r = bookings.accept(bookingId, unitId)) { is Outcome.Success -> "Booking confirmed. The customer has been notified."; is Outcome.Failure -> r.error.message() }
    }
    fun decline(bookingId: Long) = viewModelScope.launch {
        message.value = when (val r = bookings.decline(bookingId)) { is Outcome.Success -> "Request declined"; is Outcome.Failure -> r.error.message() }
    }
    fun messageShown() { message.value = null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderBookingsScreen(onHandover: (Long, Boolean) -> Unit, viewModel: ProviderBookingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); viewModel.messageShown() } }
    var tab by rememberSaveable { mutableStateOf(ProviderTab.REQUESTS) }
    Scaffold(topBar = { TopAppBar(title = { Text("Bookings") }) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.padding(padding)) {
            PrimaryTabRow(selectedTabIndex = tab.ordinal) {
                ProviderTab.entries.forEach { t ->
                    val n = state.byTab[t]?.size ?: 0
                    Tab(t == tab, { tab = t }, text = { Text(if (n > 0) "${t.label} ($n)" else t.label, maxLines = 1) })
                }
            }
            val rows = state.byTab[tab].orEmpty()
            when {
                state.loading -> SkeletonList()
                rows.isEmpty() -> EmptyState(Icons.Rounded.Inbox, "Nothing here", when (tab) {
                    ProviderTab.REQUESTS -> "New booking requests from customers appear here."
                    ProviderTab.UPCOMING -> "Confirmed bookings waiting for pickup."
                    ProviderTab.ACTIVE -> "Items currently out with customers."
                    ProviderTab.COMPLETED -> "Finished, declined and cancelled bookings."
                })
                else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(rows, key = { it.booking.id }) { r ->
                        when (tab) {
                            ProviderTab.REQUESTS -> RequestCard(r, onAccept = { viewModel.accept(r.booking.id, it) }, onDecline = { viewModel.decline(r.booking.id) })
                            ProviderTab.UPCOMING -> BookingCard(r) { Button({ onHandover(r.booking.id, false) }, shape = MaterialTheme.shapes.small) { Icon(Icons.Rounded.Outbox, null); Spacer(Modifier.width(6.dp)); Text("Check out") } }
                            ProviderTab.ACTIVE -> BookingCard(r) { Button({ onHandover(r.booking.id, true) }, shape = MaterialTheme.shapes.small) { Icon(Icons.Rounded.MoveToInbox, null); Spacer(Modifier.width(6.dp)); Text("Process return") } }
                            ProviderTab.COMPLETED -> BookingCard(r) {}
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookingHeader(r: ProviderBookingUi) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ItemArt(r.item?.photos?.firstOrNull().orEmpty(), Modifier.size(56.dp).clip(MaterialTheme.shapes.small), iconSize = 24.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(r.item?.title ?: "Item", style = MaterialTheme.typography.titleSmall)
            Text("${r.customerName} · ${DateFormats.range(r.booking.range)} (${DateFormats.days(r.booking.range.days)})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${MoneyFormatter.format(r.booking.subtotal)} + ${MoneyFormatter.format(r.booking.deposit)} deposit", style = MaterialTheme.typography.labelLarge)
        }
        if (r.isOverdue) StatusChipOverdue() else StatusChip(r.booking.status)
    }
}

@Composable
private fun StatusChipOverdue() {
    Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer, shape = MaterialTheme.shapes.extraLarge) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Warning, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Overdue", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun BookingCard(r: ProviderBookingUi, action: @Composable RowScope.() -> Unit) {
    Card(shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BookingHeader(r)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    listOfNotNull(DateFormats.bookingCode(r.booking.id), r.unitTag?.let { "Unit $it" }, r.booking.damageFee.takeIf { it > 0 }?.let { "Damage ${MoneyFormatter.format(it)}" }).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
                )
                action()
            }
        }
    }
}

/** Request card with accept (unit picker) / decline (confirm). */
@Composable
fun RequestCard(r: ProviderBookingUi, onAccept: (Long) -> Unit, onDecline: () -> Unit) {
    var picking by remember { mutableStateOf(false) }
    var confirmDecline by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Card(shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BookingHeader(r)
            if (r.freeUnits.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("No unit is free for these dates", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton({ confirmDecline = true }, Modifier.weight(1f), shape = MaterialTheme.shapes.small) { Text("Decline") }
                Button({ picking = true }, Modifier.weight(1f), enabled = r.freeUnits.isNotEmpty(), shape = MaterialTheme.shapes.small) { Text("Accept") }
            }
        }
    }
    if (picking) {
        var chosen by remember { mutableStateOf(r.freeUnits.firstOrNull()?.id) }
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("Assign a unit") },
            text = {
                Column {
                    Text("Pick which ${r.item?.title ?: "unit"} goes to ${r.customerName}.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    r.freeUnits.forEach { u ->
                        Row(
                            Modifier.fillMaxWidth().selectable(chosen == u.id, role = Role.RadioButton) { chosen = u.id }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(chosen == u.id, null)
                            Spacer(Modifier.width(8.dp))
                            Text(u.tag, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text(u.condition.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = chosen != null, onClick = {
                    picking = false
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    chosen?.let(onAccept)
                }) { Text("Confirm") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        )
    }
    if (confirmDecline) {
        AlertDialog(
            onDismissRequest = { confirmDecline = false },
            title = { Text("Decline this request?") },
            text = { Text("${r.customerName} will be told the item isn't available.") },
            confirmButton = { TextButton(onClick = { confirmDecline = false; onDecline() }) { Text("Decline") } },
            dismissButton = { TextButton(onClick = { confirmDecline = false }) { Text("Keep") } },
        )
    }
}
