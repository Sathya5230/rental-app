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
import com.rentnest.app.ui.components.ItemArt
import com.rentnest.app.ui.components.PrimaryButton
import com.rentnest.app.ui.navigation.HandoverRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

private val PICKUP_CHECKS = listOf("Customer ID verified", "Deposit collected", "All accessories included", "Condition photos taken")
private val RETURN_CHECKS = listOf("All accessories returned", "Item cleaned", "Functional test passed")

data class HandoverForm(val checked: Set<Int> = emptySet(), val condition: UnitCondition? = null, val damageFee: String = "0", val notes: String = "", val submitting: Boolean = false, val error: String? = null, val done: Boolean = false)

data class HandoverUiState(val isReturn: Boolean, val booking: Booking? = null, val item: Item? = null, val unit: ItemUnit? = null, val customer: String = "", val form: HandoverForm = HandoverForm()) {
    val checks get() = if (isReturn) RETURN_CHECKS else PICKUP_CHECKS
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HandoverViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val bookings: BookingRepository,
    catalog: CatalogRepository,
    inventory: InventoryRepository,
) : ViewModel() {
    private val route = savedState.toRoute<HandoverRoute>()
    private val form = MutableStateFlow(HandoverForm())

    private val details = bookings.booking(route.bookingId).flatMapLatest { b ->
        if (b == null) flowOf(Triple<Booking?, Item?, ItemUnit?>(null, null, null))
        else combine(catalog.item(b.itemId), inventory.unitsForItem(b.itemId)) { item, units -> Triple(b, item, units.firstOrNull { it.id == b.unitId }) }
    }

    val state = combine(details, catalog.users(), form) { (b, item, unit), users, f ->
        HandoverUiState(route.isReturn, b, item, unit, users.firstOrNull { it.id == b?.customerId }?.name.orEmpty(), f.copy(condition = f.condition ?: unit?.condition))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HandoverUiState(route.isReturn))

    fun toggle(i: Int) = form.update { it.copy(checked = if (i in it.checked) it.checked - i else it.checked + i) }
    fun setCondition(c: UnitCondition) = form.update { it.copy(condition = c) }
    fun setFee(v: String) = form.update { it.copy(damageFee = v, error = null) }
    fun setNotes(v: String) = form.update { it.copy(notes = v) }

    fun confirm() = viewModelScope.launch {
        val s = state.value
        val f = form.value
        if (f.submitting || f.done) return@launch
        val checklist = f.checked.sorted().map { s.checks[it] }
        val fee = MoneyFormatter.parseRupees(f.damageFee.ifBlank { "0" })
        if (route.isReturn && fee == null) { form.update { it.copy(error = "Enter a valid damage fee") }; return@launch }
        form.update { it.copy(submitting = true, error = null) }
        val r = if (route.isReturn) bookings.processReturn(route.bookingId, checklist, s.form.condition ?: UnitCondition.GOOD, f.notes, fee ?: 0)
        else bookings.checkOut(route.bookingId, checklist, f.notes)
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
fun HandoverScreen(onBack: () -> Unit, viewModel: HandoverViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(state.form.done) { if (state.form.done) { haptics.performHapticFeedback(HapticFeedbackType.Confirm); onBack() } }
    val f = state.form
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isReturn) "Process return" else "Check out") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            )
        },
        bottomBar = {
            Surface(shadowElevation = 12.dp, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Column(Modifier.navigationBarsPadding().padding(16.dp)) {
                    f.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp)) }
                    PrimaryButton(if (state.isReturn) "Complete return" else "Hand over item", viewModel::confirm, Modifier.fillMaxWidth(), enabled = state.booking != null, loading = f.submitting)
                }
            }
        },
    ) { padding ->
        val b = state.booking ?: return@Scaffold
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                val fee = MoneyFormatter.parseRupees(f.damageFee.ifBlank { "0" }) ?: 0
                val refund = b.deposit - fee
                Card(shape = MaterialTheme.shapes.small, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Text(
                        if (refund >= 0) "Refund to customer: ${MoneyFormatter.format(refund)} of ${MoneyFormatter.format(b.deposit)} deposit"
                        else "Customer owes ${MoneyFormatter.format(-refund)} beyond the deposit",
                        Modifier.padding(14.dp), style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
            OutlinedTextField(f.notes, viewModel::setNotes, label = { Text("Notes (optional)") }, minLines = 2, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth())
        }
    }
}
