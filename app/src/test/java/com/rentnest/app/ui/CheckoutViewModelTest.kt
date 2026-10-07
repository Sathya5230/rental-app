package com.rentnest.app.ui

import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rentnest.app.data.FixedTime
import com.rentnest.app.data.local.AppDatabase
import com.rentnest.app.data.repository.RoomBookingRepository
import com.rentnest.app.data.repository.RoomCatalogRepository
import com.rentnest.app.data.seed.DemoDataManager
import com.rentnest.app.domain.DEMO_USER_ID
import com.rentnest.app.ui.customer.CheckoutViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CheckoutViewModelTest {
    private lateinit var db: AppDatabase
    private val time = FixedTime()

    @Before fun setUp() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined) // real-time delay for the simulated payment
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        DemoDataManager(db, time).seedIfEmpty()
    }

    @After fun tearDown() { db.close(); Dispatchers.resetMain() }

    @Test fun payTwiceCreatesOneBooking() = runBlocking {
        val start = time.today().plusDays(20).toEpochDay()
        val handle = SavedStateHandle(mapOf("itemId" to 11L, "startEpochDay" to start, "endEpochDay" to start + 2))
        val vm = CheckoutViewModel(handle, RoomCatalogRepository(db), RoomBookingRepository(db, time))
        vm.pay()
        vm.pay()
        withTimeout(10_000) { vm.state.first { it.bookedId != null } }
        vm.pay()
        val mine = db.bookingDao().bookingsForCustomer(DEMO_USER_ID).first().filter { it.itemId == 11L }
        assertEquals(1, mine.size)
    }
}
