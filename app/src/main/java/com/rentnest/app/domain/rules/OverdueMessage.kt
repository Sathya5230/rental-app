package com.rentnest.app.domain.rules

import com.rentnest.app.domain.format.DateFormats
import com.rentnest.app.domain.format.MoneyFormatter
import com.rentnest.app.domain.model.Booking

object OverdueMessage {
    fun sms(b: Booking, itemTitle: String, daysLate: Int, lateFee: Long): String =
        "RentNest: Your rental of $itemTitle (${DateFormats.bookingCode(b.id)}) was due back on ${DateFormats.full(b.endDate)} " +
            "and is ${DateFormats.days(daysLate)} overdue. A late fee of ${MoneyFormatter.format(lateFee)} so far will be " +
            "deducted from your advance of ${MoneyFormatter.format(b.deposit)}. Please return it today."
}
