package com.ih.osm.features.carddetail.data.cache

import android.content.Context
import com.ih.osm.core.network.NetworkResult
import com.ih.osm.features.card.domain.model.CardEvidence
import com.ih.osm.features.card.domain.model.CardEvidenceMediaType
import com.ih.osm.features.card.domain.repository.CardRepository
import com.ih.osm.features.carddetail.domain.cache.EvidenceCache
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class EvidenceFileCache(
    context: Context,
    private val repository: CardRepository,
) : EvidenceCache {
    private val directory = File(context.applicationContext.cacheDir, "card_evidence").apply { mkdirs() }
    private val mutex = Mutex()

    override suspend fun resolve(evidence: CardEvidence): Result<File> = withContext(Dispatchers.IO) {
        val localSource = File(evidence.url)
        if (localSource.isFile && localSource.length() > 0L) {
            return@withContext Result.success(localSource)
        }
        mutex.withLock {
            val cached = cacheFile(evidence.url, evidence.mediaType)
            if (cached.isFile && cached.length() > 0L) {
                cached.setLastModified(System.currentTimeMillis())
                return@withLock Result.success(cached)
            }
            when (val result = repository.downloadEvidence(evidence.siteId, evidence.url)) {
                is NetworkResult.Failure -> Result.failure(IllegalStateException(result.error.message))
                is NetworkResult.Success -> runCatching {
                    require(result.data.isNotEmpty()) { "The evidence is empty." }
                    require(result.data.size <= MAX_FILE_SIZE_BYTES) { "The evidence exceeds the cache limit." }
                    val temporary = File(directory, "${cached.name}.tmp")
                    temporary.writeBytes(result.data)
                    if (!temporary.renameTo(cached)) {
                        temporary.copyTo(cached, overwrite = true)
                        temporary.delete()
                    }
                    trim()
                    cached
                }
            }
        }
    }

    override suspend fun adopt(localFile: File, remoteReference: String, mediaType: CardEvidenceMediaType) {
        if (!localFile.isFile) return
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val cached = cacheFile(remoteReference, mediaType)
                if (!localFile.renameTo(cached)) {
                    localFile.copyTo(cached, overwrite = true)
                    localFile.delete()
                }
                cached.setLastModified(System.currentTimeMillis())
                trim()
            }
        }
    }

    private fun cacheFile(reference: String, mediaType: CardEvidenceMediaType): File {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(reference.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }
        val extension = when (mediaType) {
            CardEvidenceMediaType.IMAGE -> ".jpg"
            CardEvidenceMediaType.VIDEO -> ".mp4"
            CardEvidenceMediaType.AUDIO -> ".m4a"
        }
        return File(directory, "$hash$extension")
    }

    private fun trim() {
        val files = directory.listFiles()?.filter(File::isFile)?.sortedByDescending(File::lastModified).orEmpty()
        var retainedBytes = 0L
        files.forEach { file ->
            retainedBytes += file.length()
            if (retainedBytes > MAX_CACHE_BYTES) file.delete()
        }
    }

    private companion object {
        const val MAX_FILE_SIZE_BYTES = 25 * 1024 * 1024
        const val MAX_CACHE_BYTES = 300L * 1024L * 1024L
    }
}
