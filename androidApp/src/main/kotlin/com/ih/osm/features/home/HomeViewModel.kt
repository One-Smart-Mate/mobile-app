package com.ih.osm.features.home

import androidx.lifecycle.viewModelScope
import com.ih.osm.core.feature.viewmodel.GRViewModel
import com.ih.osm.features.card.domain.repository.CardRepository
import com.ih.osm.features.cards.domain.manager.CardSyncManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class HomeCardSyncState(
    val pendingCount: Long = 0,
    val isSyncing: Boolean = false,
    val completed: Int = 0,
    val total: Int = 0,
)

class HomeViewModel(
    repository: CardRepository,
    private val cardSyncManager: CardSyncManager,
) : GRViewModel<HomeCardSyncState, HomeViewModel.Action, HomeViewModel.Event>(HomeCardSyncState()) {
    sealed interface Action {
        data object SyncPendingCards : Action
    }

    sealed interface Event

    private val syncRequested = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            combine(
                repository.observePendingCount(),
                cardSyncManager.workStatus,
                syncRequested,
            ) { pendingCount, workStatus, requested ->
                HomeCardSyncState(
                    pendingCount = pendingCount,
                    isSyncing = requested || workStatus.isActive,
                    completed = workStatus.completed,
                    total = workStatus.total,
                )
            }.collect { state ->
                setState { state }
            }
        }
        viewModelScope.launch {
            var workStarted = false
            cardSyncManager.workStatus.collect { status ->
                if (status.isActive) {
                    workStarted = true
                } else if (workStarted) {
                    syncRequested.value = false
                    workStarted = false
                }
            }
        }
    }

    override fun processImpl(action: Action) {
        when (action) {
            Action.SyncPendingCards -> syncPendingCards()
        }
    }

    private fun syncPendingCards() {
        if (getStateValue().isSyncing) return
        syncRequested.value = true
        if (!cardSyncManager.enqueueManually()) {
            syncRequested.value = false
        }
    }
}
