package com.rentnest.app.ui.model

import com.rentnest.app.domain.DEMO_USER_ID
import com.rentnest.app.domain.model.*
import com.rentnest.app.domain.repository.CatalogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

data class ItemSummary(
    val item: Item,
    val rating: RatingSummary?,
    val providerName: String,
    val isFavourite: Boolean,
    val categoryName: String = "",
)

data class CatalogSnapshot(
    val categories: List<Category>,
    val items: List<Item>,
    val providers: Map<Long, Provider>,
    val ratings: Map<Long, RatingSummary>,
    val bookingCounts: Map<Long, Int>,
    val favourites: Set<Long>,
) {
    private val categoryNames = categories.associate { it.id to it.name }
    fun summary(item: Item) = ItemSummary(item, ratings[item.id], providers[item.providerId]?.shopName.orEmpty(), item.id in favourites, categoryNames[item.categoryId].orEmpty())
}

class ObserveCatalog @Inject constructor(private val catalog: CatalogRepository) {
    operator fun invoke(): Flow<CatalogSnapshot> = combine(
        catalog.categories(), catalog.activeItems(), catalog.providers(), catalog.ratingSummaries(),
        combine(catalog.bookingCounts(), catalog.favouriteIds(DEMO_USER_ID)) { c, f -> c to f },
    ) { categories, items, providers, ratings, (counts, favourites) ->
        CatalogSnapshot(categories, items, providers.associateBy { it.id }, ratings, counts, favourites)
    }
}
