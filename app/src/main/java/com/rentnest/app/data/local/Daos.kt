package com.rentnest.app.data.local

import androidx.room.*
import com.rentnest.app.domain.model.Audience
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {
    @Query("SELECT * FROM categories ORDER BY id") fun categories(): Flow<List<CategoryEntity>>
    @Query("SELECT * FROM categories WHERE id = :id") suspend fun categoryOnce(id: Long): CategoryEntity?
    @Query("SELECT * FROM items WHERE isActive = 1 ORDER BY id") fun activeItems(): Flow<List<ItemEntity>>
    @Query("SELECT * FROM items ORDER BY id") fun allItems(): Flow<List<ItemEntity>>
    @Query("SELECT * FROM items WHERE providerId = :providerId ORDER BY id DESC") fun itemsByProvider(providerId: Long): Flow<List<ItemEntity>>
    @Query("SELECT * FROM items WHERE id = :id") fun item(id: Long): Flow<ItemEntity?>
    @Query("SELECT * FROM items WHERE id = :id") suspend fun itemOnce(id: Long): ItemEntity?
    @Insert suspend fun insertItem(item: ItemEntity): Long
    @Update suspend fun updateItem(item: ItemEntity)
    @Query("SELECT * FROM providers ORDER BY rating DESC") fun providers(): Flow<List<ProviderEntity>>
    @Query("SELECT * FROM providers WHERE id = :id") suspend fun providerOnce(id: Long): ProviderEntity?
    @Query("SELECT * FROM providers WHERE userId = :userId LIMIT 1") fun providerForUser(userId: Long): Flow<ProviderEntity?>
    @Query("SELECT * FROM reviews WHERE itemId = :itemId ORDER BY createdAt DESC") fun reviewsForItem(itemId: Long): Flow<List<ReviewEntity>>
    @Query("SELECT itemId, AVG(rating) AS average, COUNT(*) AS count FROM reviews GROUP BY itemId") fun ratingSummaries(): Flow<List<RatingRow>>
    @Query("SELECT itemId, COUNT(*) AS count FROM bookings WHERE status NOT IN ('DECLINED', 'CANCELLED') GROUP BY itemId") fun bookingCounts(): Flow<List<CountRow>>
    @Query("SELECT itemId FROM favourites WHERE userId = :userId") fun favouriteIds(userId: Long): Flow<List<Long>>
    @Query("SELECT COUNT(*) FROM favourites WHERE userId = :userId AND itemId = :itemId") suspend fun isFavourite(userId: Long, itemId: Long): Int
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addFavourite(f: FavouriteEntity)
    @Delete suspend fun removeFavourite(f: FavouriteEntity)
    @Query("SELECT * FROM users WHERE id = :id") fun user(id: Long): Flow<UserEntity?>
    @Query("SELECT * FROM users") fun users(): Flow<List<UserEntity>>
    @Query("SELECT * FROM users WHERE id = :id") suspend fun userOnce(id: Long): UserEntity?
    @Update suspend fun updateUser(u: UserEntity)
    @Query("SELECT * FROM categories WHERE LOWER(name) = LOWER(:name) LIMIT 1") suspend fun categoryByName(name: String): CategoryEntity?
    @Insert suspend fun insertCategory(c: CategoryEntity): Long
    @Query("SELECT * FROM vendors ORDER BY name") fun vendors(): Flow<List<VendorEntity>>
    @Insert suspend fun insertVendor(v: VendorEntity): Long
}

@Dao
interface InventoryDao {
    @Query("SELECT * FROM item_units WHERE itemId = :itemId ORDER BY id") fun unitsForItem(itemId: Long): Flow<List<ItemUnitEntity>>
    @Query("SELECT * FROM item_units WHERE itemId = :itemId ORDER BY id") suspend fun unitsForItemOnce(itemId: Long): List<ItemUnitEntity>
    @Query("SELECT u.* FROM item_units u JOIN items i ON u.itemId = i.id WHERE i.providerId = :providerId ORDER BY u.id")
    fun unitsForProvider(providerId: Long): Flow<List<ItemUnitEntity>>
    @Query("SELECT * FROM item_units") fun allUnits(): Flow<List<ItemUnitEntity>>
    @Query("SELECT * FROM item_units WHERE id = :id") suspend fun unitOnce(id: Long): ItemUnitEntity?
    @Insert suspend fun insertUnit(u: ItemUnitEntity): Long
    @Update suspend fun updateUnit(u: ItemUnitEntity)
    @Query("SELECT * FROM audits ORDER BY timestamp DESC, itemId") fun audits(): Flow<List<AuditEntity>>
    @Insert suspend fun insertAudits(a: List<AuditEntity>)
}

@Dao
interface BookingDao {
    @Query("SELECT * FROM bookings") fun allBookings(): Flow<List<BookingEntity>>
    @Query("SELECT * FROM bookings WHERE itemId = :itemId") fun bookingsForItem(itemId: Long): Flow<List<BookingEntity>>
    @Query("SELECT * FROM bookings WHERE itemId = :itemId") suspend fun bookingsForItemOnce(itemId: Long): List<BookingEntity>
    @Query("SELECT * FROM bookings WHERE customerId = :customerId ORDER BY startDate DESC") fun bookingsForCustomer(customerId: Long): Flow<List<BookingEntity>>
    @Query("SELECT b.* FROM bookings b JOIN items i ON b.itemId = i.id WHERE i.providerId = :providerId ORDER BY b.startDate")
    fun bookingsForProvider(providerId: Long): Flow<List<BookingEntity>>
    @Query("SELECT * FROM bookings WHERE id = :id") fun booking(id: Long): Flow<BookingEntity?>
    @Query("SELECT * FROM bookings WHERE id = :id") suspend fun bookingOnce(id: Long): BookingEntity?
    @Insert suspend fun insertBooking(b: BookingEntity): Long
    @Update suspend fun updateBooking(b: BookingEntity)
    @Insert suspend fun insertHandover(h: HandoverEntity): Long
    @Query("SELECT * FROM handovers WHERE bookingId = :bookingId ORDER BY timestamp") suspend fun handoversOnce(bookingId: Long): List<HandoverEntity>
    @Insert suspend fun insertReview(r: ReviewEntity): Long
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications WHERE recipientUserId = :userId AND audience = :audience ORDER BY createdAt DESC")
    fun forUser(userId: Long, audience: Audience): Flow<List<NotificationEntity>>
    @Query("SELECT COUNT(*) FROM notifications WHERE recipientUserId = :userId AND audience = :audience AND isRead = 0")
    fun unreadCount(userId: Long, audience: Audience): Flow<Int>
    @Query("UPDATE notifications SET isRead = 1 WHERE recipientUserId = :userId AND audience = :audience")
    suspend fun markAllRead(userId: Long, audience: Audience)
    @Insert suspend fun insert(n: NotificationEntity): Long
}

@Dao
interface SeedDao {
    @Query("SELECT COUNT(*) FROM users") suspend fun userCount(): Int
    @Insert suspend fun users(v: List<UserEntity>)
    @Insert suspend fun providers(v: List<ProviderEntity>)
    @Insert suspend fun categories(v: List<CategoryEntity>)
    @Insert suspend fun items(v: List<ItemEntity>)
    @Insert suspend fun units(v: List<ItemUnitEntity>)
    @Insert suspend fun bookings(v: List<BookingEntity>)
    @Insert suspend fun reviews(v: List<ReviewEntity>)
    @Insert suspend fun favourites(v: List<FavouriteEntity>)
    @Insert suspend fun notifications(v: List<NotificationEntity>)
    @Insert suspend fun vendors(v: List<VendorEntity>)
    @Insert suspend fun audits(v: List<AuditEntity>)
}
