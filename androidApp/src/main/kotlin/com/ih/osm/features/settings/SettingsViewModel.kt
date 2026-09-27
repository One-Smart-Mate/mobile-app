package com.ih.osm.features.settings

import androidx.lifecycle.viewModelScope
import com.ih.osm.core.feature.viewmodel.GRViewModel
import com.ih.osm.features.auth.domain.model.AuthenticatedUser
import com.ih.osm.features.card.domain.repository.CardRepository
import com.ih.osm.features.cards.domain.manager.CardSyncManager
import com.ih.osm.features.permissions.domain.manager.PermissionManager
import com.ih.osm.features.permissions.domain.model.AppPermissionStatus
import com.ih.osm.features.settings.domain.manager.LogoutManager
import com.ih.osm.features.settings.domain.preferences.MobileDataSyncPreferences
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

data class SettingsUiState(
    val pendingCardCount: Long = 0,
    val missingPermissionCount: Int = 0,
    val allowMobileData: Boolean = true,
    val showAccountInformation: Boolean = false,
    val showPermissions: Boolean = false,
    val showPendingLogoutWarning: Boolean = false,
    val isLoggingOut: Boolean = false,
    val logoutFailed: Boolean = false,
)

class SettingsViewModel(
    repository: CardRepository,
    private val permissionManager: PermissionManager,
    private val syncPreferences: MobileDataSyncPreferences,
    private val cardSyncManager: CardSyncManager,
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
        data class RequestLogout(val user: AuthenticatedUser) : Action
        data object DismissLogoutWarning : Action
        data class ConfirmLogout(val user: AuthenticatedUser) : Action
        data object DismissError : Action
    }

    sealed interface Event

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
            is Action.RequestLogout -> requestLogout(action.user)
            Action.DismissLogoutWarning -> setState { copy(showPendingLogoutWarning = false) }
            is Action.ConfirmLogout -> confirmLogout(action.user)
            Action.DismissError -> setState { copy(logoutFailed = false) }
        }
    }

    private fun refreshPermissions() {
        setState { copy(missingPermissionCount = permissionManager.missingPermissionCount()) }
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

private fun PermissionManager.missingPermissionCount(): Int = snapshot().items.count { item ->
    item.status == AppPermissionStatus.MISSING || item.status == AppPermissionStatus.PARTIAL
}
