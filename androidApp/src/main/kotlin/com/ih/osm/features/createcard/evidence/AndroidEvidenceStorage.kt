package com.ih.osm.features.createcard.evidence

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.ih.osm.BuildConfig
import com.ih.osm.features.card.domain.create.CreateCardEvidenceDraft
import com.ih.osm.features.card.domain.model.CardEvidenceMediaType
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PendingEvidenceCapture(
    val id: String,
    val file: File,
    val uri: Uri,
    val mediaType: CardEvidenceMediaType,
    val mimeType: String,
)

class AndroidEvidenceStorage(context: Context) {
    private val appContext = context.applicationContext
    private val evidenceDirectory = File(appContext.filesDir, "card_evidence").apply { mkdirs() }
    private var audioRecorder: MediaRecorder? = null
    private var activeAudioCapture: PendingEvidenceCapture? = null

    fun createCapture(mediaType: CardEvidenceMediaType): PendingEvidenceCapture {
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

    suspend fun import(uri: Uri, mediaType: CardEvidenceMediaType): CreateCardEvidenceDraft =
        withContext(Dispatchers.IO) {
            val mimeType = appContext.contentResolver.getType(uri) ?: mediaType.defaultMimeType()
            require(mimeType in mediaType.allowedMimeTypes()) { "Unsupported evidence format." }
            val displayName = queryDisplayName(uri)
            val extension = displayName.substringAfterLast('.', missingDelimiterValue = "")
                .takeIf(String::isNotBlank)
                ?.let { ".$it" }
                ?: mimeType.defaultExtension(mediaType)
            val id = UUID.randomUUID().toString()
            val destination = File(evidenceDirectory, "$id$extension")
            try {
                appContext.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "Unable to open the selected evidence." }
                    destination.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            require(total <= MAX_FILE_SIZE_BYTES) { "Evidence exceeds the file size limit." }
                            output.write(buffer, 0, read)
                        }
                    }
                }
                destination.toDraft(id, displayName.ifBlank { destination.name }, mimeType, mediaType)
            } catch (error: Throwable) {
                destination.delete()
                throw error
            }
        }

    suspend fun finishCapture(capture: PendingEvidenceCapture): CreateCardEvidenceDraft =
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

    fun startAudioRecording(maxDurationSeconds: Long): PendingEvidenceCapture {
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

    suspend fun stopAudioRecording(): CreateCardEvidenceDraft {
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

    fun cancelAudioRecording() {
        val recorder = audioRecorder
        audioRecorder = null
        val capture = activeAudioCapture
        activeAudioCapture = null
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        capture?.file?.delete()
    }

    fun delete(localPath: String) {
        runCatching { File(localPath).delete() }
    }

    fun discard(capture: PendingEvidenceCapture?) {
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

    private fun queryDisplayName(uri: Uri): String {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        return appContext.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
        }.orEmpty()
    }

    private companion object {
        const val MAX_FILE_SIZE_BYTES = 25L * 1024L * 1024L
        const val RECORDING_GUARD_SECONDS = 2L
    }
}

private fun CardEvidenceMediaType.allowedMimeTypes(): Set<String> = when (this) {
    CardEvidenceMediaType.IMAGE -> setOf("image/jpeg", "image/png", "image/webp")
    CardEvidenceMediaType.VIDEO -> setOf("video/mp4", "video/quicktime", "video/webm")
    CardEvidenceMediaType.AUDIO -> setOf(
        "audio/mp4",
        "audio/mpeg",
        "audio/wav",
        "audio/x-wav",
        "audio/webm",
        "audio/aac",
    )
}

private fun CardEvidenceMediaType.defaultMimeType(): String = when (this) {
    CardEvidenceMediaType.IMAGE -> "image/jpeg"
    CardEvidenceMediaType.VIDEO -> "video/mp4"
    CardEvidenceMediaType.AUDIO -> "audio/mp4"
}

private fun String.defaultExtension(mediaType: CardEvidenceMediaType): String = when {
    contains("jpeg") || contains("jpg") -> ".jpg"
    contains("png") -> ".png"
    contains("webp") -> ".webp"
    contains("quicktime") -> ".mov"
    contains("video") -> ".mp4"
    contains("mpeg") -> ".mp3"
    contains("wav") -> ".wav"
    contains("audio") -> ".m4a"
    else -> when (mediaType) {
        CardEvidenceMediaType.IMAGE -> ".jpg"
        CardEvidenceMediaType.VIDEO -> ".mp4"
        CardEvidenceMediaType.AUDIO -> ".m4a"
    }
}
