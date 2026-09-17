package com.ih.osm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ih.osm.features.auth.domain.session.SessionRepository
import com.ih.osm.features.auth.domain.session.SessionStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(
    sessionRepository: SessionRepository,
) : ViewModel() {
    data class UiState(val session: SessionStatus = SessionStatus.Unknown)

    val uiState = sessionRepository.status
        .map(::UiState)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = UiState(),
        )

    init {
        viewModelScope.launch { sessionRepository.restore() }
    }
}
