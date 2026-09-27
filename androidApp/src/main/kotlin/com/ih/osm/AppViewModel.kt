package com.ih.osm

import androidx.lifecycle.viewModelScope
import com.ih.osm.core.feature.viewmodel.GRViewModel
import com.ih.osm.features.auth.domain.session.SessionRepository
import com.ih.osm.features.auth.domain.session.SessionStatus
import com.ih.osm.features.cards.domain.manager.CardSyncManager
import com.ih.osm.features.notifications.data.firebase.FirebaseTokenRegistrationScheduler
import kotlinx.coroutines.launch

class AppViewModel(
    private val sessionRepository: SessionRepository,
    private val cardSyncManager: CardSyncManager,
    private val firebaseTokenScheduler: FirebaseTokenRegistrationScheduler,
) : GRViewModel<AppViewModel.UiState, AppViewModel.Action, AppViewModel.Event>(UiState()) {
    data class UiState(val session: SessionStatus = SessionStatus.Unknown)

    sealed interface Action {
        data object RestoreSession : Action
    }

    sealed interface Event

    private var initializedUserId: Long? = null

    init {
        viewModelScope.launch {
            sessionRepository.status.collect { status ->
                setState { copy(session = status) }
                val authenticated = status as? SessionStatus.Authenticated
                if (authenticated != null && initializedUserId != authenticated.user.id) {
                    initializedUserId = authenticated.user.id
                    firebaseTokenScheduler.ensureRegistered()
                    cardSyncManager.enqueueRemoteChanges(
                        authenticated.user.sites.map { site -> site.id },
                    )
                } else if (status is SessionStatus.Unauthenticated) {
                    initializedUserId = null
                }
            }
        }
        process(Action.RestoreSession)
    }

    override fun processImpl(action: Action) {
        when (action) {
            Action.RestoreSession -> viewModelScope.launch { sessionRepository.restore() }
        }
    }
}
