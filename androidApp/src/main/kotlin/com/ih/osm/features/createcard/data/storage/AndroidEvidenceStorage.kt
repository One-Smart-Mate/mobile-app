package com.ih.osm.features.createcard.data.storage

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import androidx.core.content.FileProvider
import com.ih.osm.BuildConfig
import com.ih.osm.features.card.domain.create.CreateCardEvidenceDraft
import com.ih.osm.features.card.domain.model.CardEvidenceMediaType
import com.ih.osm.features.createcard.domain.storage.EvidenceStorage
import com.ih.osm.features.createcard.domain.storage.PendingEvidenceCapture
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidEvidenceStorage(context: Context) : EvidenceStorage {
    private val appContext = context.applicationContext
    private val evidenceDirectory = File(appContext.filesDir, "card_evidence").apply { mkdirs() }
    private var audioRecorder: MediaRecorder? = null
    private var activeAudioCapture: PendingEvidenceCapture? = null

    override fun createCapture(mediaType: CardEvidenceMediaType): PendingEvidenceCapture {
        val id = UUID.randomUUID().toString()
        val mimeType = when (mediaType) {
            CardEvidenceMediaType.IMAGE -> "image/jpeg"
            CardEvidenceMediaType.VIDEO -> "video/mp4"
            CardEvidenceMediaType.AUDIO -> "audio/mp4"
        }
        val extension = when (mediaType) {
            CardEvidenceMediaType.IMAGE -> ".jpg"
            CardEvidenceMediaType.VIDEO -> ".mp4"
            CardEvidenceMediaType.AUDIO -> ".m4a"
        }
        val file = File(evidenceDirectory, "$id$extension")
        return PendingEvidenceCapture(
            id = id,
            file = file,
            uri = FileProvider.getUriForFile(
                appContext,
                "${BuildConfig.APPLICATION_ID}.fileprovider",
                file,
            ),
            mediaType = mediaType,
            mimeType = mimeType,
        )
    }

    override suspend fun finishCapture(capture: PendingEvidenceCapture): CreateCardEvidenceDraft =
        withContext(Dispatchers.IO) {
            require(capture.file.isFile && capture.file.length() > 0L) {
                "The captured evidence is empty."
            }
            capture.file.toDraft(
                id = capture.id,
                displayName = capture.file.name,
                mimeType = capture.mimeType,
                mediaType = capture.mediaType,
            )
        }

    override fun startAudioRecording(maxDurationSeconds: Long): PendingEvidenceCapture {
        cancelAudioRecording()
        val capture = createCapture(CardEvidenceMediaType.AUDIO)
        val recorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            MediaRecorder(appContext)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        try {
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128_000)
                setAudioSamplingRate(44_100)
                if (maxDurationSeconds > 0) {
                    // The UI stops exactly at the catalog limit. This slightly larger
                    // guard only protects recordings if the composable is interrupted.
                    setMaxDuration(
                        ((maxDurationSeconds + RECORDING_GUARD_SECONDS) * 1_000L)
                            .coerceAtMost(Int.MAX_VALUE.toLong())
                            .toInt(),
                    )
                }
                setOutputFile(capture.file.absolutePath)
                prepare()
                start()
            }
        } catch (error: Throwable) {
            recorder.release()
            capture.file.delete()
            throw error
        }
        audioRecorder = recorder
        activeAudioCapture = capture
        return capture
    }

    override suspend fun stopAudioRecording(): CreateCardEvidenceDraft {
        val capture = requireNotNull(activeAudioCapture) { "There is no active audio recording." }
        val recorder = requireNotNull(audioRecorder)
        audioRecorder = null
        activeAudioCapture = null
        try {
            recorder.stop()
        } catch (error: Throwable) {
            capture.file.delete()
            throw error
        } finally {
            recorder.release()
        }
        return finishCapture(capture)
    }

    override fun cancelAudioRecording() {
        val recorder = audioRecorder
        audioRecorder = null
        val capture = activeAudioCapture
        activeAudioCapture = null
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        capture?.file?.delete()
    }

    override fun delete(localPath: String) {
        runCatching { File(localPath).delete() }
    }

    override fun discard(capture: PendingEvidenceCapture?) {
        capture?.file?.delete()
    }

    private fun File.toDraft(
        id: String,
        displayName: String,
        mimeType: String,
        mediaType: CardEvidenceMediaType,
    ) = CreateCardEvidenceDraft(
        id = id,
        localPath = absolutePath,
        displayName = displayName,
        mimeType = mimeType,
        mediaType = mediaType,
        durationMillis = if (mediaType == CardEvidenceMediaType.IMAGE) 0 else mediaDuration(absolutePath),
        sizeBytes = length(),
    )

    private fun mediaDuration(path: String): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } finally {
            retriever.release()
        }
    }

    private companion object {
        const val RECORDING_GUARD_SECONDS = 2L
    }
}
