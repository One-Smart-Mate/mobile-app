@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.ih.osm.features.opl

import com.ih.osm.core.config.AppConfig
import com.ih.osm.core.network.NetworkResult
import com.ih.osm.features.auth.domain.session.SessionRepository
import com.ih.osm.features.opl.domain.model.OplContentType
import com.ih.osm.features.opl.domain.repository.OplRepository
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.CoreCrypto.CC_SHA256
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite
import kotlin.random.Random

/** Disposable files for native PDFKit/AVKit. Persistent source of truth is shared SQLite, not this cache. */
internal object IosOplMediaCache {
    private val mutex = Mutex()

    suspend fun resolve(
        repository: OplRepository, session: SessionRepository, config: AppConfig,
        siteId: Long, oplId: Long, contentId: Long,
    ): IosOplMediaOutcome = mutex.withLock {
        try {
            val owner = authorizedOwner(session, siteId)
            val base = config.baseUrl
            val outcome = repository.getById(siteId, oplId)
            if (outcome is NetworkResult.Failure) return@withLock IosOplMediaOutcome(null, outcome.error.message)
            val opl = (outcome as NetworkResult.Success).data
            val content = opl.content.firstOrNull { it.id == contentId } ?: error("OPL content not found")
            require(!content.mediaUrl.isNullOrBlank())
            val directory = lessonDirectory(owner, base, siteId, oplId)
            val manager = NSFileManager.defaultManager
            check(manager.createDirectoryAtPath(directory, true, null, null))
            val extension = when (content.type) {
                OplContentType.PDF -> "pdf"
                OplContentType.IMAGE -> "jpg"
                OplContentType.VIDEO -> "mp4"
                else -> "m4a"
            }
            val revision = opl.downloadRevision ?: "remote-${content.updatedAt}-${content.mediaUrl}"
            val destination = "$directory/$contentId-${key(revision)}.$extension"
            if (!validFile(destination)) {
                val temporary = "$destination-${Random.nextLong()}.part"
                val file = fopen(temporary, "wb") ?: error("Unable to create OPL media file")
                var closed = false
                try {
                    val result = repository.readMedia(siteId, oplId, contentId) { bytes ->
                        val written = bytes.usePinned { pinned ->
                            fwrite(pinned.addressOf(0), 1.convert(), bytes.size.convert(), file).toLong()
                        }
                        check(written == bytes.size.toLong()) { "Insufficient storage for OPL media" }
                    }
                    val closeStatus = fclose(file)
                    closed = true
                    check(closeStatus == 0)
                    if (result is NetworkResult.Failure) error(result.error.message)
                    check(authorizedOwner(session, siteId) == owner && config.baseUrl == base)
                    check(validFile(temporary))
                    manager.removeItemAtPath(destination, null)
                    check(manager.moveItemAtPath(temporary, destination, null))
                } finally {
                    if (!closed) fclose(file)
                    manager.removeItemAtPath(temporary, null)
                }
            }
            check(authorizedOwner(session, siteId) == owner && config.baseUrl == base)
            IosOplMediaOutcome(destination)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { IosOplMediaOutcome(null, error.message ?: "Unable to read OPL evidence") }
    }

    suspend fun clearLesson(session: SessionRepository, config: AppConfig, siteId: Long, oplId: Long) = mutex.withLock {
        val path = lessonDirectory(authorizedOwner(session, siteId), config.baseUrl, siteId, oplId)
        val manager = NSFileManager.defaultManager
        check(!manager.fileExistsAtPath(path) || manager.removeItemAtPath(path, null))
    }

    suspend fun clearAll() = mutex.withLock {
        val path = root()
        val manager = NSFileManager.defaultManager
        check(!manager.fileExistsAtPath(path) || manager.removeItemAtPath(path, null))
    }

    private suspend fun authorizedOwner(session: SessionRepository, siteId: Long): Long {
        val user = session.currentUser() ?: error("Session expired")
        check(user.sites.any { it.id == siteId })
        return user.id
    }

    private fun root(): String {
        val cache = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).firstOrNull() as? String
            ?: error("Cache directory unavailable")
        return "$cache/opl_media"
    }

    private fun lessonDirectory(owner: Long, base: String, site: Long, opl: Long) =
        "${root()}/user-$owner/${key(base)}/site-$site/opl-$opl"

    // Stable, collision-resistant keys isolate API environments and media revisions.
    private fun key(value: String): String {
        val input = value.encodeToByteArray()
        val digest = ByteArray(32)
        input.usePinned { source ->
            digest.usePinned { output ->
                CC_SHA256(if (input.isEmpty()) null else source.addressOf(0), input.size.convert(),
                    output.addressOf(0).reinterpret())
            }
        }
        return digest.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    }

    private fun validFile(path: String): Boolean =
        (NSFileManager.defaultManager.attributesOfItemAtPath(path, null)?.get(NSFileSize) as? NSNumber)
            ?.longLongValue?.let { it > 0 } == true
}
