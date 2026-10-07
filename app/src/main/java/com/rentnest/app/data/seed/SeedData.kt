package com.rentnest.app.data.seed

import com.rentnest.app.data.local.*
import com.rentnest.app.data.repository.UnitTagger
import com.rentnest.app.domain.DEMO_USER_ID
import com.rentnest.app.domain.format.DateFormats
import com.rentnest.app.domain.model.*
import com.rentnest.app.domain.rules.PricingEngine
import com.rentnest.app.domain.Outcome
import java.time.LocalDate

data class SeedBundle(
    val users: List<UserEntity>,
    val providers: List<ProviderEntity>,
    val categories: List<CategoryEntity>,
    val items: List<ItemEntity>,
    val units: List<ItemUnitEntity>,
    val bookings: List<BookingEntity>,
    val reviews: List<ReviewEntity>,
    val favourites: List<FavouriteEntity>,
    val notifications: List<NotificationEntity>,
)

/** Deterministic demo data. Booking dates are relative to [today] so the dashboard always looks alive. */
object SeedData {
    private const val DAY = 86_400_000L

    fun build(today: LocalDate, now: Long): SeedBundle {
        val users = listOf(
            UserEntity(1, "Arjun Mehta", "+91 98450 12001", true),
            UserEntity(2, "Kavya Rao", "+91 98450 12002", true),
            UserEntity(3, "Rohan Das", "+91 98450 12003", true),
            UserEntity(4, "Meera Iyer", "+91 98450 12004", true),
            UserEntity(5, "Farhan Ali", "+91 98450 12005", true),
            UserEntity(6, "Neha Kapoor", "+91 98450 12006", true),
            UserEntity(7, "Ishaan Verma", "+91 98450 12007", false),
            UserEntity(8, "Ananya Singh", "+91 98450 12008", false),
            UserEntity(9, "Vikram Nair", "+91 98450 12009", false),
        )
        val providers = listOf(
            ProviderEntity(1, 1, "Arjun's Gear Hub", 4.8, 126, "Indiranagar, Bengaluru", LocalDate.of(2023, 3, 12)),
            ProviderEntity(2, 2, "LensLoop Rentals", 4.9, 312, "Koramangala, Bengaluru", LocalDate.of(2022, 7, 1)),
            ProviderEntity(3, 3, "ToolShed Co.", 4.6, 98, "HSR Layout, Bengaluru", LocalDate.of(2023, 11, 20)),
            ProviderEntity(4, 4, "WildTrail Outfitters", 4.7, 154, "Whitefield, Bengaluru", LocalDate.of(2022, 12, 5)),
            ProviderEntity(5, 5, "PartyPal Events", 4.5, 77, "Jayanagar, Bengaluru", LocalDate.of(2024, 2, 14)),
            ProviderEntity(6, 6, "RideOn Rentals", 4.8, 201, "Malleshwaram, Bengaluru", LocalDate.of(2023, 6, 30)),
        )
        val categories = SEED_CATEGORIES.mapIndexed { i, (name, key) -> CategoryEntity(i + 1L, name, key) }

        val items = SEED_ITEMS.mapIndexed { index, t ->
            val id = index + 1L
            val categoryId = index / 5 + 1L
            val key = SEED_CATEGORIES[index / 5].second
            ItemEntity(
                id = id, providerId = index % 6 + 1L, categoryId = categoryId, title = t.title,
                description = t.description, photos = (0 until 3).map { "$key:${(index + it) % 4}" },
                dailyRate = t.daily * 100, weeklyRate = t.weekly * 100, deposit = t.deposit * 100,
                specs = t.specs.toMap(), lowStockThreshold = 1, isActive = true,
            )
        }

        val units = mutableListOf<ItemUnitEntity>()
        val firstUnit = mutableMapOf<Long, Long>()
        items.forEach { item ->
            val key = SEED_CATEGORIES[(item.categoryId - 1).toInt()].second
            val count = 1 + (item.id % 4).toInt()
            for (n in 1..count) {
                val unitId = units.size + 1L
                if (n == 1) firstUnit[item.id] = unitId
                var condition = when (n) { 1 -> if (item.id % 3 == 0L) UnitCondition.NEW else UnitCondition.GOOD; 3 -> UnitCondition.FAIR; else -> UnitCondition.GOOD }
                var status = UnitStatus.AVAILABLE
                if (item.id == 7L && n == count) { condition = UnitCondition.DAMAGED; status = UnitStatus.MAINTENANCE }
                if (item.id == 22L && n == 3) status = UnitStatus.RETIRED
                units += ItemUnitEntity(unitId, item.id, UnitTagger.tag(key, item.id, n), condition, status)
            }
        }

        data class B(val item: Long, val customer: Long, val from: Long, val to: Long, val status: BookingStatus, val reviewed: Boolean = false, val damage: Long = 0)
        val specs = listOf(
            // The demo user renting from other providers
            B(2, 1, -20, -17, BookingStatus.RETURNED, reviewed = true),
            B(3, 1, -10, -8, BookingStatus.RETURNED),
            B(4, 1, -2, 2, BookingStatus.ACTIVE),
            B(5, 1, 3, 5, BookingStatus.ACCEPTED),
            B(8, 1, 6, 8, BookingStatus.REQUESTED),
            B(9, 1, -5, -4, BookingStatus.CANCELLED),
            B(10, 1, 1, 2, BookingStatus.DECLINED),
            // Customers renting the demo user's shop (provider 1 owns items 1, 7, 13, 19, 25, 31, 37)
            B(1, 7, 2, 4, BookingStatus.REQUESTED),
            B(7, 8, 1, 3, BookingStatus.REQUESTED),
            B(13, 9, 0, 2, BookingStatus.ACCEPTED),
            B(19, 7, -3, 0, BookingStatus.ACTIVE),
            B(25, 8, -1, 4, BookingStatus.ACTIVE),
            B(31, 9, -14, -12, BookingStatus.RETURNED, reviewed = true),
            B(37, 7, -30, -25, BookingStatus.RETURNED, reviewed = true, damage = 1_500),
            B(1, 8, -9, -7, BookingStatus.RETURNED, reviewed = true),
            B(7, 9, -21, -19, BookingStatus.RETURNED),
            B(13, 7, -45, -40, BookingStatus.RETURNED),
            B(19, 8, -60, -58, BookingStatus.RETURNED),
            B(25, 9, -5, -3, BookingStatus.RETURNED),
            B(31, 8, -100, -96, BookingStatus.RETURNED),
            // Marketplace activity elsewhere
            B(2, 8, -40, -38, BookingStatus.RETURNED),
            B(16, 9, -15, -14, BookingStatus.RETURNED),
            B(21, 7, -1, 1, BookingStatus.ACTIVE),
            B(27, 8, 4, 6, BookingStatus.ACCEPTED),
        )
        val bookings = specs.mapIndexed { i, s ->
            val item = items[(s.item - 1).toInt()]
            val range = DateRange(today.plusDays(s.from), today.plusDays(s.to))
            val quote = (PricingEngine.quote(item.dailyRate, item.weeklyRate, item.deposit, range) as Outcome.Success).value
            val assigned = s.status in setOf(BookingStatus.ACCEPTED, BookingStatus.ACTIVE, BookingStatus.RETURNED)
            BookingEntity(
                id = i + 1L, itemId = s.item, unitId = if (assigned) firstUnit.getValue(s.item) else null,
                customerId = s.customer, startDate = range.start, endDate = range.end, status = s.status,
                subtotal = quote.subtotal, deposit = quote.deposit, damageFee = s.damage * 100,
                createdAt = now - (i + 1) * 3 * 3_600_000L, reviewed = s.reviewed,
            )
        }

        val reviews = mutableListOf<ReviewEntity>()
        items.forEach { item ->
            if (item.id % 5 == 0L) return@forEach
            val n = 1 + (item.id % 3).toInt()
            for (k in 0 until n) {
                reviews += ReviewEntity(
                    itemId = item.id, bookingId = null, customerId = 7L + (item.id + k) % 3,
                    rating = listOf(5, 4, 5, 5, 4, 3, 5)[((item.id + k) % 7).toInt()],
                    text = REVIEW_TEXTS[((item.id * 3 + k) % REVIEW_TEXTS.size).toInt()],
                    createdAt = now - (item.id * (k + 1) + 2) * DAY,
                )
            }
        }
        bookings.filter { it.reviewed }.forEach { b ->
            reviews += ReviewEntity(itemId = b.itemId, bookingId = b.id, customerId = b.customerId, rating = 5,
                text = REVIEW_TEXTS[(b.id % REVIEW_TEXTS.size).toInt()], createdAt = now - DAY)
        }

        fun title(id: Long) = items[(id - 1).toInt()].title
        fun booking(item: Long, status: BookingStatus) = bookings.first { it.itemId == item && it.status == status }
        val notifications = listOf(
            notification(Audience.PROVIDER, "New booking request", "Ishaan Verma wants ${title(1)} · ${DateFormats.range(DateRange(booking(1, BookingStatus.REQUESTED).startDate, booking(1, BookingStatus.REQUESTED).endDate))}", booking(1, BookingStatus.REQUESTED).id, false, now - 3_600_000),
            notification(Audience.PROVIDER, "New booking request", "Ananya Singh wants ${title(7)}", booking(7, BookingStatus.REQUESTED).id, false, now - 7_200_000),
            notification(Audience.PROVIDER, "Return due today", "${title(19)} is due back from Ishaan Verma today.", booking(19, BookingStatus.ACTIVE).id, false, now - 10_800_000),
            notification(Audience.PROVIDER, "New 5★ review", "${title(1)}: \"Exactly as described.\"", null, true, now - 2 * DAY),
            notification(Audience.CUSTOMER, "Booking confirmed", "${title(5)} is reserved for you. Pickup in 3 days.", booking(5, BookingStatus.ACCEPTED).id, false, now - 5_400_000),
            notification(Audience.CUSTOMER, "Rental started", "Enjoy your ${title(4)}! Return by ${DateFormats.short(today.plusDays(2))}.", booking(4, BookingStatus.ACTIVE).id, true, now - 2 * DAY),
            notification(Audience.CUSTOMER, "How was it?", "Rate your ${title(3)} rental to help other renters.", booking(3, BookingStatus.RETURNED).id, false, now - 8 * DAY),
        )

        val favourites = listOf(4L, 11L, 21L, 26L).map { FavouriteEntity(DEMO_USER_ID, it) }
        return SeedBundle(users, providers, categories, items, units, bookings, reviews, favourites, notifications)
    }

    private fun notification(audience: Audience, title: String, body: String, bookingId: Long?, read: Boolean, at: Long) =
        NotificationEntity(recipientUserId = DEMO_USER_ID, audience = audience, title = title, body = body,
            bookingId = bookingId, isRead = read, createdAt = at)
}
