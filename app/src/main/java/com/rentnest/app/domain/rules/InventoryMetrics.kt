package com.rentnest.app.domain.rules

import com.rentnest.app.domain.model.*

/** Per-item stock and money figures for the inventory screen. */
data class ItemStock(
    val item: Item,
    /** Units the store holds, whatever their state (excludes retired). */
    val units: Int,
    /** Units out with customers right now. */
    val out: Int,
    /** Units in maintenance. */
    val inService: Int,
) {
    val inStore: Int get() = units - out
    val worth: Long get() = item.unitValue * units
    /** Earnings per day if every rentable unit were out. */
    val dailyPotential: Long get() = item.dailyRate * (units - inService)
    /** Earnings per day from units out right now. */
    val dailyEarning: Long get() = item.dailyRate * out
    val dailyVendorCost: Long get() = if (item.ownership == Ownership.BORROWED) item.vendorCostPerDay * units else 0
}

data class InventoryTotals(
    val products: Int,
    val units: Int,
    val unitsOut: Int,
    val unitsInStore: Int,
    val unitsInService: Int,
    val ownedWorth: Long,
    val borrowedWorth: Long,
    val dailyPotential: Long,
    val dailyEarning: Long,
    val dailyVendorCost: Long,
    val borrowedProducts: Int,
) {
    val totalWorth: Long get() = ownedWorth + borrowedWorth
    /** Today's rental income minus what borrowed stock costs per day. */
    val netDaily: Long get() = dailyEarning - dailyVendorCost
}

object InventoryMetrics {
    fun stock(item: Item, units: List<ItemUnit>, bookings: List<Booking>): ItemStock {
        val held = units.filter { it.status != UnitStatus.RETIRED }
        val heldIds = held.map { it.id }.toSet()
        val out = bookings.filter { it.status == BookingStatus.ACTIVE && it.unitId in heldIds }.mapNotNull { it.unitId }.toSet().size
        return ItemStock(item, held.size, out, held.count { it.status == UnitStatus.MAINTENANCE })
    }

    fun totals(stocks: List<ItemStock>): InventoryTotals {
        val (borrowed, owned) = stocks.partition { it.item.ownership == Ownership.BORROWED }
        return InventoryTotals(
            products = stocks.size,
            units = stocks.sumOf { it.units },
            unitsOut = stocks.sumOf { it.out },
            unitsInStore = stocks.sumOf { it.inStore },
            unitsInService = stocks.sumOf { it.inService },
            ownedWorth = owned.sumOf { it.worth },
            borrowedWorth = borrowed.sumOf { it.worth },
            dailyPotential = stocks.sumOf { it.dailyPotential },
            dailyEarning = stocks.sumOf { it.dailyEarning },
            dailyVendorCost = stocks.sumOf { it.dailyVendorCost },
            borrowedProducts = borrowed.size,
        )
    }
}
