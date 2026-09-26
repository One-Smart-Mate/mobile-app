package com.ih.osm.features.permissions

import androidx.lifecycle.viewModelScope
import com.ih.osm.core.feature.viewmodel.GRViewModel
import kotlinx.coroutines.launch

class PermissionsViewModel(
    private val helper: PermissionHelper,
) : GRViewModel<PermissionsViewModel.UiState, PermissionsViewModel.Action, PermissionsViewModel.Event>(
    initialState = UiState(),
) {
    data class UiState(
        val initialized: Boolean = false,
        val showSheet: Boolean = false,
        val isRequesting: Boolean = false,
        val hasRequestedOnce: Boolean = false,
        val items: List<AppPermissionItem> = emptyList(),
    )

    sealed interface Action {
        data object Initialize : Action
        data object Continue : Action
        data object NotNow : Action
        data object RuntimeRequestFinished : Action
    }

    sealed interface Event {
        data class RequestRuntimePermissions(val permissions: List<String>) : Event
        data object OpenAppSettings : Event
    }

    override fun processImpl(action: Action) {
        when (action) {
            Action.Initialize -> initialize()
            Action.Continue -> continueRequest()
            Action.NotNow -> setState { copy(showSheet = false, isRequesting = false) }
            Action.RuntimeRequestFinished -> refreshAfterRequest()
        }
    }

    private fun initialize() {
        if (getStateValue().initialized) return
        val snapshot = helper.snapshot()
        setState {
            copy(
                initialized = true,
                showSheet = !snapshot.allRuntimePermissionsGranted,
                items = snapshot.items,
            )
        }
    }

    private fun continueRequest() {
        if (getStateValue().isRequesting) return
        val snapshot = helper.snapshot()
        if (snapshot.allRuntimePermissionsGranted) {
            setState { copy(showSheet = false, items = snapshot.items) }
            return
        }
        setState { copy(isRequesting = true, items = snapshot.items) }
        viewModelScope.launch {
            if (getStateValue().hasRequestedOnce) {
                sendNewEvent(Event.OpenAppSettings)
            } else {
                sendNewEvent(Event.RequestRuntimePermissions(snapshot.missingRuntimePermissions))
            }
        }
    }

    private fun refreshAfterRequest() {
        val snapshot = helper.snapshot()
        setState {
            copy(
                showSheet = !snapshot.allRuntimePermissionsGranted,
                isRequesting = false,
                hasRequestedOnce = true,
                items = snapshot.items,
            )
        }
    }
}
