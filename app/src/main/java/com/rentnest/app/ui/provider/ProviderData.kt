package com.rentnest.app.ui.provider

import com.rentnest.app.domain.ADMIN_USER_ID
import com.rentnest.app.domain.model.*
import com.rentnest.app.domain.repository.BookingRepository
import com.rentnest.app.domain.repository.CatalogRepository
import com.rentnest.app.domain.repository.InventoryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import javax.inject.Inject

/** Everything about the admin's store, as one stream. */
data class ShopSnapshot(
    val provider: Provider,
    val items: List<Item>,
    val units: List<ItemUnit>,
    val bookings: List<Booking>,
    val userNames: Map<Long, String>,
    val categories: List<Category>,
)

class ObserveShop @Inject constructor(
    private val catalog: CatalogRepository,
    private val inventory: InventoryRepository,
    private val bookings: BookingRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<ShopSnapshot?> = catalog.providerForUser(ADMIN_USER_ID).flatMapLatest { p ->
        if (p == null) flowOf(null)
        else combine(
            catalog.itemsByProvider(p.id), inventory.unitsForProvider(p.id), bookings.bookingsForProvider(p.id),
            catalog.users(), catalog.categories(),
        ) { items, units, books, users, cats -> ShopSnapshot(p, items, units, books, users.associate { it.id to it.name }, cats) }
    }
}
