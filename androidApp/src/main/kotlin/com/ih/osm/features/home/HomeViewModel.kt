package com.ih.osm.features.home

import androidx.lifecycle.viewModelScope
import com.ih.osm.core.feature.viewmodel.GRViewModel
import com.ih.osm.core.network.NetworkConnectionStatus
import com.ih.osm.core.network.NetworkStatusMonitor
import com.ih.osm.features.auth.domain.model.AuthenticatedUser
import com.ih.osm.features.card.domain.repository.CardRepository
import com.ih.osm.features.cards.domain.manager.CardSyncManager
import com.ih.osm.features.catalog.domain.manager.CatalogSyncManager
import com.ih.osm.features.catalog.domain.model.CatalogKind
import com.ih.osm.features.catalog.domain.model.CatalogSyncStatus
import com.ih.osm.features.settings.domain.preferences.MobileDataSyncPreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

enum class HomeBanner {
    MOBILE_DATA_DISABLED,
    NO_INTERNET,
    CATALOG_SYNC_FAILED,
}

data class HomeCardSyncState(
    val pendingCount: Long = 0,
    val isSyncing: Boolean = false,
    val completed: Int = 0,
    val total: Int = 0,
    val networkStatus: NetworkConnectionStatus = NetworkConnectionStatus.OFFLINE,
    val catalogSyncStatus: CatalogSyncStatus = CatalogSyncStatus.Idle,
    val isCatalogSyncRequesting: Boolean = false,
    val showCatalogSyncConfirmation: Boolean = false,
    val banner: HomeBanner? = null,
)

class HomeViewModel(
    repository: CardRepository,
    private val cardSyncManager: CardSyncManager,
    private val catalogSyncManager: CatalogSyncManager,
    private val networkStatusMonitor: NetworkStatusMonitor,
    private val syncPreferences: MobileDataSyncPreferences,
) : GRViewModel<HomeCardSyncState, HomeViewModel.Action, HomeViewModel.Event>(HomeCardSyncState()) {
    sealed interface Action {
        data class BindUser(val user: AuthenticatedUser) : Action
        data object SyncPendingCards : Action
        data object RequestCatalogSync : Action
        data object DismissCatalogSyncConfirmation : Action
        data object ConfirmCatalogSync : Action
        data object DismissBanner : Action
    }

    sealed interface Event

    private val syncRequested = MutableStateFlow(false)
    private var currentUser: AuthenticatedUser? = null
    private var catalogObservationJob: Job? = null

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
            }.collect { cardSyncState ->
                setState {
                    copy(
                        pendingCount = cardSyncState.pendingCount,
                        isSyncing = cardSyncState.isSyncing,
                        completed = cardSyncState.completed,
                        total = cardSyncState.total,
                    )
                }
            }
        }
        viewModelScope.launch {
            networkStatusMonitor.status.collect { status ->
                setState { copy(networkStatus = status) }
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
            is Action.BindUser -> bindUser(action.user)
            Action.SyncPendingCards -> syncPendingCards()
            Action.RequestCatalogSync -> requestCatalogSync()
            Action.DismissCatalogSyncConfirmation -> setState {
                copy(showCatalogSyncConfirmation = false)
            }
            Action.ConfirmCatalogSync -> confirmCatalogSync()
            Action.DismissBanner -> setState { copy(banner = null) }
        }
    }

    private fun bindUser(user: AuthenticatedUser) {
        val scope = user.sites.map { it.id }.distinct().sorted()
        val currentScope = currentUser?.sites?.map { it.id }?.distinct()?.sorted()
        if (currentUser?.id == user.id && currentScope == scope) return

        currentUser = user
        catalogObservationJob?.cancel()
        catalogObservationJob = viewModelScope.launch {
            catalogSyncManager.observe(user).collect { status ->
                setState {
                    val wasActive = catalogSyncStatus.isActive()
                    copy(
                        catalogSyncStatus = status,
                        isCatalogSyncRequesting = when {
                            status is CatalogSyncStatus.Failed -> false
                            status.isActive() -> false
                            wasActive -> false
                            else -> isCatalogSyncRequesting
                        },
                    )
                }
            }
        }
    }

    private fun requestCatalogSync() {
        if (getStateValue().isCatalogSyncBusy()) return
        setState { copy(showCatalogSyncConfirmation = true, banner = null) }
    }

    private fun confirmCatalogSync() {
        val user = currentUser ?: return
        val state = getStateValue()
        if (state.isCatalogSyncBusy()) return

        setState { copy(showCatalogSyncConfirmation = false, banner = null) }
        when (state.networkStatus) {
            NetworkConnectionStatus.WIFI_CONNECTED -> enqueueCatalogSync(user)
            NetworkConnectionStatus.CELLULAR_CONNECTED -> {
                if (syncPreferences.allowMobileData.value) {
                    enqueueCatalogSync(user)
                } else {
                    setState { copy(banner = HomeBanner.MOBILE_DATA_DISABLED) }
                }
            }
            NetworkConnectionStatus.WIFI_NO_INTERNET,
            NetworkConnectionStatus.CELLULAR_NO_INTERNET,
            NetworkConnectionStatus.OFFLINE,
            -> setState { copy(banner = HomeBanner.NO_INTERNET) }
        }
    }

    private fun enqueueCatalogSync(user: AuthenticatedUser) {
        setState { copy(isCatalogSyncRequesting = true) }
        val enqueued = catalogSyncManager.enqueueManual(user, CatalogKind.entries.toSet())
        if (!enqueued) {
            setState {
                copy(
                    isCatalogSyncRequesting = false,
                    banner = HomeBanner.CATALOG_SYNC_FAILED,
                )
            }
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

fun HomeCardSyncState.isCatalogSyncBusy(): Boolean =
    isCatalogSyncRequesting || catalogSyncStatus.isActive()

private fun CatalogSyncStatus.isActive(): Boolean =
    this is CatalogSyncStatus.WaitingForNetwork || this is CatalogSyncStatus.Downloading
