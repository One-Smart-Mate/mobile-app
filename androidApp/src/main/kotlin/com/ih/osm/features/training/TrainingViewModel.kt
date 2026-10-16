package com.ih.osm.features.training

import androidx.lifecycle.viewModelScope
import com.ih.osm.core.feature.viewmodel.GRViewModel
import com.ih.osm.core.network.NetworkResult
import com.ih.osm.features.training.domain.model.TrainingRecord
import com.ih.osm.features.training.domain.repository.TrainingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TrainingUiState(
    val initialized: Boolean = false,
    val isLoading: Boolean = false,
    val records: List<TrainingRecord> = emptyList(),
    val markingOplId: Long? = null,
    val error: TrainingError? = null,
    val message: TrainingMessage? = null,
)

enum class TrainingError { LOAD_FAILED, MARK_FAILED, NOT_AUTHENTICATED }

enum class TrainingMessage { MARKED_TAKEN }

class TrainingViewModel(
    private val repository: TrainingRepository,
) : GRViewModel<TrainingUiState, TrainingViewModel.Action, TrainingViewModel.Event>(TrainingUiState()) {
    sealed interface Action {
        data object Initialize : Action
        data object Refresh : Action
        data class MarkTaken(val oplId: Long) : Action
        data object DismissError : Action
        data object DismissMessage : Action
    }

    sealed interface Event

    override fun processImpl(action: Action) {
        when (action) {
            Action.Initialize -> if (!getStateValue().initialized) load()
            Action.Refresh -> load()
            is Action.MarkTaken -> markTaken(action.oplId)
            Action.DismissError -> setState { copy(error = null) }
            Action.DismissMessage -> setState { copy(message = null) }
        }
    }

    private fun load() {
        setState { copy(initialized = true, isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { repository.getMyRecords() }) {
                is NetworkResult.Success ->
                    setState { copy(isLoading = false, records = result.data.sortedBy { it.oplTitle }) }
                is NetworkResult.Failure ->
                    setState { copy(isLoading = false, error = TrainingError.LOAD_FAILED) }
            }
        }
    }

    private fun markTaken(oplId: Long) {
        if (getStateValue().markingOplId != null) return
        setState { copy(markingOplId = oplId, error = null) }
        viewModelScope.launch {
            when (withContext(Dispatchers.IO) { repository.selfComplete(oplId) }) {
                is NetworkResult.Success -> {
                    setState { copy(markingOplId = null, message = TrainingMessage.MARKED_TAKEN) }
                    load()
                }
                is NetworkResult.Failure ->
                    setState { copy(markingOplId = null, error = TrainingError.MARK_FAILED) }
            }
        }
    }
}
