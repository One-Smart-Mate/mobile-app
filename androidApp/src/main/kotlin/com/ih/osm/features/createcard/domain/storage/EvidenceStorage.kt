package com.ih.osm.features.createcard.domain.storage

import android.net.Uri
import com.ih.osm.features.card.domain.create.CreateCardEvidenceDraft
import com.ih.osm.features.card.domain.model.CardEvidenceMediaType
import java.io.File

data class PendingEvidenceCapture(
    val id: String,
    val file: File,
    val uri: Uri,
    val mediaType: CardEvidenceMediaType,
    val mimeType: String,
)

interface EvidenceStorage {
    fun createCapture(mediaType: CardEvidenceMediaType): PendingEvidenceCapture
    suspend fun finishCapture(capture: PendingEvidenceCapture): CreateCardEvidenceDraft
    fun startAudioRecording(maxDurationSeconds: Long): PendingEvidenceCapture
    suspend fun stopAudioRecording(): CreateCardEvidenceDraft
    fun delete(localPath: String)
    fun discard(capture: PendingEvidenceCapture?)
    fun cancelAudioRecording()
}
