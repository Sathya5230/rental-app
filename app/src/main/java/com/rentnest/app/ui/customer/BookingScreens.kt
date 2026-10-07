package com.rentnest.app.ui.customer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.rentnest.app.domain.DEMO_USER_ID
import com.rentnest.app.domain.DomainError
import com.rentnest.app.domain.Outcome
import com.rentnest.app.domain.format.DateFormats
import com.rentnest.app.domain.format.MoneyFormatter
import com.rentnest.app.domain.getOrNull
import com.rentnest.app.domain.message
import com.rentnest.app.domain.model.*
import com.rentnest.app.domain.repository.*
import com.rentnest.app.domain.rules.AvailabilityCalculator
import com.rentnest.app.domain.rules.PriceBreakdown
import com.rentnest.app.domain.rules.PricingEngine
import com.rentnest.app.domain.time.DateMillis
import com.rentnest.app.domain.time.TimeProvider
import com.rentnest.app.ui.components.*
import com.rentnest.app.ui.navigation.BookingDatesRoute
import com.rentnest.app.ui.navigation.BookingSuccessRoute
import com.rentnest.app.ui.navigation.CheckoutRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

// ---------- Date selection ----------

data class BookingDatesUiState(
    val item: Item? = null,
    val unavailableDays: Set<Long> = emptySet(),
    val today: LocalDate = LocalDate.now(),
    val range: DateRange? = null,
    val breakdown: PriceBreakdown? = null,
    val error: DomainError? = null,
)

@HiltViewModel
class BookingDatesViewModel @Inject constructor(
    savedState: SavedStateHandle,
    catalog: CatalogRepository,
    inventory: InventoryRepository,
    bookings: BookingRepository,
    private val time: TimeProvider,
) : ViewModel() {
    val itemId = savedState.toRoute<BookingDatesRoute>().itemId
    private val selection = MutableStateFlow<Pair<LocalDate?, LocalDate?>>(null to null)

    val state = combine(catalog.item(itemId), inventory.unitsForItem(itemId), bookings.bookingsForItem(itemId), selection) { item, units, books, (start, end) ->
        val today = time.today()
        val unavailable = AvailabilityCalculator.unavailableDates(DateRange(today, today.plusDays(365)), units, books).map { it.toEpochDay() }.toSet()
        val range = if (start != null) DateRange(start, end ?: start) else null
        val bookable = range != null && AvailabilityCalculator.isBookable(range, units, books)
        BookingDatesUiState(
            item = item, unavailableDays = unavailable, today = today, range = range,
            breakdown = if (item != null && range != null && bookable) PricingEngine.quote(item, range).getOrNull() else null,
            error = if (range != null && !bookable) DomainError.DatesUnavailable else null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BookingDatesUiState())

    fun select(start: LocalDate?, end: LocalDate?) { selection.value = start to end }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingDatesScreen(onBack: () -> Unit, onContinue: (Long, Long, Long) -> Unit, viewModel: BookingDatesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val unavailable = rememberUpdatedState(state.unavailableDays)
    val today = state.today
    val selectable = remember(today) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val d = DateMillis.toLocalDate(utcTimeMillis)
                return !d.isBefore(today) && d.toEpochDay() !in unavailable.value
            }
            override fun isSelectableYear(year: Int) = year >= today.year
        }
    }
    val picker = rememberDateRangePickerState(selectableDates = selectable)
    LaunchedEffect(picker.selectedStartDateMillis, picker.selectedEndDateMillis) {
        viewModel.select(picker.selectedStartDateMillis?.let(DateMillis::toLocalDate), picker.selectedEndDateMillis?.let(DateMillis::toLocalDate))
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Choose dates") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            )
        },
        bottomBar = {
            Surface(shadowElevation = 12.dp, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Column(Modifier.navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val item = state.item
                    when {
                        state.error != null -> Text(state.error!!.message(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        state.breakdown != null && item != null -> PriceBreakdownCard(state.breakdown!!, item.dailyRate, item.weeklyRate)
                        state.range == null -> Text("Tap a start date, then an end date. Tap the same day twice for a one-day rental.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    PrimaryButton(
                        "Continue",
                        onClick = { state.range?.let { onContinue(viewModel.itemId, it.start.toEpochDay(), it.end.toEpochDay()) } },
                        enabled = state.breakdown != null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    ) { padding ->
        DateRangePicker(picker, Modifier.padding(padding).fillMaxSize(), title = null, showModeToggle = false)
    }
}

// ---------- Checkout ----------

enum class PaymentMethod(val title: String, val subtitle: String) {
    UPI("UPI", "arjun@okrentnest"),
    CARD("Credit / debit card", "Visa •••• 4242"),
    WALLET("RentNest Wallet", "Balance ₹25,000"),
}

data class CheckoutUiState(
    val loading: Boolean = true,
    val item: Item? = null,
    val providerName: String = "",
    val range: DateRange? = null,
    val breakdown: PriceBreakdown? = null,
    val method: PaymentMethod = PaymentMethod.UPI,
    val processing: Boolean = false,
    val error: DomainError? = null,
    val bookedId: Long? = null,
)

private data class CheckoutLocal(val method: PaymentMethod = PaymentMethod.UPI, val processing: Boolean = false, val error: DomainError? = null, val bookedId: Long? = null)

@HiltViewModel
class CheckoutViewModel @Inject constructor(
    savedState: SavedStateHandle,
    catalog: CatalogRepository,
    private val bookings: BookingRepository,
) : ViewModel() {
    private val route = savedState.toRoute<CheckoutRoute>()
    private val range = DateRange(LocalDate.ofEpochDay(route.startEpochDay), LocalDate.ofEpochDay(route.endEpochDay))
    private val local = MutableStateFlow(CheckoutLocal())

    val state = combine(catalog.item(route.itemId), catalog.providers(), local) { item, providers, l ->
        CheckoutUiState(
            loading = false, item = item, providerName = providers.firstOrNull { it.id == item?.providerId }?.shopName.orEmpty(),
            range = range, breakdown = item?.let { PricingEngine.quote(it, range).getOrNull() },
            method = l.method, processing = l.processing, error = l.error, bookedId = l.bookedId,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CheckoutUiState())

    fun selectMethod(m: PaymentMethod) = local.update { it.copy(method = m) }

    /** Idempotent: ignored while processing or once booked, so a double tap can't create two bookings. */
    fun pay() {
        val current = local.value
        if (current.processing || current.bookedId != null) return
        local.value = current.copy(processing = true, error = null)
        viewModelScope.launch {
            delay(1_500) // simulated payment gateway
            when (val r = bookings.requestBooking(route.itemId, DEMO_USER_ID, range)) {
                is Outcome.Success -> local.update { it.copy(processing = false, bookedId = r.value.id) }
                is Outcome.Failure -> local.update { it.copy(processing = false, error = r.error) }
            }
        }
    }
}

@Composable
fun CheckoutScreen(onBack: () -> Unit, onBooked: (Long) -> Unit, viewModel: CheckoutViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(state.bookedId) {
        state.bookedId?.let { haptics.performHapticFeedback(HapticFeedbackType.Confirm); onBooked(it) }
    }
    CheckoutContent(state, onBack, viewModel::selectMethod, viewModel::pay)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutContent(state: CheckoutUiState, onBack: () -> Unit, onSelectMethod: (PaymentMethod) -> Unit, onPay: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Checkout") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } })
        },
        bottomBar = {
            Surface(shadowElevation = 12.dp, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Column(Modifier.navigationBarsPadding().padding(16.dp)) {
                    AnimatedVisibility(state.error != null) {
                        Text(state.error?.message().orEmpty(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    PrimaryButton(
                        text = "Pay ${state.breakdown?.let { MoneyFormatter.format(it.totalDueNow) } ?: ""}",
                        onClick = onPay,
                        enabled = state.breakdown != null,
                        loading = state.processing,
                        icon = Icons.Rounded.Lock,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    ) { padding ->
        val item = state.item ?: return@Scaffold
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    ItemArt(item.photos.firstOrNull().orEmpty(), Modifier.size(72.dp).clip(MaterialTheme.shapes.small), iconSize = 32.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(item.title, style = MaterialTheme.typography.titleSmall)
                        Text(state.providerName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        state.range?.let { Text("${DateFormats.range(it)} · ${DateFormats.days(it.days)}", style = MaterialTheme.typography.labelLarge) }
                    }
                }
            }
            state.breakdown?.let { PriceBreakdownCard(it, item.dailyRate, item.weeklyRate) }
            Text("Payment method", style = MaterialTheme.typography.titleMedium)
            PaymentMethod.entries.forEach { m ->
                val selected = m == state.method
                OutlinedCard(
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.selectable(selected, role = Role.RadioButton) { onSelectMethod(m) },
                ) {
                    Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(when (m) { PaymentMethod.UPI -> Icons.Rounded.QrCode2; PaymentMethod.CARD -> Icons.Rounded.CreditCard; PaymentMethod.WALLET -> Icons.Rounded.AccountBalanceWallet }, null)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.title, style = MaterialTheme.typography.titleSmall)
                            Text(m.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        RadioButton(selected = selected, onClick = null)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Info, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text("Demo mode: no real payment is made. The provider confirms your request.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ---------- Success ----------

@HiltViewModel
class BookingSuccessViewModel @Inject constructor(savedState: SavedStateHandle, bookings: BookingRepository, catalog: CatalogRepository) : ViewModel() {
    private val id = savedState.toRoute<BookingSuccessRoute>().bookingId
    val state = bookings.booking(id).flatMapLatest { b ->
        if (b == null) flowOf(null) else catalog.item(b.itemId).map { b to it }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun BookingSuccessScreen(onViewRentals: () -> Unit, onHome: () -> Unit, viewModel: BookingSuccessViewModel = hiltViewModel()) {
    val data by viewModel.state.collectAsStateWithLifecycle()
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            SuccessAnimation()
            Text("Request sent!", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            val (booking, item) = data ?: (null to null)
            Text(
                if (booking != null && item != null) "${item.title} · ${DateFormats.range(booking.range)}\nBooking ${DateFormats.bookingCode(booking.id)}. The provider usually confirms within an hour."
                else "The provider usually confirms within an hour.",
                textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))
            PrimaryButton("View my rentals", onViewRentals, Modifier.fillMaxWidth())
            TextButton(onClick = onHome) { Text("Back to home") }
        }
    }
}
