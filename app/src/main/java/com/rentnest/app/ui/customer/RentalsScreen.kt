package com.rentnest.app.ui.customer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rentnest.app.domain.DEMO_USER_ID
import com.rentnest.app.domain.Outcome
import com.rentnest.app.domain.format.DateFormats
import com.rentnest.app.domain.format.MoneyFormatter
import com.rentnest.app.domain.message
import com.rentnest.app.domain.model.*
import com.rentnest.app.domain.repository.BookingRepository
import com.rentnest.app.domain.repository.CatalogRepository
import com.rentnest.app.domain.rules.BookingStateMachine
import com.rentnest.app.domain.rules.LateFees
import com.rentnest.app.domain.time.TimeProvider
import com.rentnest.app.ui.components.*
import com.rentnest.app.ui.model.ObserveCatalog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class RentalTab(val label: String) { REQUESTED("Requested"), UPCOMING("Upcoming"), ACTIVE("Active"), PAST("Past") }

fun rentalTabOf(status: BookingStatus) = when (status) {
    BookingStatus.REQUESTED -> RentalTab.REQUESTED
    BookingStatus.ACCEPTED -> RentalTab.UPCOMING
    BookingStatus.ACTIVE -> RentalTab.ACTIVE
    else -> RentalTab.PAST
}

data class RentalUi(val booking: Booking, val item: Item?, val providerName: String, val daysLate: Int = 0, val lateFee: Long = 0)

data class RentalsUiState(val loading: Boolean = true, val byTab: Map<RentalTab, List<RentalUi>> = emptyMap(), val message: String? = null)

@HiltViewModel
class RentalsViewModel @Inject constructor(
    catalog: CatalogRepository,
    private val bookings: BookingRepository,
    time: TimeProvider,
) : ViewModel() {
    private val message = MutableStateFlow<String?>(null)
    val state = combine(bookings.bookingsForCustomer(DEMO_USER_ID), catalog.allItems(), catalog.providers(), message) { list, items, providers, msg ->
        val itemMap = items.associateBy { it.id }
        val providerNames = providers.associate { it.id to it.shopName }
        val today = time.today()
        val rows = list.map { b ->
            val item = itemMap[b.itemId]
            val overdue = LateFees.isOverdue(b, today)
            RentalUi(
                b, item, item?.let { providerNames[it.providerId] }.orEmpty(),
                daysLate = if (overdue) LateFees.daysLate(b.endDate, today) else 0,
                lateFee = if (overdue) LateFees.fee(b.endDate, today, item?.dailyRate ?: 0) else b.lateFee,
            )
        }
        val grouped = rows.groupBy { rentalTabOf(it.booking.status) }.mapValues { (tab, v) ->
            if (tab == RentalTab.PAST) v.sortedByDescending { it.booking.endDate } else v.sortedBy { it.booking.startDate }
        }
        RentalsUiState(false, grouped, msg)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RentalsUiState())

    fun cancel(id: Long) = viewModelScope.launch {
        message.value = when (val r = bookings.cancel(id)) { is Outcome.Success -> "Booking cancelled"; is Outcome.Failure -> r.error.message() }
    }

    fun review(id: Long, rating: Int, text: String) = viewModelScope.launch {
        message.value = when (val r = bookings.submitReview(id, rating, text)) { is Outcome.Success -> "Thanks for your review!"; is Outcome.Failure -> r.error.message() }
    }

    fun messageShown() { message.value = null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RentalsScreen(onOpenItem: (Long) -> Unit, onViewBill: (Long) -> Unit, viewModel: RentalsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableStateOf<RentalTab?>(null) }
    LaunchedEffect(state.loading) {
        if (!state.loading && tab == null) tab = listOf(RentalTab.ACTIVE, RentalTab.UPCOMING, RentalTab.REQUESTED).firstOrNull { !state.byTab[it].isNullOrEmpty() } ?: RentalTab.PAST
    }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); viewModel.messageShown() } }
    var cancelId by remember { mutableStateOf<Long?>(null) }
    var reviewFor by remember { mutableStateOf<RentalUi?>(null) }
    val current = tab ?: RentalTab.ACTIVE
    Scaffold(topBar = { TopAppBar(title = { Text("My rentals") }) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.padding(padding)) {
            PrimaryTabRow(selectedTabIndex = current.ordinal) {
                RentalTab.entries.forEach { t ->
                    val count = state.byTab[t]?.size ?: 0
                    Tab(selected = t == current, onClick = { tab = t }, text = { Text(if (count > 0) "${t.label} ($count)" else t.label, maxLines = 1) })
                }
            }
            val rows = state.byTab[current].orEmpty()
            if (state.loading) SkeletonList()
            else if (rows.isEmpty()) EmptyState(Icons.Rounded.EventBusy, "Nothing here yet", "Your ${current.label.lowercase()} rentals will show up here.")
            else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(rows, key = { it.booking.id }) { r ->
                    RentalCard(r, onOpen = { onOpenItem(r.booking.itemId) }, onCancel = { cancelId = r.booking.id }, onReview = { reviewFor = r }, onViewBill = { onViewBill(r.booking.id) })
                }
            }
        }
    }
    cancelId?.let { id ->
        AlertDialog(
            onDismissRequest = { cancelId = null },
            title = { Text("Cancel this booking?") },
            text = { Text("The store will be notified. Nothing has been charged.") },
            confirmButton = { TextButton(onClick = { viewModel.cancel(id); cancelId = null }) { Text("Cancel booking") } },
            dismissButton = { TextButton(onClick = { cancelId = null }) { Text("Keep it") } },
        )
    }
    reviewFor?.let { r -> ReviewSheet(r, onDismiss = { reviewFor = null }) { rating, text -> viewModel.review(r.booking.id, rating, text); reviewFor = null } }
}

@Composable
private fun RentalCard(r: RentalUi, onOpen: () -> Unit, onCancel: () -> Unit, onReview: () -> Unit, onViewBill: () -> Unit) {
    val b = r.booking
    Card(onClick = onOpen, shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ItemArt(r.item?.photos?.firstOrNull().orEmpty(), Modifier.size(64.dp).clip(MaterialTheme.shapes.small), iconSize = 28.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.item?.title ?: "Item", style = MaterialTheme.typography.titleSmall)
                    Text(r.providerName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${DateFormats.range(b.range)} · ${MoneyFormatter.format(b.subtotal)}", style = MaterialTheme.typography.labelLarge)
                }
                StatusChip(b.status)
            }
            StatusTimeline(b.status)
            when (b.status) {
                BookingStatus.REQUESTED -> Note(Icons.Rounded.HourglassTop, "Waiting for the store to approve. You can pick it up only after approval.")
                BookingStatus.ACCEPTED -> Note(Icons.Rounded.Payments, "Approved! Pay ${MoneyFormatter.format(b.total)} at pickup on ${DateFormats.short(b.startDate)}, including the ${MoneyFormatter.format(b.deposit)} refundable advance.")
                BookingStatus.ACTIVE -> if (r.daysLate > 0) Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer, shape = MaterialTheme.shapes.small) {
                    Row(Modifier.padding(10.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Warning, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Overdue by ${DateFormats.days(r.daysLate)}. Late fee so far ${MoneyFormatter.format(r.lateFee)}, taken from your advance. Please return it today.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                } else Note(Icons.Rounded.EventAvailable, "Return by ${DateFormats.full(b.endDate)}. Late days cost ${MoneyFormatter.format(r.item?.dailyRate ?: 0)} each.")
                else -> {}
            }
            val extraCharges = b.lateFee + b.damageFee + b.dropTransportFee + b.cleaningFee
            if (b.status == BookingStatus.RETURNED && extraCharges > 0) Text(
                listOfNotNull(
                    b.lateFee.takeIf { it > 0 }?.let { "Late fee ${MoneyFormatter.format(it)}" },
                    b.damageFee.takeIf { it > 0 }?.let { "Damage fee ${MoneyFormatter.format(it)}" },
                    b.dropTransportFee.takeIf { it > 0 }?.let { "Transport fee ${MoneyFormatter.format(it)}" },
                    b.cleaningFee.takeIf { it > 0 }?.let { "Cleaning fee ${MoneyFormatter.format(it)}" },
                ).joinToString(" · ") + " deducted from your advance",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
            )
            val canCancel = BookingStateMachine.canCancel(b)
            val canReview = BookingStateMachine.canReview(b)
            if (canCancel || canReview || b.reviewed || b.status == BookingStatus.RETURNED) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(DateFormats.bookingCode(b.id), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    if (b.status == BookingStatus.RETURNED) OutlinedButton(onClick = onViewBill, shape = MaterialTheme.shapes.small) { Text("View bill") }
                    if (canCancel) OutlinedButton(onClick = onCancel, shape = MaterialTheme.shapes.small) { Text("Cancel") }
                    if (canReview) FilledTonalButton(onClick = onReview, shape = MaterialTheme.shapes.small) { Icon(Icons.Rounded.Star, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Rate rental") }
                    if (b.reviewed) Text("Reviewed ✓", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun Note(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewSheet(r: RentalUi, onDismiss: () -> Unit, onSubmit: (Int, String) -> Unit) {
    var rating by remember { mutableIntStateOf(0) }
    var text by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("How was ${r.item?.title ?: "your rental"}?", style = MaterialTheme.typography.titleLarge)
            Row {
                (1..5).forEach { i ->
                    IconButton(onClick = { rating = i }) {
                        Icon(if (i <= rating) Icons.Rounded.Star else Icons.Rounded.StarBorder, "$i star${if (i > 1) "s" else ""}", tint = Color(0xFFF5A623), modifier = Modifier.size(36.dp))
                    }
                }
            }
            OutlinedTextField(text, { text = it }, label = { Text("Tell others about it (optional)") }, minLines = 3, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth())
            PrimaryButton("Submit review", { onSubmit(rating, text) }, enabled = rating > 0, modifier = Modifier.fillMaxWidth())
        }
    }
}

// ---------- Saved ----------

@HiltViewModel
class SavedViewModel @Inject constructor(observeCatalog: ObserveCatalog, private val catalog: CatalogRepository) : ViewModel() {
    val state = observeCatalog().map { snap -> snap.items.filter { it.id in snap.favourites }.map(snap::summary) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun toggle(id: Long) = viewModelScope.launch { catalog.toggleFavourite(DEMO_USER_ID, id) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedScreen(onOpenItem: (Long) -> Unit, onExplore: () -> Unit, viewModel: SavedViewModel = hiltViewModel()) {
    val items by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text("Saved") }) }) { padding ->
        val list = items
        when {
            list == null -> SkeletonList(modifier = Modifier.padding(padding))
            list.isEmpty() -> EmptyState(Icons.Rounded.FavoriteBorder, "Nothing saved yet", "Tap the heart on any item to keep it here.", Modifier.padding(padding), "Explore gear", onExplore)
            else -> LazyVerticalGrid(
                GridCells.Adaptive(160.dp), Modifier.padding(padding), contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(list, key = { it.item.id }) { s -> ItemCard(s, { onOpenItem(s.item.id) }) { viewModel.toggle(s.item.id) } }
            }
        }
    }
}
