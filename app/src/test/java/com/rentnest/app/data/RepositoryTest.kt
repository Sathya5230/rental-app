package com.rentnest.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rentnest.app.data.local.AppDatabase
import com.rentnest.app.data.repository.*
import com.rentnest.app.data.seed.DemoDataManager
import com.rentnest.app.data.seed.SeedData
import com.rentnest.app.domain.DEMO_USER_ID
import com.rentnest.app.domain.DomainError
import com.rentnest.app.domain.Outcome
import com.rentnest.app.domain.model.*
import com.rentnest.app.domain.rules.AvailabilityCalculator
import com.rentnest.app.domain.rules.ItemDraft
import com.rentnest.app.domain.time.TimeProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

class FixedTime(private val day: LocalDate = LocalDate.of(2026, 10, 7)) : TimeProvider {
    override fun today() = day
    override fun nowMillis() = 1_791_000_000_000L
}

@RunWith(RobolectricTestRunner::class)
class RepositoryTest {
    private lateinit var db: AppDatabase
    private val time = FixedTime()
    private lateinit var bookings: RoomBookingRepository
    private lateinit var inventory: RoomInventoryRepository
    private lateinit var catalog: RoomCatalogRepository
    private val today = time.today()

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        DemoDataManager(db, time).seedIfEmpty()
        bookings = RoomBookingRepository(db, time)
        inventory = RoomInventoryRepository(db, time)
        catalog = RoomCatalogRepository(db)
    }

    @After fun tearDown() = db.close()

    private fun range(from: Long, to: Long) = DateRange(today.plusDays(from), today.plusDays(to))

    @Test fun seedIsConsistent() = runBlocking {
        val s = SeedData.build(today, time.nowMillis())
        assertEquals(40, s.items.size)
        assertEquals(8, s.categories.size)
        val reserving = s.bookings.filter { it.status == BookingStatus.ACCEPTED || it.status == BookingStatus.ACTIVE }
        reserving.forEach { b ->
            assertNotNull(b.unitId)
            assertEquals(b.itemId, s.units.first { it.id == b.unitId }.itemId)
            val clash = reserving.any { o -> o.id != b.id && o.unitId == b.unitId && DateRange(o.startDate, o.endDate).overlaps(DateRange(b.startDate, b.endDate)) }
            assertFalse("unit double booked: $b", clash)
        }
        assertTrue(s.items.all { i -> s.units.any { it.itemId == i.id } })
        // Re-seeding is a no-op
        DemoDataManager(db, time).seedIfEmpty()
        assertEquals(40, catalog.allItems().first().size)
    }

    @Test fun requestCreatesBookingAndNotifiesProvider() = runBlocking {
        val r = bookings.requestBooking(itemId = 11, customerId = DEMO_USER_ID, range = range(10, 12))
        val b = (r as Outcome.Success).value
        assertEquals(BookingStatus.REQUESTED, b.status)
        assertEquals(3 * 40_000L, b.subtotal)
        val owner = 5L // item 11 -> provider (10 % 6) + 1 = 5, owned by user 5
        assertTrue(db.notificationDao().forUser(owner, Audience.PROVIDER).first().any { it.bookingId == b.id })
    }

    @Test fun requestRejectsOwnListing() = runBlocking {
        assertEquals(Outcome.Failure(DomainError.OwnListing), bookings.requestBooking(1, DEMO_USER_ID, range(20, 21)))
    }

    @Test fun requestRejectsPastStart() = runBlocking {
        assertEquals(Outcome.Failure(DomainError.InvalidDateRange), bookings.requestBooking(11, DEMO_USER_ID, range(-1, 2)))
    }

    @Test fun requestRejectsRangeSpanningFullyBookedDay() = runBlocking {
        // Item 4 has a single unit, ACTIVE from -2..2
        assertEquals(Outcome.Failure(DomainError.DatesUnavailable), bookings.requestBooking(4, 8, range(1, 5)))
        assertTrue(bookings.requestBooking(4, 8, range(3, 5)) is Outcome.Success)
    }

    @Test fun fullLifecycleWithDamagedReturn() = runBlocking {
        val b = (bookings.requestBooking(11, DEMO_USER_ID, range(1, 3)) as Outcome.Success).value
        val units = inventory.unitsForItem(11).first()
        val all = bookings.bookingsForItem(11).first()
        val unit = AvailabilityCalculator.freeUnitsFor(b.range, units, all).first()
        assertEquals(BookingStatus.ACCEPTED, (bookings.accept(b.id, unit.id) as Outcome.Success).value.status)
        assertEquals(BookingStatus.ACTIVE, (bookings.checkOut(b.id, listOf("ID verified"), "") as Outcome.Success).value.status)
        val returned = (bookings.processReturn(b.id, emptyList(), UnitCondition.DAMAGED, "Torn flap", 50_000) as Outcome.Success).value
        assertEquals(BookingStatus.RETURNED, returned.status)
        assertEquals(50_000L, returned.damageFee)
        val after = inventory.unitsForItem(11).first().first { it.id == unit.id }
        assertEquals(UnitStatus.MAINTENANCE, after.status)
        assertEquals(UnitCondition.DAMAGED, after.condition)
        assertEquals(2, db.bookingDao().handoversOnce(b.id).size)
        assertTrue(bookings.submitReview(b.id, 4, "Nice") is Outcome.Success)
        assertEquals(Outcome.Failure(DomainError.NotReviewable), bookings.submitReview(b.id, 4, "Again"))
    }

    @Test fun acceptRejectsBusyUnitAndInvalidTransitions() = runBlocking {
        // Item 4's only unit is busy with an ACTIVE booking
        val b = (bookings.requestBooking(4, 8, range(1, 1)).let { it } as? Outcome.Success)?.value
        assertNull(b)
        val req = (bookings.requestBooking(11, DEMO_USER_ID, range(1, 1)) as Outcome.Success).value
        assertTrue(bookings.checkOut(req.id, emptyList(), "") is Outcome.Failure)
        val unit13 = inventory.unitsForItem(13).first().first() // reserved by the seeded ACCEPTED booking
        val other = (bookings.requestBooking(13, 8, range(1, 1)) as Outcome.Success).value
        assertEquals(Outcome.Failure(DomainError.NoUnitFree), bookings.accept(other.id, unit13.id))
    }

    @Test fun updateUnitRefusesWhenUnitHasAcceptedBooking() = runBlocking {
        val unit13 = inventory.unitsForItem(13).first().first()
        assertEquals(Outcome.Failure(DomainError.UnitBusy), inventory.updateUnit(unit13.copy(status = UnitStatus.MAINTENANCE)))
        assertTrue(inventory.updateUnit(unit13.copy(condition = UnitCondition.FAIR)) is Outcome.Success)
        val spare = inventory.unitsForItem(13).first()[1]
        assertTrue(inventory.updateUnit(spare.copy(status = UnitStatus.RETIRED)) is Outcome.Success)
    }

    @Test fun saveNewItemAddsFirstUnit() = runBlocking {
        val draft = ItemDraft(providerId = 1, title = "Tripod", categoryId = 1, dailyRate = 20_000, weeklyRate = 100_000, deposit = 0)
        val id = (catalog.saveItem(draft) as Outcome.Success).value
        val units = inventory.unitsForItem(id).first()
        assertEquals(1, units.size)
        assertEquals("CAM$id-01", units.first().tag)
        assertTrue(catalog.saveItem(draft.copy(title = "")) is Outcome.Failure)
    }
}
