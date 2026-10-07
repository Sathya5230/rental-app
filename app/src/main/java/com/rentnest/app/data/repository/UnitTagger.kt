package com.rentnest.app.data.repository

import com.rentnest.app.data.local.AppDatabase
import com.rentnest.app.data.local.ItemUnitEntity
import com.rentnest.app.domain.model.UnitCondition
import com.rentnest.app.domain.model.UnitStatus

internal object UnitTagger {
    /** e.g. "CAM12-03": category prefix, item id, per-item sequence. Units are never deleted, so tags stay unique. */
    fun tag(categoryKey: String, itemId: Long, sequence: Int): String =
        "${categoryKey.take(3).uppercase()}$itemId-${sequence.toString().padStart(2, '0')}"

    suspend fun insertUnit(db: AppDatabase, itemId: Long): ItemUnitEntity {
        val item = checkNotNull(db.catalogDao().itemOnce(itemId)) { "Item $itemId not found" }
        val key = db.catalogDao().categoryOnce(item.categoryId)?.iconKey ?: "itm"
        val seq = db.inventoryDao().unitsForItemOnce(itemId).size + 1
        val entity = ItemUnitEntity(itemId = itemId, tag = tag(key, itemId, seq), condition = UnitCondition.NEW, status = UnitStatus.AVAILABLE)
        return entity.copy(id = db.inventoryDao().insertUnit(entity))
    }
}
