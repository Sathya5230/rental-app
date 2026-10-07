package com.rentnest.app.domain.format

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

/** All money is Long paise. Display is ₹ with Indian grouping (1,23,456). */
object MoneyFormatter {
    fun format(paise: Long): String {
        val sign = if (paise < 0) "-" else ""
        val a = abs(paise)
        val grouped = groupIndian((a / 100).toString())
        val p = a % 100
        val body = if (p == 0L) grouped else "$grouped.${p.toString().padStart(2, '0')}"
        return "$sign₹$body"
    }

    /** Short form for chart axes: ₹950, ₹12.5k, ₹1.2L. */
    fun compact(paise: Long): String {
        val rupees = paise / 100.0
        return when {
            rupees < 1_000 -> "₹${rupees.toLong()}"
            rupees < 1_00_000 -> "₹${trim(rupees / 1_000)}k"
            else -> "₹${trim(rupees / 1_00_000)}L"
        }
    }

    /** Parses a rupee amount typed by a user ("1,500", "12.5"). Null if invalid or negative. */
    fun parseRupees(text: String): Long? {
        val t = text.trim().replace(",", "")
        if (t.isEmpty()) return null
        val v = t.toBigDecimalOrNull() ?: return null
        if (v.signum() < 0) return null
        return v.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
    }

    fun toInput(paise: Long): String =
        if (paise % 100 == 0L) (paise / 100).toString()
        else BigDecimal(paise).movePointLeft(2).toPlainString()

    private fun trim(v: Double): String {
        val s = String.format(java.util.Locale.ENGLISH, "%.1f", v)
        return s.removeSuffix(".0")
    }

    private fun groupIndian(digits: String): String {
        if (digits.length <= 3) return digits
        val last3 = digits.takeLast(3)
        val rest = digits.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
        return "$rest,$last3"
    }
}
