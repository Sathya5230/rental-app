package com.rentnest.app.domain.model

import java.time.LocalDate

enum class UnitCondition { NEW, GOOD, FAIR, DAMAGED }
enum class UnitStatus { AVAILABLE, MAINTENANCE, RETIRED }
enum class BookingStatus { REQUESTED, ACCEPTED, ACTIVE, RETURNED, DECLINED, CANCELLED }
enum class HandoverType { PICKUP, RETURN }
enum class Audience { CUSTOMER, PROVIDER }

data class User(val id: Long, val name: String, val phone: String, val isProvider: Boolean)

data class Provider(
    val id: Long,
    val userId: Long,
    val shopName: String,
    val rating: Double,
    val reviewCount: Int,
    val locationText: String,
    val joinedDate: LocalDate,
)

/** [iconKey] doubles as the art key used by ItemArt (e.g. "cameras"). */
data class Category(val id: Long, val name: String, val iconKey: String)

data class Item(
    val id: Long,
    val providerId: Long,
    val categoryId: Long,
    val title: String,
    val description: String,
    val photos: List<String>,
    val dailyRate: Long,
    val weeklyRate: Long,
    val deposit: Long,
    val specs: Map<String, String>,
    val lowStockThreshold: Int = 1,
    val isActive: Boolean = true,
)

data class ItemUnit(
    val id: Long,
    val itemId: Long,
    val tag: String,
    val condition: UnitCondition,
    val status: UnitStatus,
)

data class Booking(
    val id: Long,
    val itemId: Long,
    val unitId: Long?,
    val customerId: Long,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val status: BookingStatus,
    val subtotal: Long,
    val deposit: Long,
    val damageFee: Long = 0,
    val createdAt: Long,
    val reviewed: Boolean = false,
) {
    val range: DateRange get() = DateRange(startDate, endDate)
    val total: Long get() = subtotal + deposit
}

data class HandoverRecord(
    val id: Long,
    val bookingId: Long,
    val type: HandoverType,
    val checklist: List<String>,
    val conditionAfter: UnitCondition,
    val notes: String,
    val damageFee: Long,
    val timestamp: Long,
)

data class Review(
    val id: Long,
    val itemId: Long,
    val bookingId: Long?,
    val customerId: Long,
    val rating: Int,
    val text: String,
    val createdAt: Long,
)

data class AppNotification(
    val id: Long,
    val recipientUserId: Long,
    val audience: Audience,
    val title: String,
    val body: String,
    val bookingId: Long?,
    val isRead: Boolean,
    val createdAt: Long,
)

data class RatingSummary(val average: Double, val count: Int)
