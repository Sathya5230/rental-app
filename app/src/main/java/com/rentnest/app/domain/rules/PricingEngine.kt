package com.rentnest.app.domain.rules

import com.rentnest.app.domain.DomainError
import com.rentnest.app.domain.Outcome
import com.rentnest.app.domain.model.DateRange
import com.rentnest.app.domain.model.Item

data class PriceBreakdown(
    val days: Int,
    val weeks: Int,
    val extraDays: Int,
    val weeklyCharge: Long,
    val dailyCharge: Long,
    val subtotal: Long,
    val deposit: Long,
) {
    val totalDueNow: Long get() = subtotal + deposit
    /** True when a partial week was rounded up to the (cheaper) weekly rate. */
    val bestPriceApplied: Boolean get() = weeks * 7 + extraDays != days
}

object PricingEngine {
    fun quote(dailyRate: Long, weeklyRate: Long, deposit: Long, range: DateRange): Outcome<PriceBreakdown> {
        if (!range.isValid) return Outcome.Failure(DomainError.InvalidDateRange)
        val weekly = if (weeklyRate > 0) weeklyRate else dailyRate * 7
        val days = range.days
        val weeks = days / 7
        val extra = days % 7
        val raw = weeks * weekly + extra * dailyRate
        val roundedUp = (weeks + 1) * weekly
        val breakdown = if (extra > 0 && roundedUp < raw) {
            PriceBreakdown(days, weeks + 1, 0, roundedUp, 0, roundedUp, deposit)
        } else {
            PriceBreakdown(days, weeks, extra, weeks * weekly, extra * dailyRate, raw, deposit)
        }
        return Outcome.Success(breakdown)
    }

    fun quote(item: Item, range: DateRange): Outcome<PriceBreakdown> =
        quote(item.dailyRate, item.weeklyRate, item.deposit, range)
}
