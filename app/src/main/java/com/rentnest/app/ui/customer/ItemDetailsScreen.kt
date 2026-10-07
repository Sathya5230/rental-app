package com.rentnest.app.ui.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.rentnest.app.domain.DEMO_USER_ID
import com.rentnest.app.domain.format.DateFormats
import com.rentnest.app.domain.format.MoneyFormatter
import com.rentnest.app.domain.model.*
import com.rentnest.app.domain.repository.*
import com.rentnest.app.domain.rules.AvailabilityCalculator
import com.rentnest.app.domain.time.TimeProvider
import com.rentnest.app.ui.components.*
import com.rentnest.app.ui.navigation.ItemDetailsRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class ReviewUi(val author: String, val rating: Int, val text: String, val date: String)

data class ItemDetailsUiState(
    val loading: Boolean = true,
    val item: Item? = null,
    val categoryName: String = "",
    val provider: Provider? = null,
    val rating: RatingSummary? = null,
    val reviews: List<ReviewUi> = emptyList(),
    val isFavourite: Boolean = false,
    val isOwnListing: Boolean = false,
    val usableUnits: Int = 0,
    val unavailable: Set<LocalDate> = emptySet(),
    val today: LocalDate = LocalDate.now(),
)

@HiltViewModel
class ItemDetailsViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val catalog: CatalogRepository,
    inventory: InventoryRepository,
    bookings: BookingRepository,
    session: SessionRepository,
    time: TimeProvider,
) : ViewModel() {
    val itemId = savedState.toRoute<ItemDetailsRoute>().itemId

    init { viewModelScope.launch { session.recordView(itemId) } }

    private val people = combine(catalog.providers(), catalog.users(), catalog.categories()) { p, u, c -> Triple(p, u, c) }
    private val stock = combine(inventory.unitsForItem(itemId), bookings.bookingsForItem(itemId)) { u, b -> u to b }
    private val social = combine(catalog.reviewsForItem(itemId), catalog.ratingSummaries(), catalog.favouriteIds(DEMO_USER_ID)) { r, s, f -> Triple(r, s, f) }

    val state = combine(catalog.item(itemId), people, stock, social) { item, (providers, users, categories), (units, books), (reviews, ratings, favs) ->
        if (item == null) return@combine ItemDetailsUiState(loading = false)
        val today = time.today()
        val provider = providers.firstOrNull { it.id == item.providerId }
        val names = users.associate { it.id to it.name }
        ItemDetailsUiState(
            loading = false,
            item = item,
            categoryName = categories.firstOrNull { it.id == item.categoryId }?.name.orEmpty(),
            provider = provider,
            rating = ratings[item.id],
            reviews = reviews.map { ReviewUi(names[it.customerId] ?: "Renter", it.rating, it.text, DateFormats.full(java.time.Instant.ofEpochMilli(it.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate())) },
            isFavourite = item.id in favs,
            isOwnListing = provider?.userId == DEMO_USER_ID,
            usableUnits = AvailabilityCalculator.usableUnits(units).size,
            unavailable = AvailabilityCalculator.unavailableDates(DateRange(today, today.plusDays(92)), units, books),
            today = today,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ItemDetailsUiState())

    fun toggleFavourite() = viewModelScope.launch { catalog.toggleFavourite(DEMO_USER_ID, itemId) }
}

@Composable
fun ItemDetailsScreen(onBack: () -> Unit, onSelectDates: (Long) -> Unit, viewModel: ItemDetailsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ItemDetailsContent(state, onBack, { onSelectDates(viewModel.itemId) }, viewModel::toggleFavourite, sharedKey = "art-${viewModel.itemId}")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ItemDetailsContent(state: ItemDetailsUiState, onBack: () -> Unit, onSelectDates: () -> Unit, onToggleFavourite: () -> Unit, sharedKey: String? = null) {
    val item = state.item
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = { if (item != null) BookingBar(state, onSelectDates) },
    ) { padding ->
        if (item == null) {
            if (state.loading) SkeletonList(modifier = Modifier.statusBarsPadding())
            else EmptyState(Icons.Rounded.SearchOff, "Item not found", "It may have been removed by the provider.", Modifier.statusBarsPadding(), "Go back", onBack)
            return@Scaffold
        }
        var showAllReviews by remember { mutableStateOf(false) }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 16.dp)) {
            item {
                Box {
                    val pager = rememberPagerState(pageCount = { item.photos.size.coerceAtLeast(1) })
                    HorizontalPager(pager, Modifier.fillMaxWidth().height(320.dp).sharedArt(sharedKey)) { page ->
                        ItemArt(item.photos.getOrNull(page).orEmpty(), Modifier.fillMaxSize(), iconSize = 96.dp)
                    }
                    Row(Modifier.align(Alignment.BottomCenter).padding(12.dp)) {
                        repeat(pager.pageCount) { i ->
                            Box(Modifier.padding(3.dp).size(if (pager.currentPage == i) 10.dp else 7.dp).clip(CircleShape).background(Color.White.copy(alpha = if (pager.currentPage == i) 1f else 0.6f)))
                        }
                    }
                    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp)) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)) {
                            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                        }
                        Spacer(Modifier.weight(1f))
                        FavouriteButton(state.isFavourite, onToggleFavourite, Modifier.size(48.dp))
                    }
                }
            }
            item {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Text(state.categoryName.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(item.title, style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(4.dp))
                        RatingBadge(state.rating)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PriceBox("Per day", item.dailyRate, Modifier.weight(1f))
                        PriceBox("Per week", item.weeklyRate, Modifier.weight(1f))
                        PriceBox("Deposit", item.deposit, Modifier.weight(1f))
                    }
                    state.provider?.let { p ->
                        Card(shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Avatar(p.shopName)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(p.shopName, style = MaterialTheme.typography.titleSmall)
                                    Text("★ ${p.rating} · ${p.reviewCount} reviews · ${p.locationText}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.Rounded.Verified, "Verified provider", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    Text(item.description, style = MaterialTheme.typography.bodyLarge)
                    Text("Availability", style = MaterialTheme.typography.titleLarge)
                    AvailabilityCalendar(state.unavailable, state.today)
                    if (item.specs.isNotEmpty()) {
                        Text("Specifications", style = MaterialTheme.typography.titleLarge)
                        Card(shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                item.specs.forEach { (k, v) ->
                                    Row {
                                        Text(k, Modifier.weight(0.4f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(v, Modifier.weight(0.6f), style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }
                    Text("Reviews", style = MaterialTheme.typography.titleLarge)
                    if (state.reviews.isEmpty()) Text("No reviews yet. Be the first to rent it!", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    (if (showAllReviews) state.reviews else state.reviews.take(3)).forEach { r -> ReviewRow(r) }
                    if (state.reviews.size > 3 && !showAllReviews) TextButton(onClick = { showAllReviews = true }) { Text("See all ${state.reviews.size} reviews") }
                }
            }
        }
    }
}

@Composable
private fun BookingBar(state: ItemDetailsUiState, onSelectDates: () -> Unit) {
    val item = state.item ?: return
    Surface(shadowElevation = 12.dp, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Row(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                PriceText(item.dailyRate)
                Text(
                    when {
                        state.isOwnListing -> "This is your listing"
                        state.usableUnits == 0 -> "Currently unavailable"
                        else -> "${state.usableUnits} unit${if (state.usableUnits > 1) "s" else ""} in stock"
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!state.isOwnListing) {
                PrimaryButton("Select dates", onSelectDates, enabled = state.usableUnits > 0, icon = Icons.Rounded.CalendarMonth)
            }
        }
    }
}

@Composable
private fun PriceBox(label: String, amount: Long, modifier: Modifier) {
    Column(modifier.clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(12.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(MoneyFormatter.format(amount), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ReviewRow(r: ReviewUi) {
    Row {
        Avatar(r.author, size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.author, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text("★".repeat(r.rating), color = Color(0xFFF5A623), style = MaterialTheme.typography.labelMedium)
            }
            Text(r.text, style = MaterialTheme.typography.bodyMedium)
            Text(r.date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
