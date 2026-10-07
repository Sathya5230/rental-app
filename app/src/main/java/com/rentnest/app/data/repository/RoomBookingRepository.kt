package com.rentnest.app.data.repository

import androidx.room.withTransaction
import com.rentnest.app.data.local.*
import com.rentnest.app.domain.DomainError
import com.rentnest.app.domain.Outcome
import com.rentnest.app.domain.format.DateFormats
import com.rentnest.app.domain.format.MoneyFormatter
import com.rentnest.app.domain.model.*
import com.rentnest.app.domain.repository.BookingRepository
import com.rentnest.app.domain.rules.AvailabilityCalculator
import com.rentnest.app.domain.rules.BookingAction
import com.rentnest.app.domain.rules.BookingStateMachine
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

    override suspend fun requestBooking(itemId: Long, customerId: Long, range: DateRange) =
        db.withTransaction { doRequest(itemId, customerId, range) }

    private suspend fun doRequest(itemId: Long, customerId: Long, range: DateRange): Outcome<Booking> {
        if (!range.isValid || range.start.isBefore(time.today())) return Outcome.Failure(DomainError.InvalidDateRange)
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
            createdAt = time.nowMillis(), reviewed = false,
        )
        val id = bookings.insertBooking(entity)
        notify(provider.userId, Audience.PROVIDER, "New booking request", "${item.title} · ${DateFormats.range(range)}", id)
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
            notify(b.customerId, Audience.CUSTOMER, "Booking confirmed", "${title(b)} is reserved for ${DateFormats.range(b.toDomain().range)}", b.id)
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
            ownerOf(b)?.let { notify(it, Audience.PROVIDER, "Booking cancelled", "${title(b)} · ${DateFormats.range(b.toDomain().range)} was cancelled", b.id) }
            Outcome.Success(updated)
        }
    }

    override suspend fun checkOut(bookingId: Long, checklist: List<String>, notes: String) = db.withTransaction {
        transition(bookingId, BookingAction.CHECK_OUT) { b, next ->
            val unit = b.unitId?.let { inventory.unitOnce(it) } ?: return@transition Outcome.Failure(DomainError.NoUnitFree)
            val updated = b.copy(status = next)
            bookings.updateBooking(updated)
            bookings.insertHandover(
                HandoverEntity(bookingId = b.id, type = HandoverType.PICKUP, checklist = checklist, conditionAfter = unit.condition,
                    notes = notes, damageFee = 0, timestamp = time.nowMillis()),
            )
            notify(b.customerId, Audience.CUSTOMER, "Rental started", "Enjoy your ${title(b)}! Return by ${DateFormats.short(b.endDate)}.", b.id)
            Outcome.Success(updated)
        }
    }

    override suspend fun processReturn(
        bookingId: Long, checklist: List<String>, conditionAfter: UnitCondition, notes: String, damageFee: Long,
    ) = db.withTransaction {
        transition(bookingId, BookingAction.RETURN) { b, next ->
            val fee = damageFee.coerceAtLeast(0)
            val updated = b.copy(status = next, damageFee = fee)
            bookings.updateBooking(updated)
            b.unitId?.let { inventory.unitOnce(it) }?.let { unit ->
                val status = if (conditionAfter == UnitCondition.DAMAGED) UnitStatus.MAINTENANCE else unit.status
                inventory.updateUnit(unit.copy(condition = conditionAfter, status = status))
            }
            bookings.insertHandover(
                HandoverEntity(bookingId = b.id, type = HandoverType.RETURN, checklist = checklist, conditionAfter = conditionAfter,
                    notes = notes, damageFee = fee, timestamp = time.nowMillis()),
            )
            val feeText = if (fee > 0) " A damage fee of ${MoneyFormatter.format(fee)} was applied." else " Your deposit is on its way back."
            notify(b.customerId, Audience.CUSTOMER, "Return complete", "Thanks for returning ${title(b)}.$feeText Leave a review?", b.id)
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
        ownerOf(b)?.let { notify(it, Audience.PROVIDER, "New $rating★ review", "${title(b)}: \"${text.trim().take(80)}\"", b.id) }
        return Outcome.Success(entity.copy(id = id).toDomain())
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
