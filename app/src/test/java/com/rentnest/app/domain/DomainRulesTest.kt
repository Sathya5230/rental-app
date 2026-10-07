package com.rentnest.app.domain

import com.rentnest.app.domain.format.DateFormats
import com.rentnest.app.domain.format.MoneyFormatter
import com.rentnest.app.domain.model.*
import com.rentnest.app.domain.rules.*
import com.rentnest.app.domain.time.DateMillis
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.TimeZone

private val D0: LocalDate = LocalDate.of(2026, 10, 7)
private fun d(offset: Long) = D0.plusDays(offset)
private fun unit(id: Long, status: UnitStatus = UnitStatus.AVAILABLE, condition: UnitCondition = UnitCondition.GOOD) =
    ItemUnit(id, 1, "U$id", condition, status)
private fun booking(id: Long, unitId: Long?, from: Long, to: Long, status: BookingStatus) =
    Booking(id, 1, unitId, 9, d(from), d(to), status, 0, 0, createdAt = 0)

class MoneyFormatterTest {
    @Test fun formatsIndianGrouping() {
        assertEquals("₹0", MoneyFormatter.format(0))
        assertEquals("₹1,500", MoneyFormatter.format(150_000))
        assertEquals("₹1,23,456.78", MoneyFormatter.format(12_345_678))
        assertEquals("₹12,34,567", MoneyFormatter.format(123_456_700))
        assertEquals("-₹50", MoneyFormatter.format(-5_000))
    }
    @Test fun parsesUserInput() {
        assertEquals(150_000L, MoneyFormatter.parseRupees("1,500"))
        assertEquals(1_250L, MoneyFormatter.parseRupees("12.5"))
        assertNull(MoneyFormatter.parseRupees("abc"))
        assertNull(MoneyFormatter.parseRupees("-3"))
        assertNull(MoneyFormatter.parseRupees(""))
    }
    @Test fun compact() {
        assertEquals("₹950", MoneyFormatter.compact(95_000))
        assertEquals("₹12.5k", MoneyFormatter.compact(1_250_000))
        assertEquals("₹1.2L", MoneyFormatter.compact(12_000_000))
    }
}

class DateTest {
    @Test fun dateMillisRoundTripsInNonUtcZone() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))
            val date = LocalDate.of(2026, 10, 31)
            assertEquals(date, DateMillis.toLocalDate(DateMillis.toUtcMillis(date)))
        } finally {
            TimeZone.setDefault(original)
        }
    }
    @Test fun rangeIsInclusive() {
        val r = DateRange(d(0), d(2))
        assertEquals(3, r.days)
        assertTrue(d(2) in r)
        assertFalse(d(3) in r)
        assertTrue(r.overlaps(DateRange(d(2), d(5))))
        assertFalse(r.overlaps(DateRange(d(3), d(5))))
        assertEquals(0, DateRange(d(2), d(0)).days)
    }
    @Test fun formats() {
        assertEquals("7 Oct – 9 Oct", DateFormats.range(DateRange(d(0), d(2))))
        assertEquals("7 Oct", DateFormats.range(DateRange(d(0), d(0))))
        assertEquals("RN-00042", DateFormats.bookingCode(42))
    }
}

class PricingEngineTest {
    private fun quote(days: Long, weekly: Long = 300_000) =
        (PricingEngine.quote(50_000, weekly, 200_000, DateRange(d(0), d(days - 1))) as Outcome.Success).value

    @Test fun dailyPricing() {
        assertEquals(50_000L, quote(1).subtotal)
        assertEquals(250_000L, quote(1).totalDueNow)
        assertEquals(150_000L, quote(3).subtotal)
    }
    @Test fun weeklyPricing() {
        assertEquals(300_000L, quote(7).subtotal)
        assertEquals(1, quote(7).weeks)
        assertEquals(600_000L, quote(13).subtotal)
    }
    @Test fun partialWeekRoundsUpWhenCheaper() {
        val q = quote(6, weekly = 250_000)
        assertEquals(250_000L, q.subtotal)
        assertTrue(q.bestPriceApplied)
        assertEquals(400_000L, quote(10, weekly = 250_000).subtotal)
    }
    @Test fun zeroWeeklyRateFallsBackToSevenTimesDaily() {
        assertEquals(350_000L, quote(7, weekly = 0).subtotal)
    }
    @Test fun invalidRange() {
        val r = PricingEngine.quote(50_000, 300_000, 0, DateRange(d(3), d(1)))
        assertEquals(Outcome.Failure(DomainError.InvalidDateRange), r)
    }
}

class AvailabilityCalculatorTest {
    private val units = listOf(unit(1), unit(2), unit(3, UnitStatus.MAINTENANCE))

    @Test fun countsOnlyReservingBookings() {
        assertEquals(2, AvailabilityCalculator.freeCountOn(d(1), units, emptyList()))
        val bs = listOf(
            booking(1, 1, 0, 2, BookingStatus.ACCEPTED),
            booking(2, null, 0, 2, BookingStatus.REQUESTED),
            booking(3, 2, 0, 2, BookingStatus.CANCELLED),
        )
        assertEquals(1, AvailabilityCalculator.freeCountOn(d(1), units, bs))
        assertEquals(2, AvailabilityCalculator.freeCountOn(d(3), units, bs))
    }
    @Test fun fullyBookedDayBlocksRange() {
        val bs = listOf(booking(1, 1, 2, 2, BookingStatus.ACCEPTED), booking(2, 2, 1, 3, BookingStatus.ACTIVE))
        assertFalse(AvailabilityCalculator.isBookable(DateRange(d(0), d(4)), units, bs))
        assertTrue(AvailabilityCalculator.isBookable(DateRange(d(4), d(6)), units, bs))
        assertEquals(setOf(d(2)), AvailabilityCalculator.unavailableDates(DateRange(d(0), d(6)), units, bs))
    }
    @Test fun freeUnitsExcludeBusyAndMaintenance() {
        val bs = listOf(booking(1, 1, 0, 2, BookingStatus.ACCEPTED))
        assertEquals(listOf(2L), AvailabilityCalculator.freeUnitsFor(DateRange(d(1), d(1)), units, bs).map { it.id })
    }
    @Test fun bookingOnServicedUnitStillConsumesSlot() {
        val bs = listOf(booking(1, 3, 0, 2, BookingStatus.ACCEPTED))
        assertEquals(1, AvailabilityCalculator.freeCountOn(d(1), units, bs))
    }
    @Test fun noUsableUnitsMeansNothingBookable() {
        val serviced = listOf(unit(1, UnitStatus.MAINTENANCE))
        assertFalse(AvailabilityCalculator.isBookable(DateRange(d(0), d(0)), serviced, emptyList()))
        assertFalse(AvailabilityCalculator.isBookable(DateRange(d(0), d(0)), emptyList(), emptyList()))
    }
}

class BookingStateMachineTest {
    @Test fun validTransitions() {
        assertEquals(Outcome.Success(BookingStatus.ACCEPTED), BookingStateMachine.next(BookingStatus.REQUESTED, BookingAction.ACCEPT))
        assertEquals(Outcome.Success(BookingStatus.ACTIVE), BookingStateMachine.next(BookingStatus.ACCEPTED, BookingAction.CHECK_OUT))
        assertEquals(Outcome.Success(BookingStatus.RETURNED), BookingStateMachine.next(BookingStatus.ACTIVE, BookingAction.RETURN))
    }
    @Test fun invalidTransitionsFail() {
        val valid = setOf(
            BookingStatus.REQUESTED to BookingAction.ACCEPT, BookingStatus.REQUESTED to BookingAction.DECLINE,
            BookingStatus.REQUESTED to BookingAction.CANCEL, BookingStatus.ACCEPTED to BookingAction.CANCEL,
            BookingStatus.ACCEPTED to BookingAction.CHECK_OUT, BookingStatus.ACTIVE to BookingAction.RETURN,
        )
        for (s in BookingStatus.entries) for (a in BookingAction.entries) {
            if ((s to a) in valid) continue
            assertTrue("$s/$a", BookingStateMachine.next(s, a) is Outcome.Failure)
        }
    }
    @Test fun reviewOnlyOnceAfterReturn() {
        val b = booking(1, 1, 0, 1, BookingStatus.RETURNED)
        assertTrue(BookingStateMachine.canReview(b))
        assertFalse(BookingStateMachine.canReview(b.copy(reviewed = true)))
        assertFalse(BookingStateMachine.canReview(b.copy(status = BookingStatus.ACTIVE)))
    }
}

class StockAlertsTest {
    private val item = Item(1, 1, 1, "Tent", "", emptyList(), 100, 500, 0, emptyMap(), lowStockThreshold = 1)

    @Test fun lowStockWithinWindow() {
        val units = listOf(unit(1), unit(2), unit(3))
        assertTrue(StockAlerts.forItem(item, units, emptyList(), D0).isEmpty())
        val bs = listOf(booking(1, 1, 2, 2, BookingStatus.ACCEPTED), booking(2, 2, 2, 3, BookingStatus.ACCEPTED))
        assertEquals(listOf(StockAlert.LowStock(1, "Tent", 1, d(2))), StockAlerts.forItem(item, units, bs, D0))
        val later = listOf(booking(1, 1, 8, 8, BookingStatus.ACCEPTED), booking(2, 2, 8, 9, BookingStatus.ACCEPTED))
        assertTrue(StockAlerts.forItem(item, units, later, D0).isEmpty())
    }
    @Test fun maintenanceAndInactive() {
        val units = listOf(unit(1), unit(2), unit(3, UnitStatus.MAINTENANCE, UnitCondition.DAMAGED), unit(4, UnitStatus.RETIRED, UnitCondition.DAMAGED))
        assertEquals(listOf(StockAlert.Maintenance(1, "Tent", listOf("U3"))), StockAlerts.forItem(item, units, emptyList(), D0))
        assertTrue(StockAlerts.forItem(item.copy(isActive = false), units, emptyList(), D0).isEmpty())
    }
}

class ItemValidatorTest {
    @Test fun requiresFields() {
        val errors = ItemValidator.validate(ItemDraft(providerId = 1, deposit = -1))
        assertEquals(setOf(ItemField.TITLE, ItemField.CATEGORY, ItemField.DAILY_RATE, ItemField.WEEKLY_RATE, ItemField.DEPOSIT), errors)
    }
    @Test fun weeklyCannotExceedSevenDays() {
        val ok = ItemDraft(providerId = 1, title = "Drill", categoryId = 1, dailyRate = 100, weeklyRate = 700)
        assertTrue(ItemValidator.validate(ok).isEmpty())
        assertEquals(setOf(ItemField.WEEKLY_RATE), ItemValidator.validate(ok.copy(weeklyRate = 701)))
    }
}
