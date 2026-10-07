package com.rentnest.app.domain.model

import java.time.LocalDate

enum class UnitCondition { NEW, GOOD, FAIR, DAMAGED }
enum class UnitStatus { AVAILABLE, MAINTENANCE, RETIRED }
enum class BookingStatus { REQUESTED, ACCEPTED, ACTIVE, RETURNED, DECLINED, CANCELLED }
enum class HandoverType { PICKUP, RETURN }
enum class Audience { CUSTOMER, ADMIN }

/** Whether the store owns an item or has borrowed it from a vendor. */
enum class Ownership { OWNED, BORROWED }

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

data class Vendor(val id: Long, val name: String, val phone: String)

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
    /** Purchase or replacement value of one unit, used for inventory net worth. */
    val unitValue: Long = 0,
    val ownership: Ownership = Ownership.OWNED,
    val vendorId: Long? = null,
    /** What the store pays the vendor per unit per day for a borrowed item. */
    val vendorCostPerDay: Long = 0,
    val vendorReturnBy: LocalDate? = null,
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
    val contactPhone: String = "",
    val lateFee: Long = 0,
    /** When the customer was last sent an overdue SMS, or null if never. */
    val overdueSmsAt: Long? = null,
    /** Charged at pickup, for delivering the item to the customer. */
    val pickupTransportFee: Long = 0,
    /** Charged at return, for collecting the item back from the customer. */
    val dropTransportFee: Long = 0,
    /** Charged at return, for cleaning or servicing the item before it's listed again. */
    val cleaningFee: Long = 0,
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

/** One stock count of an item: units expected in the store vs. units physically found. */
data class AuditRecord(
    val id: Long,
    val itemId: Long,
    val timestamp: Long,
    val expected: Int,
    val counted: Int,
    val notes: String,
) {
    val matches: Boolean get() = expected == counted
}

data class RatingSummary(val average: Double, val count: Int)
