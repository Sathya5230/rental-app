package com.rentnest.app.domain.repository

import com.rentnest.app.domain.Outcome
import com.rentnest.app.domain.model.*
import com.rentnest.app.domain.rules.ItemDraft
import kotlinx.coroutines.flow.Flow

interface CatalogRepository {
    fun categories(): Flow<List<Category>>
    fun activeItems(): Flow<List<Item>>
    fun allItems(): Flow<List<Item>>
    fun item(id: Long): Flow<Item?>
    fun itemsByProvider(providerId: Long): Flow<List<Item>>
    fun providers(): Flow<List<Provider>>
    fun providerForUser(userId: Long): Flow<Provider?>
    fun reviewsForItem(itemId: Long): Flow<List<Review>>
    fun ratingSummaries(): Flow<Map<Long, RatingSummary>>
    fun bookingCounts(): Flow<Map<Long, Int>>
    fun favouriteIds(userId: Long): Flow<Set<Long>>
    suspend fun toggleFavourite(userId: Long, itemId: Long)
    fun user(id: Long): Flow<User?>
    fun users(): Flow<List<User>>
    /** Creates (id = 0, also adds a first unit) or updates an item. */
    suspend fun saveItem(draft: ItemDraft): Outcome<Long>
    /** Adds a category, or returns the existing one with the same name. */
    suspend fun addCategory(name: String): Outcome<Category>
    fun vendors(): Flow<List<Vendor>>
    suspend fun addVendor(name: String, phone: String): Outcome<Vendor>
    suspend fun updatePhone(userId: Long, phone: String): Outcome<User>
}

interface InventoryRepository {
    fun unitsForItem(itemId: Long): Flow<List<ItemUnit>>
    fun unitsForProvider(providerId: Long): Flow<List<ItemUnit>>
    fun allUnits(): Flow<List<ItemUnit>>
    suspend fun addUnit(itemId: Long): ItemUnit
    suspend fun updateUnit(unit: ItemUnit): Outcome<ItemUnit>
    fun audits(): Flow<List<AuditRecord>>
    /** Records one stock count per entry, all with the same timestamp. */
    suspend fun submitAudit(entries: List<AuditEntry>): Outcome<Int>
}

data class AuditEntry(val itemId: Long, val expected: Int, val counted: Int, val notes: String)

interface BookingRepository {
    fun allBookings(): Flow<List<Booking>>
    fun bookingsForItem(itemId: Long): Flow<List<Booking>>
    fun bookingsForCustomer(customerId: Long): Flow<List<Booking>>
    fun bookingsForProvider(providerId: Long): Flow<List<Booking>>
    fun booking(id: Long): Flow<Booking?>
    /** Creates a REQUESTED booking and notifies the admin. Nothing is reserved until the admin accepts. */
    suspend fun requestBooking(itemId: Long, customerId: Long, range: DateRange, contactPhone: String): Outcome<Booking>
    suspend fun accept(bookingId: Long, unitId: Long): Outcome<Booking>
    suspend fun decline(bookingId: Long): Outcome<Booking>
    suspend fun cancel(bookingId: Long): Outcome<Booking>
    /** [transportFee] is what's charged to deliver the item to the customer at pickup. */
    suspend fun checkOut(bookingId: Long, checklist: List<String>, notes: String, transportFee: Long = 0): Outcome<Booking>
    /** [transportFee] and [cleaningFee] are charged at return and deducted from the advance, like [damageFee]. */
    suspend fun processReturn(
        bookingId: Long, checklist: List<String>, conditionAfter: UnitCondition, notes: String,
        damageFee: Long, transportFee: Long = 0, cleaningFee: Long = 0,
    ): Outcome<Booking>
    suspend fun submitReview(bookingId: Long, rating: Int, text: String): Outcome<Review>
    /** Marks an overdue rental as reminded (SMS sent) and tells the customer in the app. */
    suspend fun recordOverdueReminder(bookingId: Long): Outcome<Booking>
}

interface NotificationRepository {
    fun notifications(userId: Long, audience: Audience): Flow<List<AppNotification>>
    fun unreadCount(userId: Long, audience: Audience): Flow<Int>
    suspend fun markAllRead(userId: Long, audience: Audience)
}

interface SessionRepository {
    val session: Flow<SessionState>
    suspend fun completeOnboarding()
    suspend fun logIn()
    /** Choosing [AppMode.ADMIN] only takes effect while the admin is unlocked. */
    suspend fun chooseMode(mode: AppMode)
    /** Unlocks the admin dashboard for this app session. False if [pin] is wrong. */
    suspend fun unlockAdmin(pin: String): Boolean
    /** Locks the admin dashboard and returns to customer mode. */
    suspend fun lockAdmin()
    suspend fun changeAdminPin(current: String, new: String): Boolean
    suspend fun setTheme(pref: ThemePref)
    suspend fun recordView(itemId: Long)
    suspend fun logOut()
}
