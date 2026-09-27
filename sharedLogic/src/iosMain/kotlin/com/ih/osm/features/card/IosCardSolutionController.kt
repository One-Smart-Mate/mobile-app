package com.ih.osm.features.card

import com.ih.osm.features.auth.domain.session.SessionRepository
import com.ih.osm.features.card.domain.create.CreateCardEvidenceDraft
import com.ih.osm.features.card.domain.model.CardEvidenceMediaType
import com.ih.osm.features.card.domain.solution.CardSolutionManager
import com.ih.osm.features.card.domain.solution.CardSolutionSaveResult
import com.ih.osm.features.card.domain.solution.CardSolutionState
import com.ih.osm.features.card.domain.solution.CardSolutionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class IosCardSolutionSaveOutcome(
    val succeeded: Boolean,
)

class IosCardSolutionController(
    private val manager: CardSolutionManager,
    private val sessionRepository: SessionRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observationJob: Job? = null

    suspend fun start(
        cardUuid: String,
        type: CardSolutionType,
        onStateChanged: (CardSolutionState) -> Unit,
    ): Boolean {
        val user = sessionRepository.currentUser() ?: return false
        observationJob?.cancel()
        observationJob = scope.launch {
            manager.state.collectLatest(onStateChanged)
        }
        manager.initialize(user, cardUuid, type)
        return true
    }

    fun openEmployeeSheet() = manager.openEmployeeSheet()
    fun dismissEmployeeSheet() = manager.dismissEmployeeSheet()
    fun searchEmployees(value: String) = manager.searchEmployees(value)
    fun selectEmployee(id: String) = manager.selectEmployee(id)
    fun updateComments(value: String) = manager.updateComments(value)
    fun setEvidenceProcessing(value: Boolean) = manager.setEvidenceProcessing(value)
    fun evidenceImportFailed() = manager.evidenceImportFailed()
    fun dismissError() = manager.dismissError()

    fun addEvidence(
        id: String,
        localPath: String,
        displayName: String,
        mimeType: String,
        mediaType: CardEvidenceMediaType,
        durationMillis: Long,
        sizeBytes: Long,
    ): Boolean = manager.addEvidence(
        CreateCardEvidenceDraft(
            id = id,
            localPath = localPath,
            displayName = displayName,
            mimeType = mimeType,
            mediaType = mediaType,
            durationMillis = durationMillis,
            sizeBytes = sizeBytes,
        ),
    )

    fun removeEvidence(id: String): String? = manager.removeEvidence(id)?.localPath

    suspend fun save(): IosCardSolutionSaveOutcome = when (manager.save()) {
        CardSolutionSaveResult.Success -> IosCardSolutionSaveOutcome(true)
        is CardSolutionSaveResult.Failure,
        CardSolutionSaveResult.Ignored,
        -> IosCardSolutionSaveOutcome(false)
    }

    fun stop() {
        observationJob?.cancel()
        observationJob = null
    }
}
