package com.rentnest.app.ui.provider

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
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
import com.rentnest.app.domain.DEMO_USER_ID
import com.rentnest.app.domain.format.DateFormats
import com.rentnest.app.domain.format.MoneyFormatter
import com.rentnest.app.domain.model.*
import com.rentnest.app.domain.repository.NotificationRepository
import com.rentnest.app.domain.rules.StockAlert
import com.rentnest.app.domain.rules.StockAlerts
import com.rentnest.app.domain.time.TimeProvider
import com.rentnest.app.ui.components.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class ScheduleEntry(val booking: Booking, val itemTitle: String, val customerName: String, val kind: Kind) {
    enum class Kind { OVERDUE, PICKUP, RETURN }
}

data class DashboardStats(
    val pickupsToday: Int,
    val returnsToday: Int,
    val activeRentals: Int,
    val pendingRequests: Int,
    val utilisationPercent: Int,
    val weekEarnings: Long,
    val alerts: List<StockAlert>,
    val schedule: List<ScheduleEntry>,
)

object DashboardCalculator {
    fun compute(today: LocalDate, items: List<Item>, units: List<ItemUnit>, bookings: List<Booking>, names: Map<Long, String>): DashboardStats {
        val titles = items.associate { it.id to it.title }
        val unitsByItem = units.groupBy { it.itemId }
        val bookingsByItem = bookings.groupBy { it.itemId }
        val pickups = bookings.filter { it.status == BookingStatus.ACCEPTED && !it.startDate.isAfter(today) }
        val active = bookings.filter { it.status == BookingStatus.ACTIVE }
        val returns = active.filter { !it.endDate.isAfter(today) }
        val usable = units.count { it.status == UnitStatus.AVAILABLE }
        val inUse = active.mapNotNull { it.unitId }.toSet().size
        val schedule = (returns.map { ScheduleEntry(it, titles[it.itemId].orEmpty(), names[it.customerId].orEmpty(), if (it.endDate.isBefore(today)) ScheduleEntry.Kind.OVERDUE else ScheduleEntry.Kind.RETURN) } +
            pickups.map { ScheduleEntry(it, titles[it.itemId].orEmpty(), names[it.customerId].orEmpty(), ScheduleEntry.Kind.PICKUP) })
            .sortedBy { it.kind.ordinal }
        return DashboardStats(
            pickupsToday = pickups.size,
            returnsToday = returns.size,
            activeRentals = active.size,
            pendingRequests = bookings.count { it.status == BookingStatus.REQUESTED },
            utilisationPercent = if (usable == 0) 0 else inUse * 100 / usable,
            weekEarnings = bookings.filter { it.status == BookingStatus.RETURNED && !it.endDate.isBefore(today.minusDays(6)) && !it.endDate.isAfter(today) }
                .sumOf { it.subtotal + it.damageFee },
            alerts = items.flatMap { StockAlerts.forItem(it, unitsByItem[it.id].orEmpty(), bookingsByItem[it.id].orEmpty(), today) },
            schedule = schedule,
        )
    }
}

data class DashboardUiState(val loading: Boolean = true, val ownerName: String = "", val shopName: String = "", val stats: DashboardStats? = null, val unread: Int = 0)

@HiltViewModel
class DashboardViewModel @Inject constructor(observeShop: ObserveShop, notifications: NotificationRepository, time: TimeProvider) : ViewModel() {
    val state = combine(observeShop(), notifications.unreadCount(DEMO_USER_ID, Audience.PROVIDER)) { shop, unread ->
        if (shop == null) DashboardUiState(loading = false)
        else DashboardUiState(
            false, shop.userNames[DEMO_USER_ID]?.substringBefore(' ').orEmpty(), shop.provider.shopName,
            DashboardCalculator.compute(time.today(), shop.items, shop.units, shop.bookings, shop.userNames), unread,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    onOpenNotifications: () -> Unit,
    onAddItem: () -> Unit,
    onOpenItem: (Long) -> Unit,
    onOpenBookings: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold { padding ->
        val stats = state.stats
        if (stats == null) { SkeletonList(modifier = Modifier.padding(padding)); return@Scaffold }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 24.dp, start = 20.dp, end = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        val greeting = when (LocalTime.now().hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" }
                        Text("$greeting, ${state.ownerName}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(state.shopName, style = MaterialTheme.typography.headlineSmall)
                    }
                    IconButton(onClick = onOpenNotifications) {
                        BadgedBox(badge = { if (state.unread > 0) Badge { Text("${state.unread}") } }) { Icon(Icons.Rounded.NotificationsNone, "Notifications") }
                    }
                }
            }
            item {
                Card(shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)) {
                    Row(Modifier.padding(20.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Earned in the last 7 days", style = MaterialTheme.typography.bodyMedium)
                            Text(MoneyFormatter.format(stats.weekEarnings), style = MaterialTheme.typography.headlineLarge)
                        }
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { stats.utilisationPercent / 100f }, modifier = Modifier.size(72.dp), strokeWidth = 7.dp,
                                color = MaterialTheme.colorScheme.onPrimary, trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f),
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${stats.utilisationPercent}%", style = MaterialTheme.typography.titleMedium)
                                Text("in use", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        KpiTile("Pending requests", "${stats.pendingRequests}", Icons.Rounded.MarkEmailUnread, Modifier.weight(1f), onOpenBookings)
                        KpiTile("Active rentals", "${stats.activeRentals}", Icons.Rounded.PlayCircle, Modifier.weight(1f), onOpenBookings)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        KpiTile("Pickups due", "${stats.pickupsToday}", Icons.Rounded.Outbox, Modifier.weight(1f), onOpenBookings)
                        KpiTile("Returns due", "${stats.returnsToday}", Icons.Rounded.MoveToInbox, Modifier.weight(1f), onOpenBookings)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(onClick = onAddItem, Modifier.weight(1f).heightIn(min = 52.dp), shape = MaterialTheme.shapes.small) {
                        Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("Add item")
                    }
                    FilledTonalButton(onClick = onOpenBookings, Modifier.weight(1f).heightIn(min = 52.dp), shape = MaterialTheme.shapes.small) {
                        Icon(Icons.Rounded.Inbox, null); Spacer(Modifier.width(6.dp)); Text("Requests")
                    }
                }
            }
            item { Text("Today", style = MaterialTheme.typography.titleLarge) }
            if (stats.schedule.isEmpty()) item { Text("No pickups or returns due today.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            stats.schedule.forEach { e -> item(key = "s${e.booking.id}") { ScheduleRow(e, onOpenBookings) } }
            item { Text("Needs attention", style = MaterialTheme.typography.titleLarge) }
            if (stats.alerts.isEmpty()) item { Text("All stock looks healthy.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            stats.alerts.forEach { a -> item(key = "a${a.itemId}${a::class.simpleName}") { AlertRow(a) { onOpenItem(a.itemId) } } }
        }
    }
}

@Composable
private fun ScheduleRow(e: ScheduleEntry, onClick: () -> Unit) {
    val (label, icon) = when (e.kind) {
        ScheduleEntry.Kind.OVERDUE -> "Overdue return" to Icons.Rounded.Warning
        ScheduleEntry.Kind.PICKUP -> "Pickup" to Icons.Rounded.Outbox
        ScheduleEntry.Kind.RETURN -> "Return" to Icons.Rounded.MoveToInbox
    }
    val warn = e.kind == ScheduleEntry.Kind.OVERDUE
    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainerLow).clickable(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(if (warn) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (warn) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.itemTitle, style = MaterialTheme.typography.titleSmall)
            Text("$label · ${e.customerName} · ${DateFormats.range(e.booking.range)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AlertRow(a: StockAlert, onClick: () -> Unit) {
    val (title, body, icon) = when (a) {
        is StockAlert.LowStock -> Triple(a.itemTitle, if (a.minFree == 0) "Fully booked on ${DateFormats.short(a.date)}" else "Only ${a.minFree} free on ${DateFormats.short(a.date)}", Icons.Rounded.Inventory)
        is StockAlert.Maintenance -> Triple(a.itemTitle, "Needs service: ${a.unitTags.joinToString()}", Icons.Rounded.Build)
    }
    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainerLow).clickable(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Rounded.ChevronRight, null)
    }
}
