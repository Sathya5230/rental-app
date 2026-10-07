package com.rentnest.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.rentnest.app.domain.model.*
import java.time.LocalDate

@Entity(tableName = "users")
data class UserEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val phone: String, val isProvider: Boolean)

@Entity(tableName = "providers")
data class ProviderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long, val shopName: String, val rating: Double, val reviewCount: Int,
    val locationText: String, val joinedDate: LocalDate,
)

@Entity(tableName = "categories")
data class CategoryEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val iconKey: String)

@Entity(tableName = "items", indices = [Index("providerId"), Index("categoryId")])
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val providerId: Long, val categoryId: Long, val title: String, val description: String,
    val photos: List<String>, val dailyRate: Long, val weeklyRate: Long, val deposit: Long,
    val specs: Map<String, String>, val lowStockThreshold: Int, val isActive: Boolean,
    val unitValue: Long = 0, val ownership: Ownership = Ownership.OWNED, val vendorId: Long? = null,
    val vendorCostPerDay: Long = 0, val vendorReturnBy: LocalDate? = null,
)

@Entity(tableName = "vendors")
data class VendorEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val phone: String)

@Entity(tableName = "audits", indices = [Index("itemId")])
data class AuditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long, val timestamp: Long, val expected: Int, val counted: Int, val notes: String,
)

@Entity(tableName = "item_units", indices = [Index("itemId")])
data class ItemUnitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long, val tag: String, val condition: UnitCondition, val status: UnitStatus,
)

@Entity(tableName = "bookings", indices = [Index("itemId"), Index("customerId")])
data class BookingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long, val unitId: Long?, val customerId: Long,
    val startDate: LocalDate, val endDate: LocalDate, val status: BookingStatus,
    val subtotal: Long, val deposit: Long, val damageFee: Long, val createdAt: Long, val reviewed: Boolean,
    val contactPhone: String = "", val lateFee: Long = 0, val overdueSmsAt: Long? = null,
    val pickupTransportFee: Long = 0, val dropTransportFee: Long = 0, val cleaningFee: Long = 0,
)

@Entity(tableName = "handovers", indices = [Index("bookingId")])
data class HandoverEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookingId: Long, val type: HandoverType, val checklist: List<String>,
    val conditionAfter: UnitCondition, val notes: String, val damageFee: Long, val timestamp: Long,
)

@Entity(tableName = "reviews", indices = [Index("itemId")])
data class ReviewEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long, val bookingId: Long?, val customerId: Long, val rating: Int, val text: String, val createdAt: Long,
)

@Entity(tableName = "favourites", primaryKeys = ["userId", "itemId"])
data class FavouriteEntity(val userId: Long, val itemId: Long)

@Entity(tableName = "notifications", indices = [Index("recipientUserId")])
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipientUserId: Long, val audience: Audience, val title: String, val body: String,
    val bookingId: Long?, val isRead: Boolean, val createdAt: Long,
)

data class RatingRow(val itemId: Long, val average: Double, val count: Int)
data class CountRow(val itemId: Long, val count: Int)
