@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.ih.osm.features.card

import com.ih.osm.core.network.NetworkResult
import com.ih.osm.features.card.domain.model.CardEvidenceMediaType
import com.ih.osm.features.card.domain.repository.CardRepository
import com.ih.osm.features.card.domain.usecase.PendingCardSyncResult
import com.ih.osm.features.card.domain.usecase.SyncPendingCardsUseCase
import com.ih.osm.features.card.domain.usecase.SyncPendingSolutionsUseCase
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.readBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL

data class IosCardSyncProgress(
    val completed: Int,
    val total: Int,
) {
    val fraction: Double
        get() = if (total <= 0) 0.0 else completed.toDouble() / total
}

data class IosCardSyncOutcome(
    val succeeded: Boolean,
    val didRun: Boolean,
    val synced: Int,
    val errorMessage: String?,
    val retryable: Boolean,
)

class IosCardSyncController(
    private val repository: CardRepository,
    private val syncPendingCards: SyncPendingCardsUseCase,
    private val syncPendingSolutions: SyncPendingSolutionsUseCase,
) {
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var activeJob: Job? = null
    private var pendingObservationJob: Job? = null

    fun pendingCount(): Long = repository.pendingCount()

    fun observePendingCount(onChanged: (Long) -> Unit) {
        pendingObservationJob?.cancel()
        pendingObservationJob = scope.launch {
            repository.observePendingCount().collectLatest(onChanged)
        }
    }

    fun stopPendingCountObservation() {
        pendingObservationJob?.cancel()
        pendingObservationJob = null
    }

    suspend fun syncPending(
        onProgress: (IosCardSyncProgress) -> Unit,
    ): IosCardSyncOutcome = mutex.withLock {
        val pendingCards = repository.getPending(MAX_PENDING).size
        val pendingSolutions = repository.getPendingSolutions(MAX_PENDING).sumOf { card ->
            (if (card.provisionalSolutionPending) 1 else 0) +
                (if (card.definitiveSolutionPending) 1 else 0)
        }
        val pendingEvidence = repository.getPendingWork(MAX_PENDING)
            .sumOf { card -> card.evidences.count { it.isLocal } }
        val total = pendingCards + pendingSolutions + pendingEvidence
        if (total == 0) return@withLock IosCardSyncOutcome(true, false, 0, null, false)

        activeJob = currentCoroutineContext()[Job]
        onProgress(IosCardSyncProgress(0, total))
        try {
            val evidenceResult = uploadPendingEvidence(total, onProgress)
            if (evidenceResult is UploadResult.Failure) {
                return@withLock IosCardSyncOutcome(
                    succeeded = false,
                    didRun = true,
                    synced = 0,
                    errorMessage = evidenceResult.message,
                    retryable = true,
                )
            }
            val uploaded = (evidenceResult as UploadResult.Success).uploaded
            when (val cardsResult = syncPendingCards { progress ->
                onProgress(IosCardSyncProgress(uploaded + progress.completed, total))
            }) {
                is PendingCardSyncResult.Failure -> return@withLock cardsResult.toIosOutcome()
                is PendingCardSyncResult.Success -> when (val solutionsResult = syncPendingSolutions { progress ->
                    onProgress(IosCardSyncProgress(uploaded + pendingCards + progress.completed, total))
                }) {
                    is PendingCardSyncResult.Failure -> solutionsResult.toIosOutcome(cardsResult.synced)
                    is PendingCardSyncResult.Success -> IosCardSyncOutcome(
                        succeeded = true,
                        didRun = true,
                        synced = cardsResult.synced + solutionsResult.synced,
                        errorMessage = null,
                        retryable = false,
                    )
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            IosCardSyncOutcome(false, true, 0, throwable.message, true)
        } finally {
            activeJob = null
        }
    }

    fun cancelActiveSync() {
        activeJob?.cancel()
    }

    private suspend fun uploadPendingEvidence(
        totalOperations: Int,
        onProgress: (IosCardSyncProgress) -> Unit,
    ): UploadResult {
        val pending = repository.getPendingWork(MAX_PENDING)
            .flatMap { card -> card.evidences.filter { it.isLocal }.map { card to it } }
        var uploaded = 0
        for ((card, evidence) in pending) {
            val data = NSFileManager.defaultManager.contentsAtPath(evidence.url)
                ?: return UploadResult.Failure("No se encontró un archivo de evidencia local.", uploaded)
            val bytes = data.bytes?.reinterpret<ByteVar>()?.readBytes(data.length.toInt())
                ?: return UploadResult.Failure("No fue posible leer la evidencia local.", uploaded)
            val fileUrl = NSURL.fileURLWithPath(evidence.url)
            val fileName = fileUrl.lastPathComponent ?: "evidence"
            val extension = fileUrl.pathExtension.orEmpty().lowercase()
            when (
                val result = repository.uploadEvidence(
                    siteId = card.siteId,
                    cardUuid = card.uuid,
                    evidenceId = evidence.id,
                    evidenceType = evidence.typeCode,
                    fileName = fileName,
                    contentType = evidence.mediaType.contentType(extension),
                    bytes = bytes,
                )
            ) {
                is NetworkResult.Failure -> return UploadResult.Failure(result.error.message, uploaded)
                is NetworkResult.Success -> {
                    repository.markEvidenceUploaded(evidence.id, result.data)
                    NSFileManager.defaultManager.removeItemAtPath(evidence.url, error = null)
                    uploaded += 1
                    onProgress(IosCardSyncProgress(uploaded, totalOperations))
                }
            }
        }
        return UploadResult.Success(uploaded)
    }

    private sealed interface UploadResult {
        data class Success(val uploaded: Int) : UploadResult
        data class Failure(val message: String, val uploaded: Int) : UploadResult
    }

    private fun PendingCardSyncResult.Failure.toIosOutcome(alreadySynced: Int = 0) = IosCardSyncOutcome(
        succeeded = false,
        didRun = true,
        synced = alreadySynced + synced,
        errorMessage = message,
        retryable = retryable,
    )

    private companion object {
        const val MAX_PENDING = 10_000L
    }
}

private fun CardEvidenceMediaType.contentType(extension: String): String = when (this) {
    CardEvidenceMediaType.IMAGE -> when (extension) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        else -> "image/jpeg"
    }
    CardEvidenceMediaType.VIDEO -> if (extension == "mov") "video/quicktime" else "video/mp4"
    CardEvidenceMediaType.AUDIO -> when (extension) {
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "aac" -> "audio/aac"
        else -> "audio/mp4"
    }
}
