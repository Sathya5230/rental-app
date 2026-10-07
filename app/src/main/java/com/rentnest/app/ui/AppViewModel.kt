package com.rentnest.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentnest.app.data.seed.DemoDataManager
import com.rentnest.app.domain.ADMIN_USER_ID
import com.rentnest.app.domain.model.BookingStatus
import com.rentnest.app.domain.model.SessionState
import com.rentnest.app.domain.repository.BookingRepository
import com.rentnest.app.domain.repository.CatalogRepository
import com.rentnest.app.domain.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AppUiState {
    data object Loading : AppUiState
    data class Ready(val session: SessionState, val pendingRequests: Int) : AppUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AppViewModel @Inject constructor(
    session: SessionRepository,
    catalog: CatalogRepository,
    bookings: BookingRepository,
    demo: DemoDataManager,
) : ViewModel() {
    private val seeded = MutableStateFlow(false)

    init {
        viewModelScope.launch { demo.seedIfEmpty(); seeded.value = true }
    }

    private val pending = catalog.providerForUser(ADMIN_USER_ID).flatMapLatest { p ->
        if (p == null) flowOf(0) else bookings.bookingsForProvider(p.id).map { l -> l.count { it.status == BookingStatus.REQUESTED } }
    }

    val state: StateFlow<AppUiState> = seeded.flatMapLatest { ready ->
        if (!ready) flowOf(AppUiState.Loading)
        else combine(session.session, pending) { s, p -> AppUiState.Ready(s, p) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppUiState.Loading)
}
