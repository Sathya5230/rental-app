package com.rentnest.app.ui.provider

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.rentnest.app.domain.format.MoneyFormatter
import com.rentnest.app.domain.model.*
import com.rentnest.app.domain.rules.AvailabilityCalculator
import com.rentnest.app.domain.rules.StockAlert
import com.rentnest.app.domain.rules.StockAlerts
import com.rentnest.app.domain.time.TimeProvider
import com.rentnest.app.ui.components.EmptyState
import com.rentnest.app.ui.components.ItemArt
import com.rentnest.app.ui.components.SkeletonList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

enum class InventoryFilter(val label: String) { ALL("All"), LOW_STOCK("Low stock"), MAINTENANCE("Maintenance"), INACTIVE("Inactive") }

data class InventoryRow(val item: Item, val categoryName: String, val availableNow: Int, val totalUnits: Int, val lowStock: Boolean, val maintenance: Boolean) {
    fun matches(f: InventoryFilter) = when (f) {
        InventoryFilter.ALL -> true
        InventoryFilter.LOW_STOCK -> lowStock
        InventoryFilter.MAINTENANCE -> maintenance
        InventoryFilter.INACTIVE -> !item.isActive
    }
}

data class InventoryUiState(val loading: Boolean = true, val query: String = "", val filter: InventoryFilter = InventoryFilter.ALL, val rows: List<InventoryRow> = emptyList(), val counts: Map<InventoryFilter, Int> = emptyMap(), val totalUnits: Int = 0)

@HiltViewModel
class InventoryViewModel @Inject constructor(observeShop: ObserveShop, time: TimeProvider) : ViewModel() {
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(InventoryFilter.ALL)
    val state = combine(observeShop(), query, filter) { shop, q, f ->
        if (shop == null) return@combine InventoryUiState(loading = false)
        val today = time.today()
        val cats = shop.categories.associate { it.id to it.name }
        val units = shop.units.groupBy { it.itemId }
        val books = shop.bookings.groupBy { it.itemId }
        val all = shop.items.map { item ->
            val u = units[item.id].orEmpty()
            val alerts = StockAlerts.forItem(item, u, books[item.id].orEmpty(), today)
            InventoryRow(
                item, cats[item.categoryId].orEmpty(), AvailabilityCalculator.freeCountOn(today, u, books[item.id].orEmpty()),
                u.count { it.status != UnitStatus.RETIRED }, alerts.any { it is StockAlert.LowStock }, alerts.any { it is StockAlert.Maintenance },
            )
        }
        val text = q.trim().lowercase()
        InventoryUiState(
            false, q, f,
            all.filter { it.matches(f) && (text.isEmpty() || it.item.title.lowercase().contains(text) || it.categoryName.lowercase().contains(text)) },
            InventoryFilter.entries.associateWith { fl -> all.count { it.matches(fl) } },
            all.sumOf { it.totalUnits },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InventoryUiState())

    fun setQuery(q: String) { query.value = q }
    fun setFilter(f: InventoryFilter) { filter.value = f }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(onAddItem: () -> Unit, onOpenItem: (Long) -> Unit, viewModel: InventoryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { TopAppBar(title = { Column { Text("Inventory"); Text("${state.counts[InventoryFilter.ALL] ?: 0} items · ${state.totalUnits} units", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }) },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = onAddItem, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Add item") }) },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(
                state.query, viewModel::setQuery, placeholder = { Text("Search your items") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            LazyRow(contentPadding = PaddingValues(16.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(InventoryFilter.entries) { f ->
                    FilterChip(state.filter == f, { viewModel.setFilter(f) }, { Text("${f.label} (${state.counts[f] ?: 0})") })
                }
            }
            when {
                state.loading -> SkeletonList()
                state.rows.isEmpty() -> EmptyState(Icons.Rounded.Inventory2, "No items here", "Add your first item or change the filter.", actionLabel = "Add item", onAction = onAddItem)
                else -> LazyColumn(contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 88.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.rows, key = { it.item.id }) { row -> InventoryCard(row) { onOpenItem(row.item.id) } }
                }
            }
        }
    }
}

@Composable
private fun InventoryCard(row: InventoryRow, onClick: () -> Unit) {
    Card(onClick = onClick, shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ItemArt(row.item.photos.firstOrNull().orEmpty(), Modifier.size(64.dp).clip(MaterialTheme.shapes.small), iconSize = 28.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(row.item.title, style = MaterialTheme.typography.titleSmall)
                Text("${row.categoryName} · ${MoneyFormatter.format(row.item.dailyRate)}/day", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    if (!row.item.isActive) Tag("Inactive", Icons.Rounded.VisibilityOff)
                    if (row.lowStock) Tag("Low stock", Icons.Rounded.Inventory)
                    if (row.maintenance) Tag("Service", Icons.Rounded.Build)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${row.availableNow}/${row.totalUnits}", style = MaterialTheme.typography.titleLarge)
                Text("free today", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Tag(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer, shape = MaterialTheme.shapes.extraSmall) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(12.dp)); Spacer(Modifier.width(3.dp)); Text(text, style = MaterialTheme.typography.labelSmall)
        }
    }
}
