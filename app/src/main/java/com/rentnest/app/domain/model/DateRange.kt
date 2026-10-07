package com.rentnest.app.domain.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Inclusive date range. */
data class DateRange(val start: LocalDate, val end: LocalDate) {
    val isValid: Boolean get() = !end.isBefore(start)
    val days: Int get() = if (isValid) (ChronoUnit.DAYS.between(start, end) + 1).toInt() else 0
    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)
    fun overlaps(other: DateRange): Boolean = !start.isAfter(other.end) && !other.start.isAfter(end)
    fun dates(): List<LocalDate> = (0 until days).map { start.plusDays(it.toLong()) }
}
