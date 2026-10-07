package com.rentnest.app.ui.customer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardType
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
import com.rentnest.app.domain.format.PhoneNumbers
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

// ---------- Request ----------

data class CheckoutUiState(
    val loading: Boolean = true,
    val item: Item? = null,
    val providerName: String = "",
    val range: DateRange? = null,
    val breakdown: PriceBreakdown? = null,
    val phone: String = "",
    val processing: Boolean = false,
    val error: DomainError? = null,
    val bookedId: Long? = null,
) {
    val phoneValid: Boolean get() = PhoneNumbers.nationalDigits(phone) != null
}

private data class CheckoutLocal(val phone: String? = null, val processing: Boolean = false, val error: DomainError? = null, val bookedId: Long? = null)

@HiltViewModel
class CheckoutViewModel @Inject constructor(
    savedState: SavedStateHandle,
    catalog: CatalogRepository,
    private val bookings: BookingRepository,
) : ViewModel() {
    private val route = savedState.toRoute<CheckoutRoute>()
    private val range = DateRange(LocalDate.ofEpochDay(route.startEpochDay), LocalDate.ofEpochDay(route.endEpochDay))
    private val local = MutableStateFlow(CheckoutLocal())

    val state = combine(catalog.item(route.itemId), catalog.providers(), catalog.user(DEMO_USER_ID), local) { item, providers, user, l ->
        CheckoutUiState(
            loading = false, item = item, providerName = providers.firstOrNull { it.id == item?.providerId }?.shopName.orEmpty(),
            range = range, breakdown = item?.let { PricingEngine.quote(it, range).getOrNull() },
            // Until edited, use the number the customer signed in with.
            phone = l.phone ?: user?.phone?.let(PhoneNumbers::nationalDigits).orEmpty(),
            processing = l.processing, error = l.error, bookedId = l.bookedId,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CheckoutUiState())

    fun setPhone(v: String) = local.update { it.copy(phone = v.filter(Char::isDigit).take(10), error = null) }

    /** Idempotent: ignored while sending or once sent, so a double tap can't create two requests. */
    fun submit() {
        val current = local.value
        if (current.processing || current.bookedId != null) return
        val phone = state.value.phone
        local.value = current.copy(processing = true, error = null)
        viewModelScope.launch {
            when (val r = bookings.requestBooking(route.itemId, DEMO_USER_ID, range, phone)) {
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
    CheckoutContent(state, onBack, viewModel::setPhone, viewModel::submit)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutContent(state: CheckoutUiState, onBack: () -> Unit, onPhoneChange: (String) -> Unit, onSubmit: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Request to rent") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } })
        },
        bottomBar = {
            Surface(shadowElevation = 12.dp, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Column(Modifier.navigationBarsPadding().padding(16.dp)) {
                    AnimatedVisibility(state.error != null) {
                        Text(state.error?.message().orEmpty(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    PrimaryButton(
                        text = "Send request",
                        onClick = onSubmit,
                        enabled = state.breakdown != null && state.phoneValid,
                        loading = state.processing,
                        icon = Icons.AutoMirrored.Rounded.Send,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    ) { padding ->
        val item = state.item ?: return@Scaffold
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
            Text("Your mobile number", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = state.phone,
                onValueChange = onPhoneChange,
                prefix = { Text("+91 ") },
                singleLine = true,
                isError = state.phone.length == 10 && !state.phoneValid,
                supportingText = { Text("We'll text you here if the return is late.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )
            Card(shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("How it works", style = MaterialTheme.typography.titleSmall)
                    HowItWorks(Icons.Rounded.HourglassTop, "The store reviews your request. You can rent only after it's approved.")
                    HowItWorks(Icons.Rounded.Payments, "Pay ${state.breakdown?.let { MoneyFormatter.format(it.totalDueNow) } ?: "the total"} at pickup, including the refundable advance.")
                    HowItWorks(Icons.Rounded.EventBusy, "Return by ${state.range?.let { DateFormats.full(it.end) }.orEmpty()}. Each late day costs ${MoneyFormatter.format(item.dailyRate)}, taken from your advance.")
                }
            }
        }
    }
}

@Composable
private fun HowItWorks(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
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
            Text("Request sent to the store", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            val (booking, item) = data ?: (null to null)
            Text(
                if (booking != null && item != null) "${item.title} · ${DateFormats.range(booking.range)}\nBooking ${DateFormats.bookingCode(booking.id)}. We'll notify you as soon as the admin approves it."
                else "We'll notify you as soon as the admin approves it.",
                textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))
            PrimaryButton("View my rentals", onViewRentals, Modifier.fillMaxWidth())
            TextButton(onClick = onHome) { Text("Back to home") }
        }
    }
}
