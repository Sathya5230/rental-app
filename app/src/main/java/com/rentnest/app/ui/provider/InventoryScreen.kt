package com.rentnest.app.ui.provider

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FactCheck
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rentnest.app.domain.format.DateFormats
import com.rentnest.app.domain.format.MoneyFormatter
import com.rentnest.app.domain.model.*
import com.rentnest.app.domain.repository.CatalogRepository
import com.rentnest.app.domain.repository.InventoryRepository
import com.rentnest.app.domain.rules.*
import com.rentnest.app.domain.time.TimeProvider
import com.rentnest.app.ui.components.EmptyState
import com.rentnest.app.ui.components.ItemArt
import com.rentnest.app.ui.components.SkeletonList
import com.rentnest.app.ui.components.carouselGestureExclusion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

enum class InventoryFilter(val label: String) {
    ALL("All"), OWNED("Owned"), BORROWED("Borrowed"), NEEDS_AUDIT("Needs audit"),
    LOW_STOCK("Low stock"), MAINTENANCE("Maintenance"), INACTIVE("Inactive"),
}

/** An item needs counting if it was never audited, the last count was off, or it's been a month. */
const val AUDIT_EVERY_DAYS = 30L

data class InventoryRow(
    val stock: ItemStock,
    val categoryName: String,
    val availableNow: Int,
    val lowStock: Boolean,
    val maintenance: Boolean,
    val vendorName: String?,
    val lastAudit: AuditRecord?,
    val needsAudit: Boolean,
    /** Days until the borrowed item must go back to its vendor; negative when late. */
    val vendorDueInDays: Long?,
) {
    val item: Item get() = stock.item

    fun matches(f: InventoryFilter) = when (f) {
        InventoryFilter.ALL -> true
        InventoryFilter.OWNED -> item.ownership == Ownership.OWNED
        InventoryFilter.BORROWED -> item.ownership == Ownership.BORROWED
        InventoryFilter.NEEDS_AUDIT -> needsAudit
        InventoryFilter.LOW_STOCK -> lowStock
        InventoryFilter.MAINTENANCE -> maintenance
        InventoryFilter.INACTIVE -> !item.isActive
    }
}

data class InventoryUiState(
    val loading: Boolean = true,
    val query: String = "",
    val filter: InventoryFilter = InventoryFilter.ALL,
    val rows: List<InventoryRow> = emptyList(),
    val counts: Map<InventoryFilter, Int> = emptyMap(),
    val totals: InventoryTotals? = null,
)

object InventoryRows {
    fun build(shop: ShopSnapshot, audits: List<AuditRecord>, vendors: List<Vendor>, today: LocalDate, nowMillis: Long): List<InventoryRow> {
        val cats = shop.categories.associate { it.id to it.name }
        val vendorNames = vendors.associate { it.id to it.name }
        val units = shop.units.groupBy { it.itemId }
        val books = shop.bookings.groupBy { it.itemId }
        val lastAudit = audits.groupBy { it.itemId }.mapValues { (_, v) -> v.maxBy { it.timestamp } }
        return shop.items.map { item ->
            val u = units[item.id].orEmpty()
            val b = books[item.id].orEmpty()
            val alerts = StockAlerts.forItem(item, u, b, today)
            val audit = lastAudit[item.id]
            val stale = audit == null || !audit.matches || nowMillis - audit.timestamp > AUDIT_EVERY_DAYS * 86_400_000L
            InventoryRow(
                stock = InventoryMetrics.stock(item, u, b),
                categoryName = cats[item.categoryId].orEmpty(),
                availableNow = AvailabilityCalculator.freeCountOn(today, u, b),
                lowStock = alerts.any { it is StockAlert.LowStock },
                maintenance = alerts.any { it is StockAlert.Maintenance },
                vendorName = item.vendorId?.let { vendorNames[it] },
                lastAudit = audit,
                needsAudit = stale,
                vendorDueInDays = item.vendorReturnBy?.takeIf { item.ownership == Ownership.BORROWED }?.let { ChronoUnit.DAYS.between(today, it) },
            )
        }
    }
}

@HiltViewModel
class InventoryViewModel @Inject constructor(
    observeShop: ObserveShop,
    inventory: InventoryRepository,
    catalog: CatalogRepository,
    time: TimeProvider,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(InventoryFilter.ALL)
    val state = combine(observeShop(), inventory.audits(), catalog.vendors(), query, filter) { shop, audits, vendors, q, f ->
        if (shop == null) return@combine InventoryUiState(loading = false)
        val all = InventoryRows.build(shop, audits, vendors, time.today(), time.nowMillis())
        val text = q.trim().lowercase()
        InventoryUiState(
            false, q, f,
            all.filter { r -> r.matches(f) && (text.isEmpty() || listOfNotNull(r.item.title, r.categoryName, r.vendorName).any { it.lowercase().contains(text) }) },
            InventoryFilter.entries.associateWith { fl -> all.count { it.matches(fl) } },
            InventoryMetrics.totals(all.map { it.stock }),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InventoryUiState())

    fun setQuery(q: String) { query.value = q }
    fun setFilter(f: InventoryFilter) { filter.value = f }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(onAddItem: () -> Unit, onOpenItem: (Long) -> Unit, onOpenAudit: () -> Unit, viewModel: InventoryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inventory") },
                actions = {
                    TextButton(onClick = onOpenAudit) {
                        BadgedBox(badge = { (state.counts[InventoryFilter.NEEDS_AUDIT] ?: 0).takeIf { it > 0 }?.let { Badge { Text("$it") } } }) {
                            Icon(Icons.AutoMirrored.Rounded.FactCheck, null)
                        }
                        Spacer(Modifier.width(8.dp)); Text("Audit")
                    }
                },
            )
        },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = onAddItem, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Add item") }) },
    ) { padding ->
        when {
            state.loading -> SkeletonList(modifier = Modifier.padding(padding))
            else -> LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(bottom = 88.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.totals?.let { t -> item(key = "totals") { TotalsCard(t, Modifier.padding(horizontal = 16.dp)) } }
                item(key = "search") {
                    OutlinedTextField(
                        state.query, viewModel::setQuery, placeholder = { Text("Search items, categories, vendors") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        singleLine = true, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                }
                item(key = "filters") {
                    LazyRow(Modifier.carouselGestureExclusion(48.dp), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(InventoryFilter.entries) { f ->
                            FilterChip(state.filter == f, { viewModel.setFilter(f) }, { Text("${f.label} (${state.counts[f] ?: 0})") })
                        }
                    }
                }
                if (state.rows.isEmpty()) item(key = "empty") {
                    EmptyState(Icons.Rounded.Inventory2, "No items here", "Add a product or change the filter.", actionLabel = "Add item", onAction = onAddItem)
                }
                items(state.rows, key = { it.item.id }) { row -> InventoryCard(row, Modifier.padding(horizontal = 16.dp)) { onOpenItem(row.item.id) } }
            }
        }
    }
}

@Composable
private fun TotalsCard(t: InventoryTotals, modifier: Modifier = Modifier) {
    Card(modifier, shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text("Inventory net worth", style = MaterialTheme.typography.labelLarge)
                Text(MoneyFormatter.format(t.totalWorth), style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Owned ${MoneyFormatter.format(t.ownedWorth)} · Borrowed ${MoneyFormatter.format(t.borrowedWorth)} (${t.borrowedProducts} items)",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))
            Row {
                Figure("${t.products}", "Products", Modifier.weight(1f))
                Figure("${t.units}", "Units", Modifier.weight(1f))
                Figure("${t.unitsInStore}", "In store", Modifier.weight(1f))
                Figure("${t.unitsOut}", "Rented out", Modifier.weight(1f))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))
            Text("Per-day rental", style = MaterialTheme.typography.labelLarge)
            Row {
                Figure(MoneyFormatter.format(t.dailyEarning), "Earning now", Modifier.weight(1f))
                Figure(MoneyFormatter.format(t.dailyVendorCost), "Vendor cost", Modifier.weight(1f))
                Figure(MoneyFormatter.format(t.netDaily), "Net / day", Modifier.weight(1f))
            }
            Text(
                "If every rentable unit were out: ${MoneyFormatter.format(t.dailyPotential)}/day" + if (t.unitsInService > 0) " · ${t.unitsInService} in service" else "",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun Figure(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InventoryCard(row: InventoryRow, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val s = row.stock
    Card(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            ItemArt(row.item.photos.firstOrNull().orEmpty(), Modifier.size(72.dp).clip(MaterialTheme.shapes.small), iconSize = 28.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(row.item.title, style = MaterialTheme.typography.titleSmall)
                Text("${row.categoryName} · ${MoneyFormatter.format(row.item.dailyRate)}/day", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Worth ${MoneyFormatter.format(s.worth)} · ${s.inStore} in store, ${s.out} out",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (row.vendorName != null) {
                    val due = row.vendorDueInDays?.let { d ->
                        when { d < 0 -> " · return overdue ${-d}d"; d == 0L -> " · return today"; else -> " · return in ${d}d" }
                    }.orEmpty()
                    Text(
                        "From ${row.vendorName} · ${MoneyFormatter.format(row.item.vendorCostPerDay)}/day$due",
                        style = MaterialTheme.typography.bodySmall,
                        color = if ((row.vendorDueInDays ?: 1) <= 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                    if (row.item.ownership == Ownership.BORROWED) Tag("Borrowed", Icons.Rounded.Handshake)
                    if (!row.item.isActive) Tag("Inactive", Icons.Rounded.VisibilityOff)
                    if (row.lowStock) Tag("Low stock", Icons.Rounded.Inventory)
                    if (row.maintenance) Tag("Service", Icons.Rounded.Build)
                    val audit = row.lastAudit
                    when {
                        audit == null -> Tag("Never audited", Icons.AutoMirrored.Rounded.FactCheck)
                        !audit.matches -> Tag("Audit: ${audit.counted}/${audit.expected} found", Icons.Rounded.Warning)
                        row.needsAudit -> Tag("Audit due", Icons.AutoMirrored.Rounded.FactCheck)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${row.availableNow}/${s.units}", style = MaterialTheme.typography.titleLarge)
                Text("free today", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun Tag(text: String, icon: ImageVector) {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer, shape = MaterialTheme.shapes.extraSmall) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(12.dp)); Spacer(Modifier.width(3.dp)); Text(text, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** "Today", "3d ago": for audit timestamps. */
internal fun auditAge(timestamp: Long, nowMillis: Long): String = DateFormats.relative(timestamp, nowMillis)
