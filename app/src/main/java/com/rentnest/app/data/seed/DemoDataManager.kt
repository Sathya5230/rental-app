package com.rentnest.app.data.seed

import androidx.room.withTransaction
import com.rentnest.app.data.local.AppDatabase
import com.rentnest.app.domain.time.TimeProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DemoDataManager @Inject constructor(
    private val db: AppDatabase,
    private val time: TimeProvider,
) {
    suspend fun seedIfEmpty() {
        if (db.seedDao().userCount() == 0) insertSeed()
    }

    /** Restores the demo data. Run it before each client demo. */
    suspend fun reset() {
        withContext(Dispatchers.IO) { db.clearAllTables() }
        insertSeed()
    }

    private suspend fun insertSeed() {
        val s = SeedData.build(time.today(), time.nowMillis())
        val dao = db.seedDao()
        db.withTransaction {
            dao.users(s.users); dao.providers(s.providers); dao.categories(s.categories); dao.vendors(s.vendors)
            dao.items(s.items); dao.units(s.units); dao.bookings(s.bookings)
            dao.reviews(s.reviews); dao.favourites(s.favourites); dao.notifications(s.notifications); dao.audits(s.audits)
        }
    }
}
