package com.ih.osm.features.card

import com.ih.osm.features.auth.domain.session.SessionRepository
import com.ih.osm.features.card.domain.create.CreateCardEvidenceDraft
import com.ih.osm.features.card.domain.create.CreateCardManager
import com.ih.osm.features.card.domain.create.CreateCardSaveResult
import com.ih.osm.features.card.domain.create.CreateCardSheet
import com.ih.osm.features.card.domain.create.CreateCardState
import com.ih.osm.features.card.domain.model.CardEvidenceMediaType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class IosCreateCardSaveOutcome(
    val succeeded: Boolean,
    val uuid: String?,
)

class IosCreateCardController(
    private val manager: CreateCardManager,
    private val sessionRepository: SessionRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observationJob: Job? = null

    suspend fun start(
        siteId: Long?,
        appVersion: String,
        onStateChanged: (CreateCardState) -> Unit,
    ): Boolean {
        val user = sessionRepository.currentUser() ?: return false
        observationJob?.cancel()
        observationJob = scope.launch {
            manager.state.collectLatest(onStateChanged)
        }
        manager.initialize(user, siteId, appVersion)
        return true
    }

    fun openSheet(sheet: CreateCardSheet) = manager.openSheet(sheet)
    fun dismissSheet() = manager.dismissSheet()
    fun updateSheetQuery(value: String) = manager.updateSheetQuery(value)
    fun selectSheetItem(id: String) = manager.selectSheetItem(id)
    fun navigateToLevel(id: String?) = manager.navigateToLevel(id)
    fun updateCustomDueDate(value: String) = manager.updateCustomDueDate(value)
    fun updateDescription(value: String) = manager.updateDescription(value)
    fun setEvidenceProcessing(value: Boolean) = manager.setEvidenceProcessing(value)
    fun evidenceImportFailed() = manager.evidenceImportFailed()
    fun dismissError() = manager.dismissError()
    fun next(): Boolean = manager.next()
    fun back(): Boolean = manager.back()

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

    suspend fun save(): IosCreateCardSaveOutcome = when (val result = manager.save()) {
        is CreateCardSaveResult.Success -> IosCreateCardSaveOutcome(true, result.uuid)
        is CreateCardSaveResult.Failure,
        CreateCardSaveResult.Ignored,
        -> IosCreateCardSaveOutcome(false, null)
    }

    fun stop() {
        observationJob?.cancel()
        observationJob = null
    }
}
