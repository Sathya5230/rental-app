package com.rentnest.app.ui.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rentnest.app.domain.DEMO_USER_ID
import com.rentnest.app.domain.model.*
import com.rentnest.app.domain.repository.CatalogRepository
import com.rentnest.app.domain.repository.NotificationRepository
import com.rentnest.app.domain.repository.SessionRepository
import com.rentnest.app.ui.components.*
import com.rentnest.app.ui.model.ItemSummary
import com.rentnest.app.ui.model.ObserveCatalog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

data class HomeUiState(
    val loading: Boolean = true,
    val userName: String = "",
    val categories: List<Category> = emptyList(),
    val popular: List<ItemSummary> = emptyList(),
    val topProviders: List<Provider> = emptyList(),
    val recent: List<ItemSummary> = emptyList(),
    val unread: Int = 0,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    observeCatalog: ObserveCatalog,
    private val catalog: CatalogRepository,
    session: SessionRepository,
    notifications: NotificationRepository,
) : ViewModel() {
    val state = combine(
        observeCatalog(), catalog.user(DEMO_USER_ID), session.session, notifications.unreadCount(DEMO_USER_ID, Audience.CUSTOMER),
    ) { snap, user, s, unread ->
        val byId = snap.items.associateBy { it.id }
        HomeUiState(
            loading = false,
            userName = user?.name?.substringBefore(' ').orEmpty(),
            categories = snap.categories,
            popular = snap.items.sortedWith(compareByDescending<Item> { snap.bookingCounts[it.id] ?: 0 }.thenByDescending { snap.ratings[it.id]?.average ?: 0.0 })
                .take(8).map(snap::summary),
            topProviders = snap.providers.values.sortedByDescending { it.rating }.take(6),
            // Skip ids that no longer exist (e.g. after a demo reset)
            recent = s.recentItemIds.mapNotNull { byId[it] }.take(6).map(snap::summary),
            unread = unread,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun toggleFavourite(itemId: Long) = viewModelScope.launch { catalog.toggleFavourite(DEMO_USER_ID, itemId) }
}

@Composable
fun HomeScreen(
    onOpenItem: (Long) -> Unit,
    onOpenSearch: (Long?) -> Unit,
    onOpenNotifications: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold { padding ->
        if (state.loading) { SkeletonList(modifier = Modifier.padding(padding)); return@Scaffold }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item {
                Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        val greeting = when (LocalTime.now().hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" }
                        Text("$greeting, ${state.userName}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("What will you rent today?", style = MaterialTheme.typography.headlineSmall)
                    }
                    IconButton(onClick = onOpenNotifications) {
                        BadgedBox(badge = { if (state.unread > 0) Badge { Text("${state.unread}") } }) {
                            Icon(Icons.Rounded.NotificationsNone, "Notifications")
                        }
                    }
                }
            }
            item {
                Surface(
                    onClick = { onOpenSearch(null) },
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(54.dp),
                ) {
                    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Text("Search cameras, tents, drills…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { PromoBanner { onOpenSearch(3L) } }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.categories, key = { it.id }) { c -> CategoryTile(c) { onOpenSearch(c.id) } }
                }
            }
            item {
                SectionHeader("Popular near you", action = "See all", onAction = { onOpenSearch(null) })
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.popular, key = { it.item.id }) { s ->
                        ItemCard(s, { onOpenItem(s.item.id) }, Modifier.width(200.dp), sharedKey = "art-${s.item.id}") { viewModel.toggleFavourite(s.item.id) }
                    }
                }
            }
            item {
                SectionHeader("Top providers")
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.topProviders, key = { it.id }) { p -> ProviderChip(p) }
                }
            }
            if (state.recent.isNotEmpty()) item {
                SectionHeader("Recently viewed")
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.recent, key = { it.item.id }) { s -> ItemCard(s, { onOpenItem(s.item.id) }, Modifier.width(160.dp)) }
                }
            }
        }
    }
}

@Composable
private fun PromoBanner(onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(MaterialTheme.shapes.medium)
            .background(Brush.linearGradient(listOf(primary, Color(0xFF134E5E))))
            .clickable(onClick = onClick).padding(20.dp),
    ) {
        Column(Modifier.fillMaxWidth(0.7f)) {
            Text("Weekend getaway?", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text("Tents, stoves and power stations. Weekly rates save up to 25%.", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
            Spacer(Modifier.height(12.dp))
            Surface(shape = CircleShape, color = Color.White) {
                Text("Explore camping", Modifier.padding(horizontal = 14.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge, color = Color(0xFF134E5E))
            }
        }
        Icon(Icons.Rounded.Forest, null, tint = Color.White.copy(alpha = 0.25f), modifier = Modifier.align(Alignment.CenterEnd).size(96.dp))
    }
}

@Composable
private fun CategoryTile(category: Category, onClick: () -> Unit) {
    Column(Modifier.width(76.dp).clip(MaterialTheme.shapes.small).clickable(onClick = onClick).padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(60.dp).clip(MaterialTheme.shapes.medium).background(Brush.linearGradient(CategoryVisuals.gradient(category.iconKey))),
            contentAlignment = Alignment.Center,
        ) { Icon(CategoryVisuals.icon(category.iconKey), null, tint = Color.White) }
        Spacer(Modifier.height(6.dp))
        Text(category.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ProviderChip(p: Provider) {
    Card(shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.padding(12.dp).width(200.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(p.shopName)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(p.shopName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("★ ${p.rating} · ${p.locationText.substringBefore(',')}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}
