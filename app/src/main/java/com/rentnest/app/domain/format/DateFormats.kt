package com.rentnest.app.domain.format

import com.rentnest.app.domain.model.DateRange
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateFormats {
    private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val dayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    fun short(date: LocalDate): String = date.format(dayMonth)
    fun full(date: LocalDate): String = date.format(dayMonthYear)
    fun range(r: DateRange): String = if (r.start == r.end) short(r.start) else "${short(r.start)} – ${short(r.end)}"
    fun bookingCode(id: Long): String = "RN-" + id.toString().padStart(5, '0')
    fun days(n: Int): String = if (n == 1) "1 day" else "$n days"

    fun relative(thenMillis: Long, nowMillis: Long): String {
        val mins = (nowMillis - thenMillis) / 60_000
        return when {
            mins < 1 -> "Just now"
            mins < 60 -> "${mins}m ago"
            mins < 1_440 -> "${mins / 60}h ago"
            else -> "${mins / 1_440}d ago"
        }
    }
}
