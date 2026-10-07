package com.rentnest.app.data.repository

import androidx.room.withTransaction
import com.rentnest.app.data.local.*
import com.rentnest.app.domain.DomainError
import com.rentnest.app.domain.Outcome
import com.rentnest.app.domain.format.DateFormats
import com.rentnest.app.domain.format.MoneyFormatter
import com.rentnest.app.domain.format.PhoneNumbers
import com.rentnest.app.domain.model.*
import com.rentnest.app.domain.repository.BookingRepository
import com.rentnest.app.domain.rules.AvailabilityCalculator
import com.rentnest.app.domain.rules.BookingAction
import com.rentnest.app.domain.rules.BookingStateMachine
import com.rentnest.app.domain.rules.LateFees
import com.rentnest.app.domain.rules.PricingEngine
import com.rentnest.app.domain.time.TimeProvider
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomBookingRepository @Inject constructor(
    private val db: AppDatabase,
    private val time: TimeProvider,
) : BookingRepository {
    private val bookings = db.bookingDao()
    private val catalog = db.catalogDao()
    private val inventory = db.inventoryDao()

    override fun allBookings() = bookings.allBookings().map { l -> l.map { it.toDomain() } }
    override fun bookingsForItem(itemId: Long) = bookings.bookingsForItem(itemId).map { l -> l.map { it.toDomain() } }
    override fun bookingsForCustomer(customerId: Long) = bookings.bookingsForCustomer(customerId).map { l -> l.map { it.toDomain() } }
    override fun bookingsForProvider(providerId: Long) = bookings.bookingsForProvider(providerId).map { l -> l.map { it.toDomain() } }
    override fun booking(id: Long) = bookings.booking(id).map { it?.toDomain() }

    override suspend fun requestBooking(itemId: Long, customerId: Long, range: DateRange, contactPhone: String) =
        db.withTransaction { doRequest(itemId, customerId, range, contactPhone) }

    private suspend fun doRequest(itemId: Long, customerId: Long, range: DateRange, contactPhone: String): Outcome<Booking> {
        if (!range.isValid || range.start.isBefore(time.today())) return Outcome.Failure(DomainError.InvalidDateRange)
        val phone = PhoneNumbers.display(contactPhone) ?: return Outcome.Failure(DomainError.InvalidPhone)
        val item = catalog.itemOnce(itemId)?.toDomain() ?: return Outcome.Failure(DomainError.NotFound)
        val provider = catalog.providerOnce(item.providerId) ?: return Outcome.Failure(DomainError.NotFound)
        if (provider.userId == customerId) return Outcome.Failure(DomainError.OwnListing)
        val units = inventory.unitsForItemOnce(itemId).map { it.toDomain() }
        val existing = bookings.bookingsForItemOnce(itemId).map { it.toDomain() }
        if (!AvailabilityCalculator.isBookable(range, units, existing)) return Outcome.Failure(DomainError.DatesUnavailable)
        val quote = when (val q = PricingEngine.quote(item, range)) {
            is Outcome.Success -> q.value
            is Outcome.Failure -> return q
        }
        val entity = BookingEntity(
            itemId = itemId, unitId = null, customerId = customerId, startDate = range.start, endDate = range.end,
            status = BookingStatus.REQUESTED, subtotal = quote.subtotal, deposit = quote.deposit, damageFee = 0,
            createdAt = time.nowMillis(), reviewed = false, contactPhone = phone,
        )
        val id = bookings.insertBooking(entity)
        val customer = catalog.userOnce(customerId)?.name ?: "A customer"
        notify(provider.userId, Audience.ADMIN, "New rental request", "$customer wants ${item.title} · ${DateFormats.range(range)}. Approve or decline it.", id)
        return Outcome.Success(entity.copy(id = id).toDomain())
    }

    override suspend fun accept(bookingId: Long, unitId: Long) = db.withTransaction {
        transition(bookingId, BookingAction.ACCEPT) { b, next ->
            val units = inventory.unitsForItemOnce(b.itemId).map { it.toDomain() }
            val others = bookings.bookingsForItemOnce(b.itemId).filter { it.id != b.id }.map { it.toDomain() }
            val free = AvailabilityCalculator.freeUnitsFor(b.toDomain().range, units, others)
            if (free.none { it.id == unitId }) return@transition Outcome.Failure(DomainError.NoUnitFree)
            val updated = b.copy(status = next, unitId = unitId)
            bookings.updateBooking(updated)
            notify(b.customerId, Audience.CUSTOMER, "Request approved", "${title(b)} is reserved for ${DateFormats.range(b.toDomain().range)}. Pay the advance of ${MoneyFormatter.format(b.deposit)} at pickup.", b.id)
            Outcome.Success(updated)
        }
    }

    override suspend fun decline(bookingId: Long) = db.withTransaction {
        transition(bookingId, BookingAction.DECLINE) { b, next ->
            val updated = b.copy(status = next)
            bookings.updateBooking(updated)
            notify(b.customerId, Audience.CUSTOMER, "Request declined", "${title(b)} isn't available for those dates. Try other dates.", b.id)
            Outcome.Success(updated)
        }
    }

    override suspend fun cancel(bookingId: Long) = db.withTransaction {
        transition(bookingId, BookingAction.CANCEL) { b, next ->
            val updated = b.copy(status = next)
            bookings.updateBooking(updated)
            ownerOf(b)?.let { notify(it, Audience.ADMIN, "Booking cancelled", "${title(b)} · ${DateFormats.range(b.toDomain().range)} was cancelled", b.id) }
            Outcome.Success(updated)
        }
    }

    override suspend fun checkOut(bookingId: Long, checklist: List<String>, notes: String, transportFee: Long) = db.withTransaction {
        transition(bookingId, BookingAction.CHECK_OUT) { b, next ->
            val unit = b.unitId?.let { inventory.unitOnce(it) } ?: return@transition Outcome.Failure(DomainError.NoUnitFree)
            val transport = transportFee.coerceAtLeast(0)
            val updated = b.copy(status = next, pickupTransportFee = transport)
            bookings.updateBooking(updated)
            bookings.insertHandover(
                HandoverEntity(bookingId = b.id, type = HandoverType.PICKUP, checklist = checklist, conditionAfter = unit.condition,
                    notes = notes, damageFee = 0, timestamp = time.nowMillis()),
            )
            val transportText = if (transport > 0) " Transport charge collected: ${MoneyFormatter.format(transport)}." else ""
            notify(b.customerId, Audience.CUSTOMER, "Rental started", "Enjoy your ${title(b)}!$transportText Return by ${DateFormats.short(b.endDate)}.", b.id)
            Outcome.Success(updated)
        }
    }

    override suspend fun processReturn(
        bookingId: Long, checklist: List<String>, conditionAfter: UnitCondition, notes: String,
        damageFee: Long, transportFee: Long, cleaningFee: Long,
    ) = db.withTransaction {
        transition(bookingId, BookingAction.RETURN) { b, next ->
            val fee = damageFee.coerceAtLeast(0)
            val transport = transportFee.coerceAtLeast(0)
            val cleaning = cleaningFee.coerceAtLeast(0)
            val dailyRate = catalog.itemOnce(b.itemId)?.dailyRate ?: 0
            val late = LateFees.fee(b.endDate, time.today(), dailyRate)
            val updated = b.copy(status = next, damageFee = fee, lateFee = late, dropTransportFee = transport, cleaningFee = cleaning)
            bookings.updateBooking(updated)
            b.unitId?.let { inventory.unitOnce(it) }?.let { unit ->
                val status = if (conditionAfter == UnitCondition.DAMAGED) UnitStatus.MAINTENANCE else unit.status
                inventory.updateUnit(unit.copy(condition = conditionAfter, status = status))
            }
            bookings.insertHandover(
                HandoverEntity(bookingId = b.id, type = HandoverType.RETURN, checklist = checklist, conditionAfter = conditionAfter,
                    notes = notes, damageFee = fee, timestamp = time.nowMillis()),
            )
            val charges = listOfNotNull(
                late.takeIf { it > 0 }?.let { "late fee ${MoneyFormatter.format(it)}" },
                fee.takeIf { it > 0 }?.let { "damage fee ${MoneyFormatter.format(it)}" },
                cleaning.takeIf { it > 0 }?.let { "cleaning charge ${MoneyFormatter.format(it)}" },
                transport.takeIf { it > 0 }?.let { "transport charge ${MoneyFormatter.format(it)}" },
            )
            val feeText = if (charges.isEmpty()) " Your advance is on its way back." else " Deducted from your advance: ${charges.joinToString(", ")}."
            notify(b.customerId, Audience.CUSTOMER, "Rental closed", "Thanks for returning ${title(b)}.$feeText Leave a review?", b.id)
            Outcome.Success(updated)
        }
    }

    override suspend fun submitReview(bookingId: Long, rating: Int, text: String): Outcome<Review> = db.withTransaction {
        doReview(bookingId, rating, text)
    }

    private suspend fun doReview(bookingId: Long, rating: Int, text: String): Outcome<Review> {
        val b = bookings.bookingOnce(bookingId) ?: return Outcome.Failure(DomainError.NotFound)
        if (!BookingStateMachine.canReview(b.toDomain())) return Outcome.Failure(DomainError.NotReviewable)
        if (rating !in 1..5) return Outcome.Failure(DomainError.InvalidRating)
        val entity = ReviewEntity(itemId = b.itemId, bookingId = b.id, customerId = b.customerId, rating = rating, text = text.trim(), createdAt = time.nowMillis())
        val id = bookings.insertReview(entity)
        bookings.updateBooking(b.copy(reviewed = true))
        ownerOf(b)?.let { notify(it, Audience.ADMIN, "New $rating★ review", "${title(b)}: \"${text.trim().take(80)}\"", b.id) }
        return Outcome.Success(entity.copy(id = id).toDomain())
    }

    override suspend fun recordOverdueReminder(bookingId: Long): Outcome<Booking> = db.withTransaction {
        val b = bookings.bookingOnce(bookingId) ?: return@withTransaction Outcome.Failure(DomainError.NotFound)
        val today = time.today()
        if (!LateFees.isOverdue(b.toDomain(), today)) return@withTransaction Outcome.Failure(DomainError.NotOverdue)
        val updated = b.copy(overdueSmsAt = time.nowMillis())
        bookings.updateBooking(updated)
        val rate = catalog.itemOnce(b.itemId)?.dailyRate ?: 0
        notify(
            b.customerId, Audience.CUSTOMER, "Return overdue",
            "${title(b)} was due on ${DateFormats.short(b.endDate)}. Late fee so far: ${MoneyFormatter.format(LateFees.fee(b.endDate, today, rate))}, taken from your advance. Please return it today.",
            b.id,
        )
        Outcome.Success(updated.toDomain())
    }

    private suspend fun transition(
        bookingId: Long,
        action: BookingAction,
        apply: suspend (BookingEntity, BookingStatus) -> Outcome<BookingEntity>,
    ): Outcome<Booking> {
        val b = bookings.bookingOnce(bookingId) ?: return Outcome.Failure(DomainError.NotFound)
        val next = when (val r = BookingStateMachine.next(b.status, action)) {
            is Outcome.Success -> r.value
            is Outcome.Failure -> return r
        }
        return when (val r = apply(b, next)) {
            is Outcome.Success -> Outcome.Success(r.value.toDomain())
            is Outcome.Failure -> r
        }
    }

    private suspend fun title(b: BookingEntity) = catalog.itemOnce(b.itemId)?.title ?: "your item"
    private suspend fun ownerOf(b: BookingEntity): Long? =
        catalog.itemOnce(b.itemId)?.let { catalog.providerOnce(it.providerId)?.userId }

    private suspend fun notify(userId: Long, audience: Audience, title: String, body: String, bookingId: Long) {
        db.notificationDao().insert(
            NotificationEntity(recipientUserId = userId, audience = audience, title = title, body = body,
                bookingId = bookingId, isRead = false, createdAt = time.nowMillis()),
        )
    }
}
