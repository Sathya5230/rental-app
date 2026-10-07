package com.rentnest.app.domain.rules

import com.rentnest.app.domain.model.Booking
import com.rentnest.app.domain.model.DateRange
import com.rentnest.app.domain.model.Item
import com.rentnest.app.domain.model.ItemUnit
import com.rentnest.app.domain.model.UnitCondition
import com.rentnest.app.domain.model.UnitStatus
import java.time.LocalDate

sealed interface StockAlert {
    val itemId: Long
    val itemTitle: String

    data class LowStock(override val itemId: Long, override val itemTitle: String, val minFree: Int, val date: LocalDate) : StockAlert
    data class Maintenance(override val itemId: Long, override val itemTitle: String, val unitTags: List<String>) : StockAlert
}

object StockAlerts {
    const val WINDOW_DAYS = 7L

    fun forItem(item: Item, units: List<ItemUnit>, bookings: List<Booking>, today: LocalDate): List<StockAlert> {
        if (!item.isActive) return emptyList()
        val alerts = mutableListOf<StockAlert>()
        val window = DateRange(today, today.plusDays(WINDOW_DAYS - 1))
        val worst = window.dates().map { it to AvailabilityCalculator.freeCountOn(it, units, bookings) }.minBy { it.second }
        if (worst.second <= item.lowStockThreshold) {
            alerts += StockAlert.LowStock(item.id, item.title, worst.second, worst.first)
        }
        val attention = units.filter {
            it.status == UnitStatus.MAINTENANCE || (it.condition == UnitCondition.DAMAGED && it.status != UnitStatus.RETIRED)
        }
        if (attention.isNotEmpty()) alerts += StockAlert.Maintenance(item.id, item.title, attention.map { it.tag })
        return alerts
    }
}
