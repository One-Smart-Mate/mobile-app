package com.ih.osm.features.cards.data.sync

import com.ih.osm.core.network.NetworkResult
import com.ih.osm.features.card.domain.model.CardEvidence
import com.ih.osm.features.card.domain.model.CardEvidenceMediaType
import com.ih.osm.features.card.domain.repository.CardRepository
import com.ih.osm.features.carddetail.domain.cache.EvidenceCache
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface EvidenceUploadResult {
    data class Success(val uploaded: Int) : EvidenceUploadResult
    data class Failure(val message: String, val uploaded: Int) : EvidenceUploadResult
}

class ServiceCardEvidenceUploader(
    private val repository: CardRepository,
    private val evidenceCache: EvidenceCache,
) {
    fun pendingEvidenceCount(): Int = repository.getPendingWork(MAX_PENDING_CARDS)
        .sumOf { card -> card.evidences.count { it.isLocal } }

    suspend fun uploadPending(
        onProgress: suspend (completed: Int, total: Int) -> Unit,
    ): EvidenceUploadResult {
        val pending = repository.getPendingWork(MAX_PENDING_CARDS)
            .flatMap { card -> card.evidences.filter { it.isLocal }.map { card to it } }
        if (pending.isEmpty()) return EvidenceUploadResult.Success(0)
        var uploaded = 0
        onProgress(0, pending.size)
        for ((card, evidence) in pending) {
            val localFile = File(evidence.url)
            if (!localFile.isFile || localFile.length() == 0L) {
                return EvidenceUploadResult.Failure(
                    "No se encontró un archivo de evidencia local.",
                    uploaded,
                )
            }
            val bytes = withContext(Dispatchers.IO) { localFile.readBytes() }
            when (
                val result = repository.uploadEvidence(
                    siteId = card.siteId,
                    cardUuid = card.uuid,
                    evidenceId = evidence.id,
                    evidenceType = evidence.typeCode,
                    fileName = localFile.name,
                    contentType = evidence.contentType(localFile),
                    bytes = bytes,
                )
            ) {
                is NetworkResult.Failure -> return EvidenceUploadResult.Failure(
                    result.error.message,
                    uploaded,
                )
                is NetworkResult.Success -> {
                    repository.markEvidenceUploaded(evidence.id, result.data)
                    runCatching {
                        evidenceCache.adopt(localFile, result.data, evidence.mediaType)
                    }.onFailure {
                        withContext(Dispatchers.IO) { localFile.delete() }
                    }
                    uploaded += 1
                    onProgress(uploaded, pending.size)
                }
            }
        }
        return EvidenceUploadResult.Success(uploaded)
    }

    private companion object {
        const val MAX_PENDING_CARDS = 10_000L
    }
}

private fun CardEvidence.contentType(file: File): String = when (mediaType) {
    CardEvidenceMediaType.IMAGE -> when (file.extension.lowercase()) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        else -> "image/jpeg"
    }
    CardEvidenceMediaType.VIDEO -> when (file.extension.lowercase()) {
        "mov" -> "video/quicktime"
        "webm" -> "video/webm"
        else -> "video/mp4"
    }
    CardEvidenceMediaType.AUDIO -> when (file.extension.lowercase()) {
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "webm" -> "audio/webm"
        "aac" -> "audio/aac"
        else -> "audio/mp4"
    }
}
