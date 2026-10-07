package com.rentnest.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.rentnest.app.domain.DEFAULT_ADMIN_PIN
import com.rentnest.app.domain.model.AppMode
import com.rentnest.app.domain.model.SessionState
import com.rentnest.app.domain.model.ThemePref
import com.rentnest.app.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreSessionRepository @Inject constructor(
    private val store: DataStore<Preferences>,
) : SessionRepository {
    /** Held in memory only, so the admin dashboard locks again whenever the app process restarts. */
    private val adminUnlocked = MutableStateFlow(false)

    override val session: Flow<SessionState> = combine(store.data, adminUnlocked) { p, unlocked ->
        val stored = AppMode.entries.firstOrNull { it.name == p[MODE] } ?: AppMode.CUSTOMER
        SessionState(
            onboarded = p[ONBOARDED] ?: false,
            loggedIn = p[LOGGED_IN] ?: false,
            modeChosen = p[MODE] != null,
            mode = if (stored == AppMode.ADMIN && !unlocked) AppMode.CUSTOMER else stored,
            theme = ThemePref.entries.firstOrNull { it.name == p[THEME] } ?: ThemePref.SYSTEM,
            recentItemIds = p[RECENT]?.split(",")?.mapNotNull { it.toLongOrNull() } ?: emptyList(),
        )
    }

    override suspend fun completeOnboarding() { store.edit { it[ONBOARDED] = true } }
    override suspend fun logIn() { store.edit { it[LOGGED_IN] = true } }
    override suspend fun chooseMode(mode: AppMode) {
        if (mode == AppMode.ADMIN && !adminUnlocked.value) return
        store.edit { it[MODE] = mode.name }
    }
    override suspend fun setTheme(pref: ThemePref) { store.edit { it[THEME] = pref.name } }
    override suspend fun recordView(itemId: Long) {
        store.edit { p ->
            val current = p[RECENT]?.split(",")?.mapNotNull { it.toLongOrNull() } ?: emptyList()
            p[RECENT] = (listOf(itemId) + current.filter { it != itemId }).take(10).joinToString(",")
        }
    }
    override suspend fun logOut() {
        adminUnlocked.value = false
        store.edit { it.remove(LOGGED_IN); it.remove(MODE) }
    }

    override suspend fun unlockAdmin(pin: String): Boolean {
        if (!pinMatches(pin)) return false
        adminUnlocked.value = true
        return true
    }

    override suspend fun lockAdmin() {
        adminUnlocked.value = false
        store.edit { it[MODE] = AppMode.CUSTOMER.name }
    }

    override suspend fun changeAdminPin(current: String, new: String): Boolean {
        if (!pinMatches(current) || !isValidPin(new)) return false
        store.edit { it[ADMIN_PIN] = hash(new) }
        return true
    }

    private suspend fun pinMatches(pin: String): Boolean =
        hash(pin) == (store.data.first()[ADMIN_PIN] ?: hash(DEFAULT_ADMIN_PIN))

    private fun hash(pin: String): String =
        MessageDigest.getInstance("SHA-256").digest("rentnest-admin:$pin".toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        fun isValidPin(pin: String) = pin.length in 4..6 && pin.all(Char::isDigit)

        private val ONBOARDED = booleanPreferencesKey("onboarded")
        private val LOGGED_IN = booleanPreferencesKey("logged_in")
        private val MODE = stringPreferencesKey("mode")
        private val THEME = stringPreferencesKey("theme")
        private val RECENT = stringPreferencesKey("recent_items")
        private val ADMIN_PIN = stringPreferencesKey("admin_pin_sha256")
    }
}
