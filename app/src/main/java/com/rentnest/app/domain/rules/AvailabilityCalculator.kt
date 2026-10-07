package com.rentnest.app.domain.rules

import com.rentnest.app.domain.model.Booking
import com.rentnest.app.domain.model.BookingStatus
import com.rentnest.app.domain.model.DateRange
import com.rentnest.app.domain.model.ItemUnit
import com.rentnest.app.domain.model.UnitStatus
import java.time.LocalDate

object AvailabilityCalculator {
    val RESERVING = setOf(BookingStatus.ACCEPTED, BookingStatus.ACTIVE)

    fun usableUnits(units: List<ItemUnit>): List<ItemUnit> = units.filter { it.status == UnitStatus.AVAILABLE }

    /**
     * Free units on [date]. A reserving booking whose unit is no longer usable (or unassigned)
     * still consumes a slot, so a promised customer is never double-booked.
     */
    fun freeCountOn(date: LocalDate, units: List<ItemUnit>, bookings: List<Booking>): Int {
        val usableIds = usableUnits(units).map { it.id }.toSet()
        val reserving = bookings.filter { it.status in RESERVING && date in it.range }
        val reservedUsable = reserving.mapNotNull { it.unitId }.filter { it in usableIds }.toSet()
        val orphaned = reserving.count { it.unitId == null || it.unitId !in usableIds }
        return (usableIds.size - reservedUsable.size - orphaned).coerceAtLeast(0)
    }

    fun isBookable(range: DateRange, units: List<ItemUnit>, bookings: List<Booking>): Boolean =
        range.isValid && range.dates().all { freeCountOn(it, units, bookings) >= 1 }

    fun unavailableDates(window: DateRange, units: List<ItemUnit>, bookings: List<Booking>): Set<LocalDate> =
        window.dates().filter { freeCountOn(it, units, bookings) == 0 }.toSet()

    fun freeUnitsFor(range: DateRange, units: List<ItemUnit>, bookings: List<Booking>): List<ItemUnit> {
        val busy = bookings.filter { it.status in RESERVING && it.range.overlaps(range) }.mapNotNull { it.unitId }.toSet()
        return usableUnits(units).filter { it.id !in busy }
    }
}
