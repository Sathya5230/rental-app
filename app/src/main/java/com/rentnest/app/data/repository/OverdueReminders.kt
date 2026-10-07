package com.rentnest.app.data.repository

import com.rentnest.app.domain.DomainError
import com.rentnest.app.domain.Outcome
import com.rentnest.app.domain.SmsGateway
import com.rentnest.app.domain.model.Booking
import com.rentnest.app.domain.repository.BookingRepository
import com.rentnest.app.domain.repository.CatalogRepository
import com.rentnest.app.domain.rules.LateFees
import com.rentnest.app.domain.rules.OverdueMessage
import com.rentnest.app.domain.time.TimeProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** The SMS a customer should get, for when the device can't send it silently. */
data class SmsDraft(val bookingId: Long, val phone: String, val message: String)

@Singleton
class OverdueReminders @Inject constructor(
    private val bookings: BookingRepository,
    private val catalog: CatalogRepository,
    private val sms: SmsGateway,
    private val time: TimeProvider,
) {
    private val lock = Mutex()

    fun canSendDirectly() = sms.canSendDirectly()

    suspend fun draft(bookingId: Long): Outcome<SmsDraft> {
        val b = bookings.booking(bookingId).first() ?: return Outcome.Failure(DomainError.NotFound)
        val today = time.today()
        if (!LateFees.isOverdue(b, today)) return Outcome.Failure(DomainError.NotOverdue)
        val item = catalog.item(b.itemId).first()
        val text = OverdueMessage.sms(b, item?.title ?: "your item", LateFees.daysLate(b.endDate, today), LateFees.fee(b.endDate, today, item?.dailyRate ?: 0))
        val phone = b.contactPhone.ifBlank { catalog.user(b.customerId).first()?.phone.orEmpty() }
        return Outcome.Success(SmsDraft(b.id, phone, text))
    }

    /**
     * Sends the SMS from this device. False if it couldn't, so the caller can hand it to the SMS app.
     * With [onlyIfDue], skips (and returns true) when the customer was already texted today.
     */
    suspend fun sendNow(bookingId: Long, onlyIfDue: Boolean = false): Boolean = lock.withLock {
        if (onlyIfDue) bookings.booking(bookingId).first()?.let { if (!needsReminder(it)) return@withLock true }
        send(bookingId)
    }

    private suspend fun send(bookingId: Long): Boolean {
        val d = (draft(bookingId) as? Outcome.Success)?.value ?: return false
        if (!sms.send(d.phone, d.message)) return false
        bookings.recordOverdueReminder(bookingId)
        return true
    }

    /** Records a reminder the admin sent by hand through the SMS app. */
    suspend fun markSent(bookingId: Long) = bookings.recordOverdueReminder(bookingId)

    /** Texts every overdue customer who hasn't been reminded today. Returns how many were sent. */
    suspend fun sendDue(overdue: List<Booking>): Int = lock.withLock {
        if (!sms.canSendDirectly()) return 0
        // Re-read each booking under the lock: a manual send may have just reminded them.
        overdue.count { b -> bookings.booking(b.id).first()?.takeIf(::needsReminder) != null && send(b.id) }
    }

    /** One reminder per day: never sent, or last sent before today. */
    fun needsReminder(b: Booking): Boolean {
        val sentAt = b.overdueSmsAt ?: return true
        return sentAt < time.startOfTodayMillis()
    }
}
