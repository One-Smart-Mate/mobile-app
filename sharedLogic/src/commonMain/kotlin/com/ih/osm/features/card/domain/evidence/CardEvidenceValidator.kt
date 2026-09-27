package com.ih.osm.features.card.domain.evidence

import com.ih.osm.features.card.domain.create.CreateCardEvidenceDraft
import com.ih.osm.features.card.domain.create.CreateCardEvidenceError
import com.ih.osm.features.card.domain.create.CreateCardEvidenceErrorReason
import com.ih.osm.features.card.domain.model.CardEvidenceMediaType

data class CardEvidenceLimits(
    val images: Long,
    val videos: Long,
    val audios: Long,
    val videoDurationSeconds: Long,
    val audioDurationSeconds: Long,
)

fun validateEvidenceDraft(
    evidence: CreateCardEvidenceDraft,
    current: List<CreateCardEvidenceDraft>,
    limits: CardEvidenceLimits,
): CreateCardEvidenceError? {
    if (evidence.sizeBytes <= 0L) {
        return CreateCardEvidenceError(CreateCardEvidenceErrorReason.INVALID_MEDIA)
    }
    if (evidence.sizeBytes > MAX_EVIDENCE_FILE_SIZE_BYTES) {
        return CreateCardEvidenceError(
            CreateCardEvidenceErrorReason.FILE_TOO_LARGE,
            MAX_EVIDENCE_FILE_SIZE_BYTES / BYTES_PER_MEGABYTE,
        )
    }
    if (current.size >= MAX_TOTAL_EVIDENCES) {
        return CreateCardEvidenceError(
            CreateCardEvidenceErrorReason.TOTAL_LIMIT_REACHED,
            MAX_TOTAL_EVIDENCES.toLong(),
        )
    }
    return when (evidence.mediaType) {
        CardEvidenceMediaType.IMAGE -> current.validateCount(
            mediaType = CardEvidenceMediaType.IMAGE,
            limit = limits.images,
            reason = CreateCardEvidenceErrorReason.IMAGE_LIMIT_REACHED,
        )
        CardEvidenceMediaType.VIDEO -> current.validateTimedMedia(
            evidence = evidence,
            mediaType = CardEvidenceMediaType.VIDEO,
            countLimit = limits.videos,
            durationLimit = limits.videoDurationSeconds,
            countReason = CreateCardEvidenceErrorReason.VIDEO_LIMIT_REACHED,
            durationReason = CreateCardEvidenceErrorReason.VIDEO_DURATION_EXCEEDED,
        )
        CardEvidenceMediaType.AUDIO -> current.validateTimedMedia(
            evidence = evidence,
            mediaType = CardEvidenceMediaType.AUDIO,
            countLimit = limits.audios,
            durationLimit = limits.audioDurationSeconds,
            countReason = CreateCardEvidenceErrorReason.AUDIO_LIMIT_REACHED,
            durationReason = CreateCardEvidenceErrorReason.AUDIO_DURATION_EXCEEDED,
        )
    }
}

private fun List<CreateCardEvidenceDraft>.validateCount(
    mediaType: CardEvidenceMediaType,
    limit: Long,
    reason: CreateCardEvidenceErrorReason,
): CreateCardEvidenceError? =
    if (count { it.mediaType == mediaType } >= limit) CreateCardEvidenceError(reason, limit) else null

private fun List<CreateCardEvidenceDraft>.validateTimedMedia(
    evidence: CreateCardEvidenceDraft,
    mediaType: CardEvidenceMediaType,
    countLimit: Long,
    durationLimit: Long,
    countReason: CreateCardEvidenceErrorReason,
    durationReason: CreateCardEvidenceErrorReason,
): CreateCardEvidenceError? = when {
    evidence.durationMillis <= 0 -> CreateCardEvidenceError(CreateCardEvidenceErrorReason.INVALID_MEDIA)
    count { it.mediaType == mediaType } >= countLimit -> CreateCardEvidenceError(countReason, countLimit)
    durationLimit <= 0 || evidence.durationMillis > durationLimit * MILLIS_PER_SECOND ->
        CreateCardEvidenceError(durationReason, durationLimit)
    else -> null
}

private const val MAX_TOTAL_EVIDENCES = 20
private const val MAX_EVIDENCE_FILE_SIZE_BYTES = 25L * 1024L * 1024L
private const val BYTES_PER_MEGABYTE = 1024L * 1024L
private const val MILLIS_PER_SECOND = 1_000L
