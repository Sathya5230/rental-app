package com.rentnest.app.domain.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Material date pickers speak UTC-midnight millis. Never use the device zone here. */
object DateMillis {
    fun toLocalDate(utcMillis: Long): LocalDate = Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()
    fun toUtcMillis(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}
