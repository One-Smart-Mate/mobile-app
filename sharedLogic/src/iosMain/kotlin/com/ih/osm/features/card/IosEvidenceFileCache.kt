@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.ih.osm.features.card

import com.ih.osm.features.card.domain.model.CardEvidence
import com.ih.osm.features.card.domain.model.CardEvidenceMediaType
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite

internal object IosEvidenceFileCache {
    private const val DIRECTORY_NAME = "card_evidence"
    private const val MAX_FILE_BYTES = 25 * 1024 * 1024
    private const val MAX_CACHE_BYTES = 300L * 1024L * 1024L

    fun resolveExisting(evidence: CardEvidence): String? {
        if (isValidFile(evidence.url)) return evidence.url
        val cached = cachePath(evidence)
        return cached.takeIf(::isValidFile)?.also(::touch)
    }

    fun store(evidence: CardEvidence, bytes: ByteArray): String {
        require(bytes.isNotEmpty()) { "The evidence is empty." }
        require(bytes.size <= MAX_FILE_BYTES) { "The evidence exceeds the cache limit." }
        val destination = cachePath(evidence)
        val temporary = "$destination.tmp"
        val file = fopen(temporary, "wb") ?: error("Unable to create the evidence cache file.")
        val written = try {
            bytes.usePinned { pinned ->
                fwrite(pinned.addressOf(0), 1.convert(), bytes.size.convert(), file).toLong()
            }
        } finally {
            fclose(file)
        }
        check(written == bytes.size.toLong()) { "Unable to cache the evidence." }
        val manager = NSFileManager.defaultManager
        manager.removeItemAtPath(destination, error = null)
        check(manager.moveItemAtPath(temporary, destination, error = null)) {
            "Unable to finalize the evidence cache."
        }
        touch(destination)
        trim()
        return destination
    }

    fun adopt(evidence: CardEvidence, localPath: String) {
        if (!isValidFile(localPath)) return
        val destination = cachePath(evidence)
        val manager = NSFileManager.defaultManager
        manager.removeItemAtPath(destination, error = null)
        if (!manager.copyItemAtPath(localPath, destination, error = null)) return
        touch(destination)
        trim()
    }

    private fun cachePath(evidence: CardEvidence): String {
        val extension = when (evidence.mediaType) {
            CardEvidenceMediaType.IMAGE -> "jpg"
            CardEvidenceMediaType.VIDEO -> "mp4"
            CardEvidenceMediaType.AUDIO -> "m4a"
        }
        val safeName = "${evidence.cardUuid}-${evidence.id}"
            .map { character -> if (character.isLetterOrDigit() || character == '-') character else '_' }
            .joinToString("")
        return "${cacheDirectory()}/$safeName.$extension"
    }

    private fun cacheDirectory(): String {
        val root = NSSearchPathForDirectoriesInDomains(
            NSCachesDirectory,
            NSUserDomainMask,
            true,
        ).firstOrNull() as? String ?: "."
        val directory = "$root/$DIRECTORY_NAME"
        NSFileManager.defaultManager.createDirectoryAtPath(
            directory,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        return directory
    }

    private fun isValidFile(path: String): Boolean =
        NSFileManager.defaultManager.contentsAtPath(path)?.length?.toLong()?.let { it > 0L } == true

    private fun touch(path: String) {
        NSFileManager.defaultManager.setAttributes(
            mapOf(NSFileModificationDate to NSDate()),
            ofItemAtPath = path,
            error = null,
        )
    }

    private fun trim() {
        val directory = cacheDirectory()
        val manager = NSFileManager.defaultManager
        val files = manager.contentsOfDirectoryAtPath(directory, error = null)
            ?.mapNotNull { it as? String }
            .orEmpty()
            .mapNotNull { name ->
                val path = "$directory/$name"
                val attributes = manager.attributesOfItemAtPath(path, error = null) ?: return@mapNotNull null
                val size = (attributes[NSFileSize] as? NSNumber)?.longLongValue ?: return@mapNotNull null
                val modified = (attributes[NSFileModificationDate] as? NSDate)?.timeIntervalSinceReferenceDate ?: 0.0
                CacheEntry(path, size, modified)
            }
            .sortedByDescending(CacheEntry::modifiedAt)
        var retainedBytes = 0L
        files.forEach { file ->
            retainedBytes += file.size
            if (retainedBytes > MAX_CACHE_BYTES) {
                manager.removeItemAtPath(file.path, error = null)
            }
        }
    }

    private data class CacheEntry(
        val path: String,
        val size: Long,
        val modifiedAt: Double,
    )
}
