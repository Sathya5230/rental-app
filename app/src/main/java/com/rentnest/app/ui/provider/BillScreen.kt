package com.rentnest.app.ui.provider

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cottage
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.rentnest.app.domain.ADMIN_USER_ID
import com.rentnest.app.domain.format.DateFormats
import com.rentnest.app.domain.format.MoneyFormatter
import com.rentnest.app.domain.model.*
import com.rentnest.app.domain.repository.BookingRepository
import com.rentnest.app.domain.repository.CatalogRepository
import com.rentnest.app.domain.repository.InventoryRepository
import com.rentnest.app.ui.components.ItemArt
import com.rentnest.app.ui.components.PrimaryButton
import com.rentnest.app.ui.navigation.BillRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class BillUiState(
    val loading: Boolean = true,
    val isReturn: Boolean = false,
    val booking: Booking? = null,
    val item: Item? = null,
    val unitTag: String? = null,
    val customer: User? = null,
    val shopName: String = "",
    val shopPhone: String = "",
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BillViewModel @Inject constructor(
    savedState: SavedStateHandle,
    bookings: BookingRepository,
    catalog: CatalogRepository,
    inventory: InventoryRepository,
) : ViewModel() {
    private val route = savedState.toRoute<BillRoute>()

    val state = bookings.booking(route.bookingId).flatMapLatest { b ->
        if (b == null) flowOf(BillUiState(loading = false, isReturn = route.isReturn))
        else combine(
            catalog.item(b.itemId), inventory.unitsForItem(b.itemId), catalog.user(b.customerId), catalog.user(ADMIN_USER_ID),
        ) { item, units, customer, admin ->
            BillUiState(
                loading = false, isReturn = route.isReturn, booking = b, item = item,
                unitTag = units.firstOrNull { it.id == b.unitId }?.tag, customer = customer,
                shopName = "RentNest Store", shopPhone = admin?.phone.orEmpty(),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BillUiState(isReturn = route.isReturn))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillScreen(onDone: () -> Unit, viewModel: BillViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isReturn) "Closing bill" else "Pickup receipt") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            )
        },
        bottomBar = {
            Surface(shadowElevation = 12.dp, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Row(Modifier.navigationBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { state.booking?.let { shareBill(context, state, it) } },
                        Modifier.weight(1f), shape = MaterialTheme.shapes.small,
                    ) { Icon(Icons.Rounded.Share, null); Spacer(Modifier.width(8.dp)); Text("Share") }
                    PrimaryButton("Done", onDone, Modifier.weight(1f))
                }
            }
        },
    ) { padding ->
        val b = state.booking
        if (b == null) {
            if (!state.loading) Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { Text("Booking not found") }
            return@Scaffold
        }
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BillCard(state, b)
        }
    }
}

@Composable
private fun BillCard(state: BillUiState, b: Booking) {
    OutlinedCard(shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Cottage, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(state.shopName, style = MaterialTheme.typography.titleMedium)
                    if (state.shopPhone.isNotBlank()) Text(state.shopPhone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(DateFormats.bookingCode(b.id), style = MaterialTheme.typography.titleSmall)
                    Text(DateFormats.full(if (state.isReturn) b.endDate else b.startDate), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider()
            Column {
                Text("To", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(state.customer?.name.orEmpty(), style = MaterialTheme.typography.titleSmall)
                Text(b.contactPhone.ifBlank { state.customer?.phone.orEmpty() }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()
            // Item table
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row {
                    TableCell("Description", Modifier.weight(2.4f), header = true)
                    TableCell("Days", Modifier.weight(0.8f), header = true)
                    TableCell("Rate", Modifier.weight(1f), header = true)
                    TableCell("Amount", Modifier.weight(1.2f), header = true)
                }
                HorizontalDivider()
                Row(verticalAlignment = Alignment.Top) {
                    Row(Modifier.weight(2.4f), verticalAlignment = Alignment.CenterVertically) {
                        ItemArt(state.item?.photos?.firstOrNull().orEmpty(), Modifier.size(32.dp).clip(MaterialTheme.shapes.extraSmall), iconSize = 14.dp)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(state.item?.title.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                            state.unitTag?.let { Text("Unit $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                    TableCell("${b.range.days}", Modifier.weight(0.8f))
                    TableCell(MoneyFormatter.format(state.item?.dailyRate ?: 0), Modifier.weight(1f))
                    TableCell(MoneyFormatter.format(b.subtotal), Modifier.weight(1.2f))
                }
            }
            HorizontalDivider()
            if (state.isReturn) ClosingCharges(b) else PickupCharges(b)
        }
    }
}

@Composable
private fun PickupCharges(b: Booking) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BillLine("Rental charge", MoneyFormatter.format(b.subtotal))
        BillLine("Advance (refundable)", MoneyFormatter.format(b.deposit))
        if (b.pickupTransportFee > 0) BillLine("Transport charge (pickup)", MoneyFormatter.format(b.pickupTransportFee))
        HorizontalDivider(Modifier.padding(vertical = 2.dp))
        BillLine("Total collected now", MoneyFormatter.format(b.subtotal + b.deposit + b.pickupTransportFee), bold = true)
    }
}

@Composable
private fun ClosingCharges(b: Booking) {
    val newCharges = b.lateFee + b.damageFee + b.cleaningFee + b.dropTransportFee
    val balance = b.deposit - newCharges
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Rental of ${MoneyFormatter.format(b.subtotal)} was collected at pickup.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (b.lateFee > 0) BillLine("Late fee", MoneyFormatter.format(b.lateFee))
        if (b.damageFee > 0) BillLine("Damage charge", MoneyFormatter.format(b.damageFee))
        if (b.dropTransportFee > 0) BillLine("Transport charge (drop)", MoneyFormatter.format(b.dropTransportFee))
        if (b.cleaningFee > 0) BillLine("Cleaning / others", MoneyFormatter.format(b.cleaningFee))
        if (newCharges == 0L) BillLine("No extra charges", "₹0")
        HorizontalDivider(Modifier.padding(vertical = 2.dp))
        BillLine("Total Amt (new charges)", MoneyFormatter.format(newCharges), bold = true)
        BillLine("Advance", MoneyFormatter.format(b.deposit))
        HorizontalDivider(Modifier.padding(vertical = 2.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (balance >= 0) "Refund to customer" else "Bal. to pay (from customer)", style = MaterialTheme.typography.titleSmall)
            Text(MoneyFormatter.format(kotlin.math.abs(balance)), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BillLine(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium)
        Text(value, style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun TableCell(text: String, modifier: Modifier = Modifier, header: Boolean = false) {
    Text(
        text, modifier,
        style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
        color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
    )
}

private fun shareBill(context: android.content.Context, state: BillUiState, b: Booking) {
    val item = state.item
    val lines = buildList {
        add(state.shopName)
        if (state.shopPhone.isNotBlank()) add(state.shopPhone)
        add("")
        add(if (state.isReturn) "CLOSING BILL" else "PICKUP RECEIPT")
        add("Bill: ${DateFormats.bookingCode(b.id)}  Date: ${DateFormats.full(b.startDate)}")
        add("To: ${state.customer?.name.orEmpty()} (${b.contactPhone.ifBlank { state.customer?.phone.orEmpty() }})")
        add("")
        add("${item?.title.orEmpty()}  x1  ${b.range.days}d @ ${MoneyFormatter.format(item?.dailyRate ?: 0)} = ${MoneyFormatter.format(b.subtotal)}")
        add("")
        if (state.isReturn) {
            if (b.lateFee > 0) add("Late fee: ${MoneyFormatter.format(b.lateFee)}")
            if (b.damageFee > 0) add("Damage charge: ${MoneyFormatter.format(b.damageFee)}")
            if (b.dropTransportFee > 0) add("Transport charge (drop): ${MoneyFormatter.format(b.dropTransportFee)}")
            if (b.cleaningFee > 0) add("Cleaning / others: ${MoneyFormatter.format(b.cleaningFee)}")
            val newCharges = b.lateFee + b.damageFee + b.cleaningFee + b.dropTransportFee
            val balance = b.deposit - newCharges
            add("Total new charges: ${MoneyFormatter.format(newCharges)}")
            add("Advance: ${MoneyFormatter.format(b.deposit)}")
            add(if (balance >= 0) "Refund to customer: ${MoneyFormatter.format(balance)}" else "Balance to pay: ${MoneyFormatter.format(-balance)}")
        } else {
            add("Advance (refundable): ${MoneyFormatter.format(b.deposit)}")
            if (b.pickupTransportFee > 0) add("Transport charge (pickup): ${MoneyFormatter.format(b.pickupTransportFee)}")
            add("Total collected now: ${MoneyFormatter.format(b.subtotal + b.deposit + b.pickupTransportFee)}")
        }
    }
    val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, lines.joinToString("\n")) }
    context.startActivity(Intent.createChooser(intent, "Share bill"))
}
