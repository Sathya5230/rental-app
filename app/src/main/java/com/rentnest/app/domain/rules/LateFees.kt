package com.rentnest.app.domain.rules

import com.rentnest.app.domain.model.Booking
import com.rentnest.app.domain.model.BookingStatus
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** What happens to the customer's advance when a rental closes. */
data class Settlement(
    val advance: Long,
    val damageFee: Long,
    val lateFee: Long,
    val cleaningFee: Long = 0,
    /** Transport charge for collecting the item back from the customer. */
    val dropTransportFee: Long = 0,
) {
    val charges: Long get() = damageFee + lateFee + cleaningFee + dropTransportFee
    /** Positive: refund to the customer. Negative: the customer still owes this much. */
    val balance: Long get() = advance - charges
}

object LateFees {
    /** Days past the inclusive end date. A rental due today is not late yet. */
    fun daysLate(endDate: LocalDate, today: LocalDate): Int =
        ChronoUnit.DAYS.between(endDate, today).toInt().coerceAtLeast(0)

    /** Each late day is charged at the item's daily rate. */
    fun fee(endDate: LocalDate, today: LocalDate, dailyRate: Long): Long = daysLate(endDate, today) * dailyRate

    fun isOverdue(b: Booking, today: LocalDate): Boolean = b.status == BookingStatus.ACTIVE && b.endDate.isBefore(today)
}
