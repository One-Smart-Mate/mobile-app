package com.ih.osm.features.createcard

import androidx.lifecycle.viewModelScope
import com.ih.osm.core.feature.viewmodel.GRViewModel
import com.ih.osm.features.auth.domain.model.AuthenticatedUser
import com.ih.osm.features.card.domain.create.CreateCardManager
import com.ih.osm.features.card.domain.create.CreateCardSaveResult
import com.ih.osm.features.card.domain.create.CreateCardSheet
import com.ih.osm.features.card.domain.create.CreateCardState
import com.ih.osm.features.cards.sync.CardSyncScheduler
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CreateCardViewModel(
    private val manager: CreateCardManager,
    private val syncScheduler: CardSyncScheduler,
) : GRViewModel<CreateCardState, CreateCardViewModel.Action, CreateCardViewModel.Event>(
    initialState = CreateCardState(),
) {
    sealed interface Action {
        data class Initialize(
            val user: AuthenticatedUser,
            val siteId: Long?,
            val appVersion: String,
        ) : Action
        data class OpenSheet(val sheet: CreateCardSheet) : Action
        data object DismissSheet : Action
        data class SearchChanged(val value: String) : Action
        data class SelectItem(val id: String) : Action
        data class NavigateLevel(val id: String?) : Action
        data class CustomDueDateChanged(val value: String) : Action
        data class DescriptionChanged(val value: String) : Action
        data object DismissError : Action
        data object Continue : Action
        data object Back : Action
        data object Save : Action
    }

    sealed interface Event {
        data object Close : Event
        data class Created(val uuid: String) : Event
    }

    init {
        viewModelScope.launch {
            manager.state.collectLatest { state -> setState { state } }
        }
    }

    override fun processImpl(action: Action) {
        when (action) {
            is Action.Initialize -> manager.initialize(action.user, action.siteId, action.appVersion)
            is Action.OpenSheet -> manager.openSheet(action.sheet)
            Action.DismissSheet -> manager.dismissSheet()
            is Action.SearchChanged -> manager.updateSheetQuery(action.value)
            is Action.SelectItem -> manager.selectSheetItem(action.id)
            is Action.NavigateLevel -> manager.navigateToLevel(action.id)
            is Action.CustomDueDateChanged -> manager.updateCustomDueDate(action.value)
            is Action.DescriptionChanged -> manager.updateDescription(action.value)
            Action.DismissError -> manager.dismissError()
            Action.Continue -> manager.next()
            Action.Back -> {
                if (!manager.back()) viewModelScope.launch { sendNewEvent(Event.Close) }
            }
            Action.Save -> save()
        }
    }

    private fun save() {
        viewModelScope.launch {
            when (val result = manager.save()) {
                is CreateCardSaveResult.Success -> {
                    syncScheduler.enqueueIfPending()
                    sendNewEvent(Event.Created(result.uuid))
                }
                is CreateCardSaveResult.Failure,
                CreateCardSaveResult.Ignored,
                -> Unit
            }
        }
    }
}
