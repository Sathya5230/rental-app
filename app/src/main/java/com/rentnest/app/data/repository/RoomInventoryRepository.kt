package com.rentnest.app.data.repository

import androidx.room.withTransaction
import com.rentnest.app.data.local.AppDatabase
import com.rentnest.app.data.local.AuditEntity
import com.rentnest.app.data.local.toDomain
import com.rentnest.app.data.local.toEntity
import com.rentnest.app.domain.DomainError
import com.rentnest.app.domain.Outcome
import com.rentnest.app.domain.model.BookingStatus
import com.rentnest.app.domain.model.ItemUnit
import com.rentnest.app.domain.model.UnitStatus
import com.rentnest.app.domain.repository.AuditEntry
import com.rentnest.app.domain.repository.InventoryRepository
import com.rentnest.app.domain.time.TimeProvider
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomInventoryRepository @Inject constructor(
    private val db: AppDatabase,
    private val time: TimeProvider,
) : InventoryRepository {
    private val dao = db.inventoryDao()

    override fun unitsForItem(itemId: Long) = dao.unitsForItem(itemId).map { l -> l.map { it.toDomain() } }
    override fun unitsForProvider(providerId: Long) = dao.unitsForProvider(providerId).map { l -> l.map { it.toDomain() } }
    override fun allUnits() = dao.allUnits().map { l -> l.map { it.toDomain() } }

    override suspend fun addUnit(itemId: Long): ItemUnit = db.withTransaction { UnitTagger.insertUnit(db, itemId).toDomain() }

    override suspend fun updateUnit(unit: ItemUnit): Outcome<ItemUnit> = db.withTransaction { doUpdate(unit) }

    private suspend fun doUpdate(unit: ItemUnit): Outcome<ItemUnit> {
        val current = dao.unitOnce(unit.id) ?: return Outcome.Failure(DomainError.NotFound)
        if (current.status == UnitStatus.AVAILABLE && unit.status != UnitStatus.AVAILABLE) {
            val today = time.today()
            val busy = db.bookingDao().bookingsForItemOnce(current.itemId).any {
                it.unitId == unit.id &&
                    (it.status == BookingStatus.ACTIVE || (it.status == BookingStatus.ACCEPTED && !it.endDate.isBefore(today)))
            }
            if (busy) return Outcome.Failure(DomainError.UnitBusy)
        }
        dao.updateUnit(unit.toEntity())
        return Outcome.Success(unit)
    }

    override fun audits() = dao.audits().map { l -> l.map { it.toDomain() } }

    override suspend fun submitAudit(entries: List<AuditEntry>): Outcome<Int> {
        if (entries.isEmpty()) return Outcome.Success(0)
        val at = time.nowMillis()
        dao.insertAudits(entries.map {
            AuditEntity(itemId = it.itemId, timestamp = at, expected = it.expected, counted = it.counted.coerceAtLeast(0), notes = it.notes.trim())
        })
        return Outcome.Success(entries.size)
    }
}
