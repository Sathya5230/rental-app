package com.rentnest.app.ui.provider

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rentnest.app.data.repository.OverdueReminders
import com.rentnest.app.domain.ADMIN_USER_ID
import com.rentnest.app.domain.Outcome
import com.rentnest.app.domain.format.DateFormats
import com.rentnest.app.domain.format.MoneyFormatter
import com.rentnest.app.domain.model.*
import com.rentnest.app.domain.repository.NotificationRepository
import com.rentnest.app.domain.rules.LateFees
import com.rentnest.app.domain.rules.StockAlert
import com.rentnest.app.domain.rules.StockAlerts
import com.rentnest.app.domain.time.TimeProvider
import com.rentnest.app.ui.components.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class ScheduleEntry(val booking: Booking, val itemTitle: String, val customerName: String, val kind: Kind, val lateFee: Long = 0) {
    enum class Kind { OVERDUE, PICKUP, RETURN }
}

/** A borrowed item that must go back to its vendor soon (or already should have). */
data class VendorReturn(val item: Item, val dueInDays: Long)

data class DashboardStats(
    val pickupsToday: Int,
    val returnsToday: Int,
    val activeRentals: Int,
    val pendingRequests: Int,
    val utilisationPercent: Int,
    val weekEarnings: Long,
    val alerts: List<StockAlert>,
    val schedule: List<ScheduleEntry>,
    val vendorReturns: List<VendorReturn> = emptyList(),
) {
    val overdue: List<ScheduleEntry> get() = schedule.filter { it.kind == ScheduleEntry.Kind.OVERDUE }
}

object DashboardCalculator {
    fun compute(today: LocalDate, items: List<Item>, units: List<ItemUnit>, bookings: List<Booking>, names: Map<Long, String>): DashboardStats {
        val titles = items.associate { it.id to it.title }
        val rates = items.associate { it.id to it.dailyRate }
        val unitsByItem = units.groupBy { it.itemId }
        val bookingsByItem = bookings.groupBy { it.itemId }
        val pickups = bookings.filter { it.status == BookingStatus.ACCEPTED && !it.startDate.isAfter(today) }
        val active = bookings.filter { it.status == BookingStatus.ACTIVE }
        val returns = active.filter { !it.endDate.isAfter(today) }
        val usable = units.count { it.status == UnitStatus.AVAILABLE }
        val inUse = active.mapNotNull { it.unitId }.toSet().size
        val schedule = (returns.map {
            val late = it.endDate.isBefore(today)
            ScheduleEntry(it, titles[it.itemId].orEmpty(), names[it.customerId].orEmpty(), if (late) ScheduleEntry.Kind.OVERDUE else ScheduleEntry.Kind.RETURN,
                LateFees.fee(it.endDate, today, rates[it.itemId] ?: 0))
        } +
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
            vendorReturns = items.mapNotNull { i ->
                val due = i.vendorReturnBy?.takeIf { i.ownership == Ownership.BORROWED } ?: return@mapNotNull null
                VendorReturn(i, java.time.temporal.ChronoUnit.DAYS.between(today, due)).takeIf { it.dueInDays <= VENDOR_RETURN_WARN_DAYS }
            }.sortedBy { it.dueInDays },
        )
    }

    const val VENDOR_RETURN_WARN_DAYS = 3L
}

data class DashboardUiState(
    val loading: Boolean = true,
    val ownerName: String = "",
    val shopName: String = "",
    val stats: DashboardStats? = null,
    val unread: Int = 0,
    val now: Long = 0,
    val autoSms: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    observeShop: ObserveShop,
    notifications: NotificationRepository,
    private val reminders: OverdueReminders,
    private val time: TimeProvider,
) : ViewModel() {
    private val autoSms = MutableStateFlow(reminders.canSendDirectly())
    private val message = MutableStateFlow<String?>(null)
    private val stats = observeShop().map { shop ->
        shop?.let { it to DashboardCalculator.compute(time.today(), it.items, it.units, it.bookings, it.userNames) }
    }

    val state = combine(stats, notifications.unreadCount(ADMIN_USER_ID, Audience.ADMIN), autoSms, message) { s, unread, auto, msg ->
        val (shop, computed) = s ?: return@combine DashboardUiState(loading = false)
        DashboardUiState(false, shop.userNames[ADMIN_USER_ID]?.substringBefore(' ').orEmpty(), shop.provider.shopName, computed, unread, time.nowMillis(), auto, msg)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    init {
        // Text each overdue customer once a day, as soon as the device is allowed to send SMS.
        viewModelScope.launch {
            combine(stats.map { it?.second?.overdue?.map { e -> e.booking }.orEmpty() }, autoSms) { overdue, auto -> overdue.takeIf { auto } }
                .filterNotNull()
                .collect { overdue ->
                    val sent = reminders.sendDue(overdue)
                    if (sent > 0) message.value = "Sent $sent overdue reminder SMS"
                }
        }
    }

    /** Call after the SMS permission dialog closes. */
    fun refreshSmsPermission() { autoSms.value = reminders.canSendDirectly() }

    /** Sends silently if possible; otherwise returns the SMS to hand to the Messages app. */
    fun remind(bookingId: Long, openComposer: (phone: String, text: String) -> Boolean, onlyIfDue: Boolean = false) = viewModelScope.launch {
        if (reminders.canSendDirectly()) {
            message.value = if (reminders.sendNow(bookingId, onlyIfDue)) "Reminder SMS sent" else "Couldn't send the SMS. Check the number and signal."
            return@launch
        }
        val draft = (reminders.draft(bookingId) as? Outcome.Success)?.value ?: return@launch
        if (openComposer(draft.phone, draft.message)) reminders.markSent(bookingId)
        else message.value = "No SMS app found on this device"
    }

    fun messageShown() { message.value = null }
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
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); viewModel.messageShown() } }
    var pendingSms by remember { mutableStateOf<Long?>(null) }
    val openComposer = { phone: String, text: String ->
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${phone.filter { it.isDigit() || it == '+' }}")).putExtra("sms_body", text)
        try { context.startActivity(intent); true } catch (_: ActivityNotFoundException) { false }
    }
    val smsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refreshSmsPermission()
        // Automatic sending may already have texted them now that permission is granted.
        pendingSms?.let { viewModel.remind(it, openComposer, onlyIfDue = true) }
        pendingSms = null
    }
    val remind = { bookingId: Long ->
        if (state.autoSms) viewModel.remind(bookingId, openComposer)
        else { pendingSms = bookingId; smsPermission.launch(Manifest.permission.SEND_SMS) }
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        val stats = state.stats
        if (stats == null) { SkeletonList(modifier = Modifier.padding(padding)); return@Scaffold }
        LazyColumn(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()), contentPadding = PaddingValues(bottom = 24.dp, start = 20.dp, end = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
            if (stats.overdue.isNotEmpty()) {
                item { Text("Overdue returns", style = MaterialTheme.typography.titleLarge) }
                if (!state.autoSms) item {
                    Card(shape = MaterialTheme.shapes.small, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Sms, null)
                            Spacer(Modifier.width(10.dp))
                            Text("Allow SMS to text late customers automatically every day.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            TextButton(onClick = { smsPermission.launch(Manifest.permission.SEND_SMS) }) { Text("Allow") }
                        }
                    }
                }
                stats.overdue.forEach { e -> item(key = "o${e.booking.id}") { OverdueRow(e, state.now, onOpenBookings) { remind(e.booking.id) } } }
            }
            item { Text("Today", style = MaterialTheme.typography.titleLarge) }
            val today = stats.schedule.filter { it.kind != ScheduleEntry.Kind.OVERDUE }
            if (today.isEmpty()) item { Text("No pickups or returns due today.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            today.forEach { e -> item(key = "s${e.booking.id}") { ScheduleRow(e, onOpenBookings) } }
            item { Text("Needs attention", style = MaterialTheme.typography.titleLarge) }
            if (stats.alerts.isEmpty() && stats.vendorReturns.isEmpty()) item { Text("All stock looks healthy.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            stats.vendorReturns.forEach { v -> item(key = "v${v.item.id}") { VendorReturnRow(v) { onOpenItem(v.item.id) } } }
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
private fun OverdueRow(e: ScheduleEntry, now: Long, onOpen: () -> Unit, onSms: () -> Unit) {
    val b = e.booking
    Card(onClick = onOpen, shape = MaterialTheme.shapes.small, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.itemTitle, style = MaterialTheme.typography.titleSmall)
                    Text("${e.customerName} · ${b.contactPhone}", style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(
                "Due ${DateFormats.short(b.endDate)} · late fee so far ${MoneyFormatter.format(e.lateFee)} of ${MoneyFormatter.format(b.deposit)} advance",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    b.overdueSmsAt?.let { "SMS sent ${DateFormats.relative(it, now).lowercase()}" } ?: "Not texted yet",
                    style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f),
                )
                FilledTonalButton(onClick = onSms, shape = MaterialTheme.shapes.small) {
                    Icon(Icons.Rounded.Sms, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(if (b.overdueSmsAt == null) "Send SMS" else "Send again")
                }
            }
        }
    }
}

@Composable
private fun VendorReturnRow(v: VendorReturn, onClick: () -> Unit) {
    val late = v.dueInDays < 0
    val body = when {
        late -> "Was due back to the vendor ${-v.dueInDays}d ago"
        v.dueInDays == 0L -> "Due back to the vendor today"
        else -> "Due back to the vendor in ${v.dueInDays}d"
    }
    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainerLow).clickable(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(if (late) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Handshake, null, tint = if (late) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(v.item.title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = if (late) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Rounded.ChevronRight, null)
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
