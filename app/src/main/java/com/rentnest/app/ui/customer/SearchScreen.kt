package com.rentnest.app.ui.customer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.rentnest.app.domain.DEMO_USER_ID
import com.rentnest.app.domain.format.DateFormats
import com.rentnest.app.domain.model.*
import com.rentnest.app.domain.repository.BookingRepository
import com.rentnest.app.domain.repository.CatalogRepository
import com.rentnest.app.domain.repository.InventoryRepository
import com.rentnest.app.domain.rules.AvailabilityCalculator
import com.rentnest.app.domain.time.DateMillis
import com.rentnest.app.domain.time.TimeProvider
import com.rentnest.app.ui.components.EmptyState
import com.rentnest.app.ui.components.ItemCard
import com.rentnest.app.ui.model.CatalogSnapshot
import com.rentnest.app.ui.model.ItemSummary
import com.rentnest.app.ui.model.ObserveCatalog
import com.rentnest.app.ui.navigation.SearchRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class SortOption(val label: String) { RELEVANCE("Popular"), PRICE_LOW("Price: low to high"), PRICE_HIGH("Price: high to low"), RATING("Top rated") }

data class SearchFilters(
    val categoryId: Long? = null,
    val providerId: Long? = null,
    val maxPrice: Long? = null,
    val minRating: Double? = null,
    val dates: DateRange? = null,
) {
    val activeCount: Int get() = listOfNotNull(categoryId, providerId, maxPrice, minRating, dates).size
}

object SearchLogic {
    fun filter(snapshot: CatalogSnapshot, query: String, f: SearchFilters, sort: SortOption, units: Map<Long, List<ItemUnit>>, bookings: Map<Long, List<Booking>>): List<ItemSummary> {
        val q = query.trim().lowercase()
        val matched = snapshot.items.map(snapshot::summary).filter { s ->
            val item = s.item
            (q.isEmpty() || listOf(item.title, item.description, s.categoryName, s.providerName).any { it.lowercase().contains(q) }) &&
                (f.categoryId == null || item.categoryId == f.categoryId) &&
                (f.providerId == null || item.providerId == f.providerId) &&
                (f.maxPrice == null || item.dailyRate <= f.maxPrice) &&
                (f.minRating == null || (s.rating?.average ?: 0.0) >= f.minRating) &&
                (f.dates == null || AvailabilityCalculator.isBookable(f.dates, units[item.id].orEmpty(), bookings[item.id].orEmpty()))
        }
        return when (sort) {
            SortOption.RELEVANCE -> matched.sortedWith(
                compareByDescending<ItemSummary> { q.isNotEmpty() && it.item.title.lowercase().contains(q) }.thenByDescending { snapshot.bookingCounts[it.item.id] ?: 0 },
            )
            SortOption.PRICE_LOW -> matched.sortedBy { it.item.dailyRate }
            SortOption.PRICE_HIGH -> matched.sortedByDescending { it.item.dailyRate }
            SortOption.RATING -> matched.sortedByDescending { it.rating?.average ?: 0.0 }
        }
    }
}

data class SearchUiState(
    val loading: Boolean = true,
    val query: String = "",
    val filters: SearchFilters = SearchFilters(),
    val sort: SortOption = SortOption.RELEVANCE,
    val categories: List<Category> = emptyList(),
    val providerName: String? = null,
    val results: List<ItemSummary> = emptyList(),
    val today: LocalDate = LocalDate.now(),
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    savedState: SavedStateHandle,
    observeCatalog: ObserveCatalog,
    inventory: InventoryRepository,
    bookings: BookingRepository,
    private val catalog: CatalogRepository,
    time: TimeProvider,
) : ViewModel() {
    private val route = savedState.toRoute<SearchRoute>()
    private val initialCategory = route.categoryId.takeIf { it > 0 }
    private val query = MutableStateFlow("")
    private val filters = MutableStateFlow(SearchFilters(categoryId = initialCategory, providerId = route.providerId.takeIf { it > 0 }))
    private val sort = MutableStateFlow(SortOption.RELEVANCE)
    private val stock = combine(inventory.allUnits(), bookings.allBookings()) { u, b -> u.groupBy { it.itemId } to b.groupBy { it.itemId } }

    val state = combine(query, filters, sort, observeCatalog(), stock) { q, f, s, snap, (units, books) ->
        SearchUiState(false, q, f, s, snap.categories, f.providerId?.let { snap.providers[it]?.shopName }, SearchLogic.filter(snap, q, f, s, units, books), time.today())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun setQuery(q: String) { query.value = q }
    fun setFilters(f: SearchFilters) { filters.value = f }
    fun setSort(s: SortOption) { sort.value = s }
    fun toggleFavourite(id: Long) = viewModelScope.launch { catalog.toggleFavourite(DEMO_USER_ID, id) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(onOpenItem: (Long) -> Unit, viewModel: SearchViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showFilters by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text("Search gear, providers…") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (state.query.isNotEmpty()) IconButton(onClick = { viewModel.setQuery("") }) { Icon(Icons.Rounded.Close, "Clear search") } },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = state.filters.activeCount > 0,
                    onClick = { showFilters = true },
                    label = { Text(if (state.filters.activeCount > 0) "Filters · ${state.filters.activeCount}" else "Filters") },
                    leadingIcon = { Icon(Icons.Rounded.Tune, null, Modifier.size(18.dp)) },
                )
                Spacer(Modifier.width(8.dp))
                Box {
                    AssistChip(onClick = { sortMenu = true }, label = { Text(state.sort.label) }, leadingIcon = { Icon(Icons.Rounded.SwapVert, null, Modifier.size(18.dp)) })
                    DropdownMenu(sortMenu, onDismissRequest = { sortMenu = false }) {
                        SortOption.entries.forEach { o -> DropdownMenuItem(text = { Text(o.label) }, onClick = { viewModel.setSort(o); sortMenu = false }) }
                    }
                }
                Spacer(Modifier.weight(1f))
                Text("${state.results.size} results", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.providerName?.let { name ->
                InputChip(
                    selected = true,
                    onClick = { viewModel.setFilters(state.filters.copy(providerId = null)) },
                    label = { Text(name) },
                    leadingIcon = { Icon(Icons.Rounded.Storefront, null, Modifier.size(18.dp)) },
                    trailingIcon = { Icon(Icons.Rounded.Close, "Show all providers", Modifier.size(18.dp)) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            if (!state.loading && state.results.isEmpty()) {
                EmptyState(Icons.Rounded.SearchOff, "No gear matches", "Try a different word or loosen your filters.", actionLabel = "Clear filters", onAction = {
                    viewModel.setFilters(SearchFilters()); viewModel.setQuery("")
                })
            } else {
                LazyVerticalGrid(
                    GridCells.Adaptive(160.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.results, key = { it.item.id }) { s ->
                        ItemCard(s, { onOpenItem(s.item.id) }, sharedKey = "art-${s.item.id}") { viewModel.toggleFavourite(s.item.id) }
                    }
                }
            }
        }
    }
    if (showFilters) FilterSheet(state, onDismiss = { showFilters = false }) { viewModel.setFilters(it); showFilters = false }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(state: SearchUiState, onDismiss: () -> Unit, onApply: (SearchFilters) -> Unit) {
    var draft by remember { mutableStateOf(state.filters) }
    val providerId = state.filters.providerId
    var pickDates by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Filters", style = MaterialTheme.typography.titleLarge)
            Text("Category", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(draft.categoryId == null, { draft = draft.copy(categoryId = null) }, { Text("All") })
                state.categories.forEach { c -> FilterChip(draft.categoryId == c.id, { draft = draft.copy(categoryId = c.id) }, { Text(c.name) }) }
            }
            val max = (draft.maxPrice ?: 250_000L) / 100f
            Text("Max price per day: ${if (draft.maxPrice == null) "Any" else "₹${max.toInt()}"}", style = MaterialTheme.typography.titleSmall)
            Slider(value = max, onValueChange = { draft = draft.copy(maxPrice = if (it >= 2_500f) null else (it.toLong() / 50 * 50) * 100) }, valueRange = 100f..2_500f)
            Text("Rating", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf<Double?>(null, 4.0, 4.5).forEach { r -> FilterChip(draft.minRating == r, { draft = draft.copy(minRating = r) }, { Text(r?.let { "$it+" } ?: "Any") }) }
            }
            Text("Available on", style = MaterialTheme.typography.titleSmall)
            OutlinedButton(onClick = { pickDates = true }, shape = MaterialTheme.shapes.small) {
                Icon(Icons.Rounded.CalendarMonth, null); Spacer(Modifier.width(8.dp))
                Text(draft.dates?.let { DateFormats.range(it) } ?: "Any dates")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { draft = SearchFilters(providerId = providerId) }, Modifier.weight(1f), shape = MaterialTheme.shapes.small) { Text("Reset") }
                Button(onClick = { onApply(draft) }, Modifier.weight(1f), shape = MaterialTheme.shapes.small) { Text("Show results") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (pickDates) {
        val today = state.today
        val picker = rememberDateRangePickerState(selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = !DateMillis.toLocalDate(utcTimeMillis).isBefore(today)
        })
        DatePickerDialog(
            onDismissRequest = { pickDates = false },
            confirmButton = {
                TextButton(enabled = picker.selectedStartDateMillis != null, onClick = {
                    val s = DateMillis.toLocalDate(picker.selectedStartDateMillis!!)
                    val e = picker.selectedEndDateMillis?.let(DateMillis::toLocalDate) ?: s
                    draft = draft.copy(dates = DateRange(s, e)); pickDates = false
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { pickDates = false }) { Text("Cancel") } },
        ) { DateRangePicker(picker, Modifier.weight(1f), title = null, showModeToggle = false) }
    }
}
