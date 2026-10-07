package com.rentnest.app.data.local

import com.rentnest.app.domain.model.*

fun UserEntity.toDomain() = User(id, name, phone, isProvider)
fun ProviderEntity.toDomain() = Provider(id, userId, shopName, rating, reviewCount, locationText, joinedDate)
fun CategoryEntity.toDomain() = Category(id, name, iconKey)
fun ItemEntity.toDomain() = Item(id, providerId, categoryId, title, description, photos, dailyRate, weeklyRate, deposit, specs, lowStockThreshold, isActive,
    unitValue, ownership, vendorId, vendorCostPerDay, vendorReturnBy)
fun Item.toEntity() = ItemEntity(id, providerId, categoryId, title, description, photos, dailyRate, weeklyRate, deposit, specs, lowStockThreshold, isActive,
    unitValue, ownership, vendorId, vendorCostPerDay, vendorReturnBy)
fun VendorEntity.toDomain() = Vendor(id, name, phone)
fun AuditEntity.toDomain() = AuditRecord(id, itemId, timestamp, expected, counted, notes)
fun ItemUnitEntity.toDomain() = ItemUnit(id, itemId, tag, condition, status)
fun ItemUnit.toEntity() = ItemUnitEntity(id, itemId, tag, condition, status)
fun BookingEntity.toDomain() = Booking(id, itemId, unitId, customerId, startDate, endDate, status, subtotal, deposit, damageFee, createdAt, reviewed,
    contactPhone, lateFee, overdueSmsAt, pickupTransportFee, dropTransportFee, cleaningFee)
fun ReviewEntity.toDomain() = Review(id, itemId, bookingId, customerId, rating, text, createdAt)
fun NotificationEntity.toDomain() = AppNotification(id, recipientUserId, audience, title, body, bookingId, isRead, createdAt)
