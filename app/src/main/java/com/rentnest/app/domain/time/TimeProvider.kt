package com.rentnest.app.domain.time

import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

interface TimeProvider {
    fun today(): LocalDate
    fun nowMillis(): Long
    fun startOfTodayMillis(): Long = today().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun today(): LocalDate = LocalDate.now()
    override fun nowMillis(): Long = System.currentTimeMillis()
}
