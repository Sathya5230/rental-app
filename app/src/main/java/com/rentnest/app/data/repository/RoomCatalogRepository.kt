package com.rentnest.app.data.repository

import androidx.room.withTransaction
import com.rentnest.app.data.local.*
import com.rentnest.app.domain.Outcome
import com.rentnest.app.domain.model.*
import com.rentnest.app.domain.repository.CatalogRepository
import com.rentnest.app.domain.rules.ItemDraft
import com.rentnest.app.domain.rules.ItemValidator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomCatalogRepository @Inject constructor(private val db: AppDatabase) : CatalogRepository {
    private val dao = db.catalogDao()

    override fun categories() = dao.categories().map { l -> l.map { it.toDomain() } }
    override fun activeItems() = dao.activeItems().map { l -> l.map { it.toDomain() } }
    override fun allItems() = dao.allItems().map { l -> l.map { it.toDomain() } }
    override fun item(id: Long) = dao.item(id).map { it?.toDomain() }
    override fun itemsByProvider(providerId: Long) = dao.itemsByProvider(providerId).map { l -> l.map { it.toDomain() } }
    override fun providers() = dao.providers().map { l -> l.map { it.toDomain() } }
    override fun providerForUser(userId: Long) = dao.providerForUser(userId).map { it?.toDomain() }
    override fun reviewsForItem(itemId: Long) = dao.reviewsForItem(itemId).map { l -> l.map { it.toDomain() } }
    override fun ratingSummaries(): Flow<Map<Long, RatingSummary>> =
        dao.ratingSummaries().map { rows -> rows.associate { it.itemId to RatingSummary(it.average, it.count) } }
    override fun bookingCounts(): Flow<Map<Long, Int>> = dao.bookingCounts().map { rows -> rows.associate { it.itemId to it.count } }
    override fun favouriteIds(userId: Long) = dao.favouriteIds(userId).map { it.toSet() }
    override fun user(id: Long) = dao.user(id).map { it?.toDomain() }
    override fun users() = dao.users().map { l -> l.map { it.toDomain() } }

    override suspend fun toggleFavourite(userId: Long, itemId: Long) = db.withTransaction {
        val f = FavouriteEntity(userId, itemId)
        if (dao.isFavourite(userId, itemId) > 0) dao.removeFavourite(f) else dao.addFavourite(f)
    }

    override suspend fun saveItem(draft: ItemDraft): Outcome<Long> {
        val item = when (val r = ItemValidator.toItem(draft)) {
            is Outcome.Success -> r.value
            is Outcome.Failure -> return r
        }
        return db.withTransaction {
            if (item.id == 0L) {
                val id = dao.insertItem(item.toEntity())
                UnitTagger.insertUnit(db, id)
                Outcome.Success(id)
            } else {
                dao.updateItem(item.toEntity())
                Outcome.Success(item.id)
            }
        }
    }
}
