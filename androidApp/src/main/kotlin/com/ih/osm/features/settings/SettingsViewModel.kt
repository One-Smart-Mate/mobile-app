package com.ih.osm.features.settings

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
import com.ih.osm.features.permissions.domain.manager.PermissionManager
import com.ih.osm.features.permissions.domain.model.AppPermissionStatus
import com.ih.osm.features.settings.domain.manager.LogoutManager
import com.ih.osm.features.settings.domain.preferences.MobileDataSyncPreferences
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class SettingsBanner {
    MOBILE_DATA_DISABLED,
    NO_INTERNET,
    CATALOG_SYNC_FAILED,
}

data class SettingsUiState(
    val pendingCardCount: Long = 0,
    val missingPermissionCount: Int = 0,
    val allowMobileData: Boolean = true,
    val showAccountInformation: Boolean = false,
    val showPermissions: Boolean = false,
    val showPendingLogoutWarning: Boolean = false,
    val isLoggingOut: Boolean = false,
    val logoutFailed: Boolean = false,
    val networkStatus: NetworkConnectionStatus = NetworkConnectionStatus.OFFLINE,
    val catalogSyncStatus: CatalogSyncStatus = CatalogSyncStatus.Idle,
    val isCatalogSyncRequesting: Boolean = false,
    val showCatalogSyncConfirmation: Boolean = false,
    val catalogBanner: SettingsBanner? = null,
)

class SettingsViewModel(
    repository: CardRepository,
    private val permissionManager: PermissionManager,
    private val syncPreferences: MobileDataSyncPreferences,
    private val cardSyncManager: CardSyncManager,
    private val catalogSyncManager: CatalogSyncManager,
    private val networkStatusMonitor: NetworkStatusMonitor,
    private val logoutManager: LogoutManager,
) : GRViewModel<SettingsUiState, SettingsViewModel.Action, SettingsViewModel.Event>(
    initialState = SettingsUiState(
        missingPermissionCount = permissionManager.missingPermissionCount(),
        allowMobileData = syncPreferences.allowMobileData.value,
    ),
) {
    sealed interface Action {
        data object ShowAccountInformation : Action
        data object DismissAccountInformation : Action
        data object ShowPermissions : Action
        data object DismissPermissions : Action
        data object RefreshPermissions : Action
        data class SetAllowMobileData(val allow: Boolean) : Action
        data class BindUser(val user: AuthenticatedUser) : Action
        data object RequestCatalogSync : Action
        data object DismissCatalogSyncConfirmation : Action
        data object ConfirmCatalogSync : Action
        data object DismissCatalogBanner : Action
        data class RequestLogout(val user: AuthenticatedUser) : Action
        data object DismissLogoutWarning : Action
        data class ConfirmLogout(val user: AuthenticatedUser) : Action
        data object DismissError : Action
    }

    sealed interface Event

    private var currentUser: AuthenticatedUser? = null
    private var catalogObservationJob: Job? = null

    init {
        viewModelScope.launch {
            repository.observePendingCount().collect { count ->
                setState {
                    copy(
                        pendingCardCount = count,
                        showPendingLogoutWarning = showPendingLogoutWarning && count > 0L,
                    )
                }
            }
        }
        viewModelScope.launch {
            syncPreferences.allowMobileData.collect { allow ->
                setState { copy(allowMobileData = allow) }
            }
        }
        viewModelScope.launch {
            networkStatusMonitor.status.collect { status ->
                setState { copy(networkStatus = status) }
            }
        }
    }

    override fun processImpl(action: Action) {
        when (action) {
            Action.ShowAccountInformation -> setState { copy(showAccountInformation = true) }
            Action.DismissAccountInformation -> setState { copy(showAccountInformation = false) }
            Action.ShowPermissions -> {
                refreshPermissions()
                setState { copy(showPermissions = true) }
            }
            Action.DismissPermissions -> {
                refreshPermissions()
                setState { copy(showPermissions = false) }
            }
            Action.RefreshPermissions -> refreshPermissions()
            is Action.SetAllowMobileData -> {
                syncPreferences.setAllowMobileData(action.allow)
                if (!action.allow) {
                    viewModelScope.launch { cardSyncManager.cancel() }
                }
            }
            is Action.BindUser -> bindUser(action.user)
            Action.RequestCatalogSync -> requestCatalogSync()
            Action.DismissCatalogSyncConfirmation -> setState {
                copy(showCatalogSyncConfirmation = false)
            }
            Action.ConfirmCatalogSync -> confirmCatalogSync()
            Action.DismissCatalogBanner -> setState { copy(catalogBanner = null) }
            is Action.RequestLogout -> requestLogout(action.user)
            Action.DismissLogoutWarning -> setState { copy(showPendingLogoutWarning = false) }
            is Action.ConfirmLogout -> confirmLogout(action.user)
            Action.DismissError -> setState { copy(logoutFailed = false) }
        }
    }

    private fun refreshPermissions() {
        setState { copy(missingPermissionCount = permissionManager.missingPermissionCount()) }
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
        setState { copy(showCatalogSyncConfirmation = true, catalogBanner = null) }
    }

    private fun confirmCatalogSync() {
        val user = currentUser ?: return
        val state = getStateValue()
        if (state.isCatalogSyncBusy()) return

        setState { copy(showCatalogSyncConfirmation = false, catalogBanner = null) }
        when (state.networkStatus) {
            NetworkConnectionStatus.WIFI_CONNECTED -> enqueueCatalogSync(user)
            NetworkConnectionStatus.CELLULAR_CONNECTED -> {
                if (syncPreferences.allowMobileData.value) {
                    enqueueCatalogSync(user)
                } else {
                    setState { copy(catalogBanner = SettingsBanner.MOBILE_DATA_DISABLED) }
                }
            }
            NetworkConnectionStatus.WIFI_NO_INTERNET,
            NetworkConnectionStatus.CELLULAR_NO_INTERNET,
            NetworkConnectionStatus.OFFLINE,
            -> setState { copy(catalogBanner = SettingsBanner.NO_INTERNET) }
        }
    }

    private fun enqueueCatalogSync(user: AuthenticatedUser) {
        setState { copy(isCatalogSyncRequesting = true) }
        val enqueued = catalogSyncManager.enqueueManual(user, CatalogKind.entries.toSet())
        if (!enqueued) {
            setState {
                copy(
                    isCatalogSyncRequesting = false,
                    catalogBanner = SettingsBanner.CATALOG_SYNC_FAILED,
                )
            }
        }
    }

    private fun requestLogout(user: AuthenticatedUser) {
        if (getStateValue().isLoggingOut) return
        if (getStateValue().pendingCardCount > 0L) {
            setState { copy(showPendingLogoutWarning = true) }
        } else {
            logout(user)
        }
    }

    private fun confirmLogout(user: AuthenticatedUser) {
        if (getStateValue().isLoggingOut) return
        setState { copy(showPendingLogoutWarning = false) }
        logout(user)
    }

    private fun logout(user: AuthenticatedUser) {
        viewModelScope.launch {
            setState { copy(isLoggingOut = true, logoutFailed = false) }
            try {
                logoutManager.logout(user)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                setState {
                    copy(
                        isLoggingOut = false,
                        logoutFailed = true,
                    )
                }
            }
        }
    }
}

fun SettingsUiState.isCatalogSyncBusy(): Boolean =
    isCatalogSyncRequesting || catalogSyncStatus.isActive()

private fun CatalogSyncStatus.isActive(): Boolean =
    this is CatalogSyncStatus.WaitingForNetwork || this is CatalogSyncStatus.Downloading

private fun PermissionManager.missingPermissionCount(): Int = snapshot().items.count { item ->
    item.status == AppPermissionStatus.MISSING || item.status == AppPermissionStatus.PARTIAL
}
