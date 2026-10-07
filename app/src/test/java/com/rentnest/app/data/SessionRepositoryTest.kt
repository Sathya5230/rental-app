package com.rentnest.app.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.rentnest.app.data.repository.DataStoreSessionRepository
import com.rentnest.app.domain.DEFAULT_ADMIN_PIN
import com.rentnest.app.domain.model.AppMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SessionRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private fun repo() = DataStoreSessionRepository(PreferenceDataStoreFactory.create(scope = scope) { tmp.root.resolve("session.preferences_pb") })

    @After fun tearDown() = scope.cancel()

    @Test fun adminModeNeedsTheRightPin() = runBlocking {
        val r = repo()
        r.chooseMode(AppMode.ADMIN)
        assertEquals(AppMode.CUSTOMER, r.session.first().mode)
        assertFalse(r.unlockAdmin("0000"))
        assertEquals(AppMode.CUSTOMER, r.session.first().mode)
        assertTrue(r.unlockAdmin(DEFAULT_ADMIN_PIN))
        r.chooseMode(AppMode.ADMIN)
        assertEquals(AppMode.ADMIN, r.session.first().mode)
        r.lockAdmin()
        assertEquals(AppMode.CUSTOMER, r.session.first().mode)
    }

    @Test fun changedPinReplacesTheDefault() = runBlocking {
        val r = repo()
        assertFalse(r.changeAdminPin("9999", "4321"))
        assertFalse(r.changeAdminPin(DEFAULT_ADMIN_PIN, "12")) // too short
        assertTrue(r.changeAdminPin(DEFAULT_ADMIN_PIN, "4321"))
        assertFalse(r.unlockAdmin(DEFAULT_ADMIN_PIN))
        assertTrue(r.unlockAdmin("4321"))
    }
}
