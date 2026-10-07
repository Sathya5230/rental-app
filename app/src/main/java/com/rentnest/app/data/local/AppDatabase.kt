package com.rentnest.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        UserEntity::class, ProviderEntity::class, CategoryEntity::class, ItemEntity::class, ItemUnitEntity::class,
        BookingEntity::class, HandoverEntity::class, ReviewEntity::class, FavouriteEntity::class, NotificationEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun bookingDao(): BookingDao
    abstract fun notificationDao(): NotificationDao
    abstract fun seedDao(): SeedDao
}
