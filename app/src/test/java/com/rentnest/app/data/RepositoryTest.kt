package com.rentnest.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rentnest.app.data.local.AppDatabase
import com.rentnest.app.data.repository.*
import com.rentnest.app.data.seed.DemoDataManager
import com.rentnest.app.data.seed.SeedData
import com.rentnest.app.domain.ADMIN_USER_ID
import com.rentnest.app.domain.DEMO_USER_ID
import com.rentnest.app.domain.SmsGateway
import com.rentnest.app.domain.format.DateFormats
import com.rentnest.app.domain.repository.AuditEntry
import com.rentnest.app.domain.rules.LateFees
import com.rentnest.app.domain.DomainError
import com.rentnest.app.domain.Outcome
import com.rentnest.app.domain.model.*
import com.rentnest.app.domain.rules.AvailabilityCalculator
import com.rentnest.app.domain.rules.ItemDraft
import com.rentnest.app.domain.time.TimeProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneId

class FixedTime(private val day: LocalDate = LocalDate.of(2026, 10, 7)) : TimeProvider {
    override fun today() = day
    /** Midday on [day], so "now" and "today" agree. */
    override fun nowMillis() = day.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

private const val PHONE = "9845077777"

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

    @Test fun requestCreatesBookingAndNotifiesAdmin() = runBlocking {
        val r = bookings.requestBooking(itemId = 11, customerId = DEMO_USER_ID, range = range(10, 12), contactPhone = PHONE)
        val b = (r as Outcome.Success).value
        assertEquals(BookingStatus.REQUESTED, b.status)
        assertEquals(3 * 40_000L, b.subtotal)
        assertEquals("+91 98450 77777", b.contactPhone)
        assertTrue(db.notificationDao().forUser(ADMIN_USER_ID, Audience.ADMIN).first().any { it.bookingId == b.id })
    }

    @Test fun requestRejectsInvalidPhone() = runBlocking {
        assertEquals(Outcome.Failure(DomainError.InvalidPhone), bookings.requestBooking(11, DEMO_USER_ID, range(20, 21), "12345"))
    }

    @Test fun everyItemBelongsToTheAdminStore() = runBlocking {
        val store = catalog.providerForUser(ADMIN_USER_ID).first()!!
        assertTrue(catalog.allItems().first().all { it.providerId == store.id })
        assertTrue(bookings.requestBooking(1, DEMO_USER_ID, range(20, 21), PHONE) is Outcome.Success)
    }

    @Test fun requestRejectsPastStart() = runBlocking {
        assertEquals(Outcome.Failure(DomainError.InvalidDateRange), bookings.requestBooking(11, DEMO_USER_ID, range(-1, 2), PHONE))
    }

    @Test fun requestRejectsRangeSpanningFullyBookedDay() = runBlocking {
        // Item 4 has a single unit, ACTIVE from -2..2
        assertEquals(Outcome.Failure(DomainError.DatesUnavailable), bookings.requestBooking(4, 8, range(1, 5), PHONE))
        assertTrue(bookings.requestBooking(4, 8, range(3, 5), PHONE) is Outcome.Success)
    }

    @Test fun fullLifecycleWithDamagedReturn() = runBlocking {
        val b = (bookings.requestBooking(11, DEMO_USER_ID, range(1, 3), PHONE) as Outcome.Success).value
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

    @Test fun checkOutAndReturnChargeTransportAndCleaningFees() = runBlocking {
        val b = (bookings.requestBooking(11, DEMO_USER_ID, range(1, 3), PHONE) as Outcome.Success).value
        val units = inventory.unitsForItem(11).first()
        val all = bookings.bookingsForItem(11).first()
        val unit = AvailabilityCalculator.freeUnitsFor(b.range, units, all).first()
        (bookings.accept(b.id, unit.id) as Outcome.Success)
        val pickedUp = (bookings.checkOut(b.id, listOf("ID verified"), "", transportFee = 15_000) as Outcome.Success).value
        assertEquals(15_000L, pickedUp.pickupTransportFee)
        val returned = (bookings.processReturn(b.id, emptyList(), UnitCondition.GOOD, "", damageFee = 0, transportFee = 12_000, cleaningFee = 5_000) as Outcome.Success).value
        assertEquals(12_000L, returned.dropTransportFee)
        assertEquals(5_000L, returned.cleaningFee)
        val notifications = db.notificationDao().forUser(DEMO_USER_ID, Audience.CUSTOMER).first()
        assertTrue(notifications.any { it.title == "Rental closed" && it.body.contains("transport charge") && it.body.contains("cleaning charge") })
    }

    @Test fun acceptRejectsBusyUnitAndInvalidTransitions() = runBlocking {
        // Item 4's only unit is busy with an ACTIVE booking
        val b = (bookings.requestBooking(4, 8, range(1, 1), PHONE).let { it } as? Outcome.Success)?.value
        assertNull(b)
        val req = (bookings.requestBooking(11, DEMO_USER_ID, range(1, 1), PHONE) as Outcome.Success).value
        assertTrue(bookings.checkOut(req.id, emptyList(), "") is Outcome.Failure)
        val unit13 = inventory.unitsForItem(13).first().first() // reserved by the seeded ACCEPTED booking
        val other = (bookings.requestBooking(13, 8, range(1, 1), PHONE) as Outcome.Success).value
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
        val draft = ItemDraft(providerId = 1, title = "Tripod", categoryId = 1, photos = listOf("/photos/tripod.jpg"), dailyRate = 20_000, weeklyRate = 100_000, deposit = 0)
        val id = (catalog.saveItem(draft) as Outcome.Success).value
        val units = inventory.unitsForItem(id).first()
        assertEquals(1, units.size)
        assertEquals("CAM$id-01", units.first().tag)
        assertTrue(catalog.saveItem(draft.copy(title = "")) is Outcome.Failure)
        assertTrue(catalog.saveItem(draft.copy(photos = emptyList())) is Outcome.Failure)
    }

    @Test fun saveBorrowedItemKeepsVendorDetails() = runBlocking {
        val vendor = (catalog.addVendor("Hill Gear", "98450 33333") as Outcome.Success).value
        assertEquals("+91 98450 33333", vendor.phone)
        val draft = ItemDraft(
            providerId = 1, title = "Snow boots", categoryId = 5, photos = listOf("/photos/boots.jpg"), dailyRate = 10_000, weeklyRate = 50_000,
            unitValue = 600_000, ownership = Ownership.BORROWED, vendorId = vendor.id, vendorCostPerDay = 4_000, vendorReturnBy = today.plusDays(10),
        )
        val id = (catalog.saveItem(draft) as Outcome.Success).value
        val item = catalog.item(id).first()!!
        assertEquals(Ownership.BORROWED, item.ownership)
        assertEquals(vendor.id, item.vendorId)
        assertEquals(4_000L, item.vendorCostPerDay)
        assertEquals(today.plusDays(10), item.vendorReturnBy)
        assertEquals(600_000L, item.unitValue)
    }

    @Test fun addCategoryReusesExistingName() = runBlocking {
        val drones = (catalog.addCategory("  Drones ") as Outcome.Success).value
        assertEquals("Drones", drones.name)
        assertEquals("drones", drones.iconKey)
        assertEquals(drones.id, (catalog.addCategory("drones") as Outcome.Success).value.id)
        assertEquals(1L, (catalog.addCategory("cameras") as Outcome.Success).value.id) // seeded "Cameras"
        assertEquals(Outcome.Failure(DomainError.InvalidName), catalog.addCategory("   "))
    }

    @Test fun updatePhoneNormalises() = runBlocking {
        assertEquals("+91 99000 11122", (catalog.updatePhone(DEMO_USER_ID, "9900011122") as Outcome.Success).value.phone)
        assertEquals(Outcome.Failure(DomainError.InvalidPhone), catalog.updatePhone(DEMO_USER_ID, "000"))
    }

    @Test fun lateReturnChargesDailyRatePerLateDay() = runBlocking {
        // Seeded overdue rental: item 33, due two days ago
        val overdue = bookings.allBookings().first().first { it.itemId == 33L && it.status == BookingStatus.ACTIVE }
        val rate = catalog.item(33).first()!!.dailyRate
        val closed = (bookings.processReturn(overdue.id, emptyList(), UnitCondition.GOOD, "", 0) as Outcome.Success).value
        assertEquals(BookingStatus.RETURNED, closed.status)
        assertEquals(2 * rate, closed.lateFee)
        assertTrue(db.notificationDao().forUser(overdue.customerId, Audience.CUSTOMER).first().any { it.title == "Rental closed" && it.body.contains("late fee") })
    }

    @Test fun onTimeReturnHasNoLateFee() = runBlocking {
        val onTime = bookings.allBookings().first().first { it.itemId == 25L && it.status == BookingStatus.ACTIVE }
        assertEquals(0L, (bookings.processReturn(onTime.id, emptyList(), UnitCondition.GOOD, "", 0) as Outcome.Success).value.lateFee)
    }

    @Test fun overdueReminderIsRecordedOnlyForOverdueRentals() = runBlocking {
        val all = bookings.allBookings().first()
        val overdue = all.first { it.itemId == 33L && it.status == BookingStatus.ACTIVE }
        val reminded = (bookings.recordOverdueReminder(overdue.id) as Outcome.Success).value
        assertEquals(time.nowMillis(), reminded.overdueSmsAt)
        assertTrue(db.notificationDao().forUser(overdue.customerId, Audience.CUSTOMER).first().any { it.title == "Return overdue" })
        val notLate = all.first { it.itemId == 25L && it.status == BookingStatus.ACTIVE }
        assertEquals(Outcome.Failure(DomainError.NotOverdue), bookings.recordOverdueReminder(notLate.id))
    }

    @Test fun submitAuditStoresOneRecordPerItem() = runBlocking {
        val before = inventory.audits().first().size
        val r = inventory.submitAudit(listOf(AuditEntry(1, 2, 2, ""), AuditEntry(2, 3, 1, " two missing ")))
        assertEquals(Outcome.Success(2), r)
        val saved = inventory.audits().first()
        assertEquals(before + 2, saved.size)
        val mismatch = saved.first { it.itemId == 2L && it.timestamp == time.nowMillis() }
        assertFalse(mismatch.matches)
        assertEquals("two missing", mismatch.notes)
    }
}

class FakeSms(var allowed: Boolean = true) : SmsGateway {
    val sent = mutableListOf<Pair<String, String>>()
    override fun canSendDirectly() = allowed
    override fun send(phone: String, message: String): Boolean {
        if (!allowed) return false
        sent += phone to message
        return true
    }
}

@RunWith(RobolectricTestRunner::class)
class OverdueRemindersTest {
    private lateinit var db: AppDatabase
    private val time = FixedTime()
    private val sms = FakeSms()
    private lateinit var reminders: OverdueReminders
    private lateinit var bookings: RoomBookingRepository

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        DemoDataManager(db, time).seedIfEmpty()
        bookings = RoomBookingRepository(db, time)
        reminders = OverdueReminders(bookings, RoomCatalogRepository(db), sms, time)
    }

    @After fun tearDown() = db.close()

    private suspend fun overdue() = bookings.allBookings().first().filter { LateFees.isOverdue(it, time.today()) }

    @Test fun textsEachOverdueCustomerOncePerDay() = runBlocking {
        val late = overdue()
        assertEquals(1, late.size)
        assertEquals(1, reminders.sendDue(late))
        val (phone, text) = sms.sent.single()
        assertEquals("+91 98450 12009", phone)
        assertTrue(text, text.contains("2 days overdue") && text.contains(DateFormats.bookingCode(late.single().id)))
        // Already reminded today: nothing more goes out
        assertEquals(0, reminders.sendDue(overdue()))
        assertEquals(1, sms.sent.size)
    }

    @Test fun withoutPermissionNothingIsSentOrRecorded() = runBlocking {
        sms.allowed = false
        assertEquals(0, reminders.sendDue(overdue()))
        assertNull(overdue().single().overdueSmsAt)
        val draft = (reminders.draft(overdue().single().id) as Outcome.Success).value
        assertEquals("+91 98450 12009", draft.phone)
    }
}



@RunWith(RobolectricTestRunner::class)
class ConcurrentRemindersTest {
    @Test fun manualAndAutomaticSendTogetherTextOnce() = runBlocking {
        val time = FixedTime()
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        DemoDataManager(db, time).seedIfEmpty()
        val bookings = RoomBookingRepository(db, time)
        val sms = FakeSms()
        val reminders = OverdueReminders(bookings, RoomCatalogRepository(db), sms, time)
        val late = bookings.allBookings().first().filter { LateFees.isOverdue(it, time.today()) }
        val auto = async(Dispatchers.Default) { reminders.sendDue(late) }
        val manual = async(Dispatchers.Default) { reminders.sendNow(late.single().id, onlyIfDue = true) }
        auto.await(); assertTrue(manual.await())
        assertEquals(1, sms.sent.size)
        db.close()
    }
}
