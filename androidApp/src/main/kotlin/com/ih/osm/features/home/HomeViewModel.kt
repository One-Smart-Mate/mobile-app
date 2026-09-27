package com.ih.osm.features.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ih.osm.features.card.domain.repository.CardRepository
import com.ih.osm.features.cards.sync.CardSyncScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeCardSyncState(
    val pendingCount: Long = 0,
    val isSyncing: Boolean = false,
    val completed: Int = 0,
    val total: Int = 0,
)

class HomeViewModel(
    repository: CardRepository,
    private val cardSyncScheduler: CardSyncScheduler,
) : ViewModel() {
    private val syncRequested = MutableStateFlow(false)

    val cardSyncState: StateFlow<HomeCardSyncState> = combine(
        repository.observePendingCount(),
        cardSyncScheduler.workStatus,
        syncRequested,
    ) { pendingCount, workStatus, requested ->
        HomeCardSyncState(
            pendingCount = pendingCount,
            isSyncing = requested || workStatus.isActive,
            completed = workStatus.completed,
            total = workStatus.total,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = HomeCardSyncState(),
    )

    init {
        viewModelScope.launch {
            var workStarted = false
            cardSyncScheduler.workStatus.collect { status ->
                if (status.isActive) {
                    workStarted = true
                } else if (workStarted) {
                    syncRequested.value = false
                    workStarted = false
                }
            }
        }
    }

    fun syncPendingCards() {
        if (cardSyncState.value.isSyncing) return
        syncRequested.value = true
        if (!cardSyncScheduler.enqueueManually()) {
            syncRequested.value = false
        }
    }
}
