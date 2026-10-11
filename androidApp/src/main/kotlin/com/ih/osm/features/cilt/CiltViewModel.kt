package com.ih.osm.features.cilt

import androidx.lifecycle.viewModelScope
import com.ih.osm.core.feature.viewmodel.GRViewModel
import com.ih.osm.core.network.NetworkErrorKind
import com.ih.osm.core.network.NetworkResult
import com.ih.osm.features.cilt.domain.model.CiltPosition
import com.ih.osm.features.cilt.domain.model.CiltSequence
import com.ih.osm.features.cilt.domain.repository.CiltRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CiltUiState(
    val initialized: Boolean = false,
    val isLoadingRoutine: Boolean = false,
    val positions: List<CiltPosition> = emptyList(),
    val error: CiltError? = null,
    // Sequence run state
    val sequence: CiltSequence? = null,
    val isLoadingSequence: Boolean = false,
    val runExecutionId: Long = 0,
    val isStarting: Boolean = false,
    val isCompleting: Boolean = false,
    val started: Boolean = false,
    val initialParameter: String = "",
    val finalParameter: String = "",
    val parameterOk: Boolean = true,
    val completed: Boolean = false,
    val isUploadingEvidence: Boolean = false,
    val evidenceCount: Int = 0,
)

enum class CiltError { ROUTINE_FAILED, NO_CONNECTION, ACCESS_DENIED, SEQUENCE_FAILED, START_FAILED, COMPLETE_FAILED, EVIDENCE_FAILED }

class CiltViewModel(
    private val repository: CiltRepository,
) : GRViewModel<CiltUiState, CiltViewModel.Action, CiltViewModel.Event>(CiltUiState()) {

    sealed interface Action {
        data object LoadRoutine : Action
        data class LoadSequence(val sequenceId: Long, val executionId: Long) : Action
        data object StartExecution : Action
        data object CompleteExecution : Action
        data class UploadEvidence(val bytes: ByteArray, val fileName: String, val contentType: String, val type: String) : Action
        data class InitialParameterChanged(val value: String) : Action
        data class FinalParameterChanged(val value: String) : Action
        data class ParameterOkChanged(val value: Boolean) : Action
        data object DismissError : Action
        data object ResetRun : Action
    }

    sealed interface Event

    override fun processImpl(action: Action) {
        when (action) {
            Action.LoadRoutine -> loadRoutine()
            is Action.LoadSequence -> loadSequence(action.sequenceId, action.executionId)
            Action.StartExecution -> startExecution()
            Action.CompleteExecution -> completeExecution()
            is Action.UploadEvidence -> uploadEvidence(action.bytes, action.fileName, action.contentType, action.type)
            is Action.InitialParameterChanged -> setState { copy(initialParameter = action.value) }
            is Action.FinalParameterChanged -> setState { copy(finalParameter = action.value) }
            is Action.ParameterOkChanged -> setState { copy(parameterOk = action.value) }
            Action.DismissError -> setState { copy(error = null) }
            Action.ResetRun -> setState {
                copy(
                    sequence = null, runExecutionId = 0, started = false, completed = false,
                    initialParameter = "", finalParameter = "", parameterOk = true,
                    isLoadingSequence = false, isStarting = false, isCompleting = false,
                )
            }
        }
    }

    private fun loadRoutine() {
        setState { copy(initialized = true, isLoadingRoutine = true, error = null) }
        viewModelScope.launch {
            try {
                when (val result = withContext(Dispatchers.IO) { repository.getRoutineOfDay() }) {
                    is NetworkResult.Success ->
                        setState { copy(positions = result.data.positions, error = null) }
                    is NetworkResult.Failure ->
                        setState { copy(error = result.error.toCiltError(CiltError.ROUTINE_FAILED)) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                setState { copy(error = CiltError.ROUTINE_FAILED) }
            } finally {
                setState { copy(isLoadingRoutine = false) }
            }
        }
    }

    private fun loadSequence(sequenceId: Long, executionId: Long) {
        setState {
            copy(
                isLoadingSequence = true, error = null, runExecutionId = executionId,
                started = false, completed = false,
                initialParameter = "", finalParameter = "", parameterOk = true,
            )
        }
        viewModelScope.launch {
            try {
                when (val result = withContext(Dispatchers.IO) { repository.getSequence(sequenceId) }) {
                    is NetworkResult.Success -> {
                        val execution = result.data.executions.firstOrNull { it.id == executionId }
                        setState {
                            copy(
                                sequence = result.data,
                                started = execution?.isCompleted == false && execution.secuenceStart != null,
                            )
                        }
                    }
                    is NetworkResult.Failure ->
                        setState { copy(error = result.error.toCiltError(CiltError.SEQUENCE_FAILED)) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                setState { copy(error = CiltError.SEQUENCE_FAILED) }
            } finally {
                setState { copy(isLoadingSequence = false) }
            }
        }
    }

    private fun uploadEvidence(bytes: ByteArray, fileName: String, contentType: String, type: String) {
        val executionId = getStateValue().runExecutionId
        val siteId = getStateValue().sequence?.siteId ?: 0
        if (executionId <= 0 || siteId <= 0 || getStateValue().isUploadingEvidence) return
        setState { copy(isUploadingEvidence = true, error = null) }
        viewModelScope.launch {
            try {
                when (
                    val result = withContext(Dispatchers.IO) {
                        repository.uploadEvidence(siteId, executionId, type, fileName, contentType, bytes)
                    }
                ) {
                    is NetworkResult.Success -> setState { copy(evidenceCount = evidenceCount + 1) }
                    is NetworkResult.Failure ->
                        setState { copy(error = result.error.toCiltError(CiltError.EVIDENCE_FAILED)) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                setState { copy(error = CiltError.EVIDENCE_FAILED) }
            } finally {
                setState { copy(isUploadingEvidence = false) }
            }
        }
    }

    private fun startExecution() {
        val executionId = getStateValue().runExecutionId
        if (executionId <= 0 || getStateValue().isStarting) return
        setState { copy(isStarting = true, error = null) }
        viewModelScope.launch {
            try {
                when (val result = withContext(Dispatchers.IO) { repository.startExecution(executionId) }) {
                    is NetworkResult.Success -> setState { copy(started = true) }
                    is NetworkResult.Failure -> {
                        // The sequence may already be started on the backend; allow completing anyway.
                        if (result.error.message?.contains("already been started", true) == true) {
                            setState { copy(started = true) }
                        } else {
                            setState { copy(error = result.error.toCiltError(CiltError.START_FAILED)) }
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                setState { copy(error = CiltError.START_FAILED) }
            } finally {
                setState { copy(isStarting = false) }
            }
        }
    }

    private fun completeExecution() {
        val state = getStateValue()
        if (state.runExecutionId <= 0 || state.isCompleting) return
        setState { copy(isCompleting = true, error = null) }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    repository.stopExecution(
                        executionId = state.runExecutionId,
                        initialParameter = state.initialParameter,
                        finalParameter = state.finalParameter,
                        parameterOk = state.parameterOk,
                    )
                }
                when (result) {
                    is NetworkResult.Success -> setState { copy(completed = true) }
                    is NetworkResult.Failure ->
                        setState { copy(error = result.error.toCiltError(CiltError.COMPLETE_FAILED)) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                setState { copy(error = CiltError.COMPLETE_FAILED) }
            } finally {
                setState { copy(isCompleting = false) }
            }
        }
    }

    private fun com.ih.osm.core.network.NetworkError.toCiltError(fallback: CiltError): CiltError = when {
        statusCode == 401 || statusCode == 403 -> CiltError.ACCESS_DENIED
        kind == NetworkErrorKind.CONNECTIVITY -> CiltError.NO_CONNECTION
        else -> fallback
    }
}
