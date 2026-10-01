package com.ih.osm.features.opl.detail.data

import android.content.Context
import android.net.Uri
import com.ih.osm.core.config.AppConfig
import com.ih.osm.core.network.NetworkResult
import com.ih.osm.features.auth.domain.session.SessionRepository
import com.ih.osm.features.opl.detail.domain.OplMediaRepository
import com.ih.osm.features.opl.domain.model.Opl
import com.ih.osm.features.opl.domain.model.OplContent
import com.ih.osm.features.opl.domain.repository.OplRepository
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Materializes shared SQLite evidence streams as disposable files for Android media players/PdfRenderer. */
class AndroidOplMediaRepository(
    context: Context,
    private val config: AppConfig,
    private val session: SessionRepository,
    private val repository: OplRepository,
) : OplMediaRepository {
    private val context = context.applicationContext
    private val mutex = Mutex()

    override suspend fun resolve(opl: Opl, content: OplContent): File = withContext(Dispatchers.IO) {
        val user = authorizedUser(opl.siteId)
        val downloaded = when (val result = repository.getDownloaded(opl.siteId)) {
            is NetworkResult.Success -> result.data.firstOrNull { it.id == opl.id }
            is NetworkResult.Failure -> error(result.error.message)
        }
        val lesson = downloaded ?: opl
        val item = lesson.content.firstOrNull { it.id == content.id } ?: error("OPL media is no longer available")
        val directory = lessonDirectory(user, lesson.siteId, lesson.id).apply { mkdirs() }
        val key = "${lesson.downloadRevision}|${item.id}|${item.updatedAt}|${item.mediaUrl}"
        val cached = File(directory, hash(key))
        mutex.withLock {
            if (!cached.isFile || cached.length() == 0L) {
                val temporary = File.createTempFile("opl-", ".part", directory)
                try {
                    val result = temporary.outputStream().buffered().use { output ->
                        repository.readMedia(lesson.siteId, lesson.id, item.id) { output.write(it) }
                    }
                    if (result is NetworkResult.Failure) error(result.error.message)
                    check(authorizedUser(lesson.siteId) == user)
                    check(temporary.length() > 0 && temporary.renameTo(cached))
                } finally {
                    temporary.delete()
                }
            }
            check(authorizedUser(lesson.siteId) == user)
            cached
        }
    }

    override suspend fun clearResolvedFiles(siteId: Long, oplId: Long) = withContext(Dispatchers.IO) {
        val user = authorizedUser(siteId)
        require(oplId > 0)
        mutex.withLock {
            val directory = lessonDirectory(user, siteId, oplId)
            check(!directory.exists() || directory.deleteRecursively()) { "Unable to remove resolved OPL media" }
        }
    }

    private suspend fun authorizedUser(siteId: Long): Long {
        val user = session.currentUser() ?: error("Session expired")
        check(user.sites.any { it.id == siteId }) { "Site access denied" }
        return user.id
    }

    private fun lessonDirectory(user: Long, siteId: Long, oplId: Long): File =
        File(context.cacheDir, "opl_media/user-$user/${hash(config.baseUrl)}/site-$siteId/opl-$oplId")

    private fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
}

internal fun fileName(content: OplContent): String {
    val name = runCatching { Uri.parse(content.mediaUrl).lastPathSegment }.getOrNull()
        ?.takeIf(String::isNotBlank) ?: "${content.typeCode}-${content.id}"
    return name.replace(Regex("[^\\p{L}\\p{N}._ -]"), "_").take(120)
}
