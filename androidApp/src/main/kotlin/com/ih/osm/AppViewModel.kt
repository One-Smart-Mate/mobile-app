package com.ih.osm

import androidx.lifecycle.viewModelScope
import com.ih.osm.core.feature.viewmodel.GRViewModel
import com.ih.osm.features.auth.domain.session.SessionRepository
import com.ih.osm.features.auth.domain.session.SessionStatus
import kotlinx.coroutines.launch

class AppViewModel(
    private val sessionRepository: SessionRepository,
) : GRViewModel<AppViewModel.UiState, AppViewModel.Action, AppViewModel.Event>(UiState()) {
    data class UiState(val session: SessionStatus = SessionStatus.Unknown)

    sealed interface Action {
        data object RestoreSession : Action
    }

    sealed interface Event

    init {
        viewModelScope.launch {
            sessionRepository.status.collect { status -> setState { copy(session = status) } }
        }
        process(Action.RestoreSession)
    }

    override fun processImpl(action: Action) {
        when (action) {
            Action.RestoreSession -> viewModelScope.launch { sessionRepository.restore() }
        }
    }
}
