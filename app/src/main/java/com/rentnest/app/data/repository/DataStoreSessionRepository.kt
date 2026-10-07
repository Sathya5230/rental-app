package com.rentnest.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.rentnest.app.domain.model.AppMode
import com.rentnest.app.domain.model.SessionState
import com.rentnest.app.domain.model.ThemePref
import com.rentnest.app.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DataStoreSessionRepository @Inject constructor(
    private val store: DataStore<Preferences>,
) : SessionRepository {
    override val session: Flow<SessionState> = store.data.map { p ->
        SessionState(
            onboarded = p[ONBOARDED] ?: false,
            loggedIn = p[LOGGED_IN] ?: false,
            modeChosen = p[MODE] != null,
            mode = AppMode.entries.firstOrNull { it.name == p[MODE] } ?: AppMode.CUSTOMER,
            theme = ThemePref.entries.firstOrNull { it.name == p[THEME] } ?: ThemePref.SYSTEM,
            recentItemIds = p[RECENT]?.split(",")?.mapNotNull { it.toLongOrNull() } ?: emptyList(),
        )
    }

    override suspend fun completeOnboarding() { store.edit { it[ONBOARDED] = true } }
    override suspend fun logIn() { store.edit { it[LOGGED_IN] = true } }
    override suspend fun chooseMode(mode: AppMode) { store.edit { it[MODE] = mode.name } }
    override suspend fun setTheme(pref: ThemePref) { store.edit { it[THEME] = pref.name } }
    override suspend fun recordView(itemId: Long) {
        store.edit { p ->
            val current = p[RECENT]?.split(",")?.mapNotNull { it.toLongOrNull() } ?: emptyList()
            p[RECENT] = (listOf(itemId) + current.filter { it != itemId }).take(10).joinToString(",")
        }
    }
    override suspend fun logOut() { store.edit { it.remove(LOGGED_IN); it.remove(MODE) } }

    private companion object {
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val LOGGED_IN = booleanPreferencesKey("logged_in")
        val MODE = stringPreferencesKey("mode")
        val THEME = stringPreferencesKey("theme")
        val RECENT = stringPreferencesKey("recent_items")
    }
}
