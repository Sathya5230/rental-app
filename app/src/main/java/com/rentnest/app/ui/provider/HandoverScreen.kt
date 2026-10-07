package com.rentnest.app.ui.provider

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.rentnest.app.domain.Outcome
import com.rentnest.app.domain.format.DateFormats
import com.rentnest.app.domain.format.MoneyFormatter
import com.rentnest.app.domain.message
import com.rentnest.app.domain.model.*
import com.rentnest.app.domain.repository.BookingRepository
import com.rentnest.app.domain.repository.CatalogRepository
import com.rentnest.app.domain.repository.InventoryRepository
import com.rentnest.app.domain.rules.LateFees
import com.rentnest.app.domain.rules.Settlement
import com.rentnest.app.domain.time.TimeProvider
import com.rentnest.app.ui.components.ItemArt
import com.rentnest.app.ui.components.PrimaryButton
import com.rentnest.app.ui.navigation.HandoverRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

private val PICKUP_CHECKS = listOf("Customer ID verified", "Advance collected", "All accessories included", "Condition photos taken")
private val RETURN_CHECKS = listOf("All accessories returned", "Item cleaned", "Functional test passed")

data class HandoverForm(
    val checked: Set<Int> = emptySet(), val condition: UnitCondition? = null, val damageFee: String = "0",
    /** Pickup's delivery charge, or return's collection charge — whichever this screen is for. */
    val transportFee: String = "0", val cleaningFee: String = "0",
    val notes: String = "", val submitting: Boolean = false, val error: String? = null, val done: Boolean = false,
)

data class HandoverUiState(
    val isReturn: Boolean, val booking: Booking? = null, val item: Item? = null, val unit: ItemUnit? = null, val customer: String = "",
    val form: HandoverForm = HandoverForm(), val daysLate: Int = 0, val lateFee: Long = 0,
) {
    val checks get() = if (isReturn) RETURN_CHECKS else PICKUP_CHECKS
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HandoverViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val bookings: BookingRepository,
    catalog: CatalogRepository,
    inventory: InventoryRepository,
    time: TimeProvider,
) : ViewModel() {
    private val route = savedState.toRoute<HandoverRoute>()
    private val form = MutableStateFlow(HandoverForm())

    private val details = bookings.booking(route.bookingId).flatMapLatest { b ->
        if (b == null) flowOf(Triple<Booking?, Item?, ItemUnit?>(null, null, null))
        else combine(catalog.item(b.itemId), inventory.unitsForItem(b.itemId)) { item, units -> Triple(b, item, units.firstOrNull { it.id == b.unitId }) }
    }

    val state = combine(details, catalog.users(), form) { (b, item, unit), users, f ->
        val today = time.today()
        HandoverUiState(
            route.isReturn, b, item, unit, users.firstOrNull { it.id == b?.customerId }?.name.orEmpty(), f.copy(condition = f.condition ?: unit?.condition),
            daysLate = b?.let { LateFees.daysLate(it.endDate, today) } ?: 0,
            lateFee = if (b != null && item != null) LateFees.fee(b.endDate, today, item.dailyRate) else 0,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HandoverUiState(route.isReturn))

    fun toggle(i: Int) = form.update { it.copy(checked = if (i in it.checked) it.checked - i else it.checked + i) }
    fun setCondition(c: UnitCondition) = form.update { it.copy(condition = c) }
    fun setFee(v: String) = form.update { it.copy(damageFee = v, error = null) }
    fun setTransportFee(v: String) = form.update { it.copy(transportFee = v, error = null) }
    fun setCleaningFee(v: String) = form.update { it.copy(cleaningFee = v, error = null) }
    fun setNotes(v: String) = form.update { it.copy(notes = v) }

    fun confirm() = viewModelScope.launch {
        val s = state.value
        val f = form.value
        if (f.submitting || f.done) return@launch
        val checklist = f.checked.sorted().map { s.checks[it] }
        val damage = MoneyFormatter.parseRupees(f.damageFee.ifBlank { "0" })
        val transport = MoneyFormatter.parseRupees(f.transportFee.ifBlank { "0" })
        val cleaning = MoneyFormatter.parseRupees(f.cleaningFee.ifBlank { "0" })
        if (transport == null) { form.update { it.copy(error = "Enter a valid transport charge") }; return@launch }
        if (route.isReturn && (damage == null || cleaning == null)) {
            form.update { it.copy(error = "Enter a valid damage and cleaning charge") }
            return@launch
        }
        form.update { it.copy(submitting = true, error = null) }
        val r = if (route.isReturn) bookings.processReturn(route.bookingId, checklist, s.form.condition ?: UnitCondition.GOOD, f.notes, damage ?: 0, transport, cleaning ?: 0)
        else bookings.checkOut(route.bookingId, checklist, f.notes, transport)
        form.update {
            when (r) {
                is Outcome.Success -> it.copy(submitting = false, done = true)
                is Outcome.Failure -> it.copy(submitting = false, error = r.error.message())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HandoverScreen(onBack: () -> Unit, onDone: (bookingId: Long, isReturn: Boolean) -> Unit, viewModel: HandoverViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(state.form.done) {
        val id = state.booking?.id
        if (state.form.done && id != null) { haptics.performHapticFeedback(HapticFeedbackType.Confirm); onDone(id, state.isReturn) }
    }
    val f = state.form
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isReturn) "Return & close rental" else "Check out") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            )
        },
        bottomBar = {
            Surface(shadowElevation = 12.dp, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Column(Modifier.navigationBarsPadding().padding(16.dp)) {
                    f.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp)) }
                    PrimaryButton(if (state.isReturn) "Close rental" else "Hand over item", viewModel::confirm, Modifier.fillMaxWidth(), enabled = state.booking != null, loading = f.submitting)
                }
            }
        },
    ) { padding ->
        val b = state.booking ?: return@Scaffold
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (state.isReturn && state.daysLate > 0) Card(shape = MaterialTheme.shapes.small, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(
                    "Returned ${DateFormats.days(state.daysLate)} late (due ${DateFormats.short(b.endDate)}). The late fee is taken from the advance.",
                    Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium,
                )
            }
            Card(shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    ItemArt(state.item?.photos?.firstOrNull().orEmpty(), Modifier.size(64.dp).clip(MaterialTheme.shapes.small), iconSize = 28.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(state.item?.title.orEmpty(), style = MaterialTheme.typography.titleSmall)
                        Text("${state.customer} · ${DateFormats.range(b.range)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Unit ${state.unit?.tag ?: "—"} · ${DateFormats.bookingCode(b.id)}", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Text("Checklist (${f.checked.size}/${state.checks.size})", style = MaterialTheme.typography.titleMedium)
            state.checks.forEachIndexed { i, label ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(i in f.checked, { viewModel.toggle(i) })
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (!state.isReturn) {
                Text("Charges", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    f.transportFee, viewModel::setTransportFee, label = { Text("Transport charge (pickup) ₹") }, singleLine = true, shape = MaterialTheme.shapes.small,
                    supportingText = { Text("For delivering the item, if any") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                )
                val transport = MoneyFormatter.parseRupees(f.transportFee.ifBlank { "0" }) ?: 0
                Card(shape = MaterialTheme.shapes.small, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Collected at pickup", style = MaterialTheme.typography.titleSmall)
                        SettlementLine("Rental (${DateFormats.days(b.range.days)})", MoneyFormatter.format(b.subtotal))
                        SettlementLine("Advance (refundable)", MoneyFormatter.format(b.deposit))
                        if (transport > 0) SettlementLine("Transport charge", MoneyFormatter.format(transport))
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        SettlementLine("Total due now", MoneyFormatter.format(b.subtotal + b.deposit + transport))
                    }
                }
            }
            if (state.isReturn) {
                Text("Condition after return", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UnitCondition.entries.forEach { c -> FilterChip(f.condition == c, { viewModel.setCondition(c) }, { Text(c.name.lowercase().replaceFirstChar { it.uppercase() }) }) }
                }
                if (f.condition == UnitCondition.DAMAGED) Text("The unit will be moved to maintenance automatically.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                OutlinedTextField(
                    f.damageFee, viewModel::setFee, label = { Text("Damage fee ₹") }, singleLine = true, shape = MaterialTheme.shapes.small,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        f.transportFee, viewModel::setTransportFee, label = { Text("Transport (drop) ₹") }, singleLine = true, shape = MaterialTheme.shapes.small,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        f.cleaningFee, viewModel::setCleaningFee, label = { Text("Cleaning ₹") }, singleLine = true, shape = MaterialTheme.shapes.small,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
                    )
                }
                val settlement = Settlement(
                    b.deposit, MoneyFormatter.parseRupees(f.damageFee.ifBlank { "0" }) ?: 0, state.lateFee,
                    MoneyFormatter.parseRupees(f.cleaningFee.ifBlank { "0" }) ?: 0, MoneyFormatter.parseRupees(f.transportFee.ifBlank { "0" }) ?: 0,
                )
                Card(shape = MaterialTheme.shapes.small, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Settlement", style = MaterialTheme.typography.titleSmall)
                        SettlementLine("Advance paid", MoneyFormatter.format(settlement.advance))
                        if (state.daysLate > 0) SettlementLine("Late fee (${DateFormats.days(state.daysLate)} × ${MoneyFormatter.format(state.item?.dailyRate ?: 0)})", "− ${MoneyFormatter.format(settlement.lateFee)}")
                        if (settlement.damageFee > 0) SettlementLine("Damage fee", "− ${MoneyFormatter.format(settlement.damageFee)}")
                        if (settlement.dropTransportFee > 0) SettlementLine("Transport charge (drop)", "− ${MoneyFormatter.format(settlement.dropTransportFee)}")
                        if (settlement.cleaningFee > 0) SettlementLine("Cleaning charge", "− ${MoneyFormatter.format(settlement.cleaningFee)}")
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        Text(
                            if (settlement.balance >= 0) "Refund to customer: ${MoneyFormatter.format(settlement.balance)}"
                            else "Collect from customer: ${MoneyFormatter.format(-settlement.balance)}",
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
            }
            OutlinedTextField(f.notes, viewModel::setNotes, label = { Text("Notes (optional)") }, minLines = 2, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SettlementLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
