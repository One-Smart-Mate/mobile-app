package com.ih.osm.features.cardsolution

import androidx.lifecycle.viewModelScope
import com.ih.osm.core.feature.viewmodel.GRViewModel
import com.ih.osm.features.auth.domain.model.AuthenticatedUser
import com.ih.osm.features.card.domain.create.CreateCardEvidenceDraft
import com.ih.osm.features.card.domain.solution.CardSolutionManager
import com.ih.osm.features.card.domain.solution.CardSolutionSaveResult
import com.ih.osm.features.card.domain.solution.CardSolutionState
import com.ih.osm.features.card.domain.solution.CardSolutionType
import com.ih.osm.features.cards.domain.manager.CardSyncManager
import com.ih.osm.features.createcard.domain.storage.EvidenceStorage
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CardSolutionViewModel(
    private val manager: CardSolutionManager,
    private val cardSyncManager: CardSyncManager,
    private val evidenceStorage: EvidenceStorage,
) : GRViewModel<CardSolutionState, CardSolutionViewModel.Action, CardSolutionViewModel.Event>(
    initialState = CardSolutionState(),
) {
    sealed interface Action {
        data class Initialize(
            val user: AuthenticatedUser,
            val cardUuid: String,
            val type: CardSolutionType,
        ) : Action

        data object OpenEmployeeSheet : Action
        data object DismissEmployeeSheet : Action
        data class SearchEmployee(val value: String) : Action
        data class SelectEmployee(val id: String) : Action
        data class CommentsChanged(val value: String) : Action
        data class AddEvidence(val evidence: CreateCardEvidenceDraft) : Action
        data class RemoveEvidence(val id: String) : Action
        data class EvidenceProcessing(val processing: Boolean) : Action
        data object EvidenceImportFailed : Action
        data object DismissError : Action
        data object Save : Action
        data object Back : Action
    }

    sealed interface Event {
        data object Close : Event
        data object Saved : Event
    }

    init {
        viewModelScope.launch {
            manager.state.collectLatest { state -> setState { state } }
        }
    }

    override fun processImpl(action: Action) {
        when (action) {
            is Action.Initialize -> manager.initialize(action.user, action.cardUuid, action.type)
            Action.OpenEmployeeSheet -> manager.openEmployeeSheet()
            Action.DismissEmployeeSheet -> manager.dismissEmployeeSheet()
            is Action.SearchEmployee -> manager.searchEmployees(action.value)
            is Action.SelectEmployee -> manager.selectEmployee(action.id)
            is Action.CommentsChanged -> manager.updateComments(action.value)
            is Action.EvidenceProcessing -> manager.setEvidenceProcessing(action.processing)
            is Action.AddEvidence -> {
                if (!manager.addEvidence(action.evidence)) evidenceStorage.delete(action.evidence.localPath)
            }
            is Action.RemoveEvidence -> manager.removeEvidence(action.id)?.let {
                evidenceStorage.delete(it.localPath)
            }
            Action.EvidenceImportFailed -> manager.evidenceImportFailed()
            Action.DismissError -> manager.dismissError()
            Action.Save -> save()
            Action.Back -> close()
        }
    }

    override fun onCleared() {
        if (!manager.state.value.saved) clearDraftEvidence()
        evidenceStorage.cancelAudioRecording()
        super.onCleared()
    }

    private fun save() {
        viewModelScope.launch {
            when (manager.save()) {
                CardSolutionSaveResult.Success -> {
                    cardSyncManager.enqueueAfterLocalChange()
                    sendNewEvent(Event.Saved)
                }
                is CardSolutionSaveResult.Failure,
                CardSolutionSaveResult.Ignored,
                -> Unit
            }
        }
    }

    private fun close() {
        clearDraftEvidence()
        viewModelScope.launch { sendNewEvent(Event.Close) }
    }

    private fun clearDraftEvidence() {
        manager.state.value.evidences.forEach { evidenceStorage.delete(it.localPath) }
    }
}
