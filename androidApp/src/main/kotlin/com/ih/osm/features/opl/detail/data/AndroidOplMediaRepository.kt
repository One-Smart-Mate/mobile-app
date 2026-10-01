package com.ih.osm.features.opl.detail.data

import android.content.Context
import android.net.Uri
import com.ih.osm.core.auth.TokenStorage
import com.ih.osm.core.config.AppConfig
import com.ih.osm.features.auth.domain.session.SessionRepository
import com.ih.osm.features.opl.detail.domain.OplMediaRepository
import com.ih.osm.features.opl.domain.model.Opl
import com.ih.osm.features.opl.domain.model.OplContent
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AndroidOplMediaRepository(
    context: Context,
    private val config: AppConfig,
    private val session: SessionRepository,
    private val tokens: TokenStorage,
) : OplMediaRepository {
    private val context = context.applicationContext
    private val mutex = Mutex()

    override suspend fun resolve(opl: Opl, content: OplContent): File = withContext(Dispatchers.IO) {
        val user = authorizedUser(opl)
        require(content.oplId == opl.id && content.siteId == opl.siteId && content in opl.content)
        val reference = requireNotNull(content.mediaUrl?.takeIf(String::isNotBlank))
        val base = URI(config.baseUrl)
        val url = base.resolve(reference)
        validateUrl(url, base)
        // Cache isolation: environment, session, site, lesson and content revision.
        val key = "$base|$user|${opl.siteId}|${opl.id}|${content.id}|${content.updatedAt}|$reference"
        val hash = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val directory = File(context.cacheDir, "opl_media").apply { mkdirs() }
        val cached = File(directory, hash)
        mutex.withLock {
            if (!cached.isFile || cached.length() == 0L) {
                val temporary = File.createTempFile("opl-", ".part", directory)
                try {
                    fetch(url, base, temporary)
                    check(authorizedUser(opl) == user) { "Session changed during media download" }
                    check(temporary.renameTo(cached)) { "Cannot commit downloaded media" }
                } finally {
                    temporary.delete()
                }
            }
            check(authorizedUser(opl) == user) { "Session changed during media request" }
            cached
        }
    }

    override suspend fun export(opl: Opl, siteName: String, destination: Uri): Unit = withContext<Unit>(Dispatchers.IO) {
        val user = authorizedUser(opl)
        val attachments = mutableListOf<Pair<OplContent, File>>()
        var totalBytes = 0L
        opl.content.filter { !it.mediaUrl.isNullOrBlank() }.forEach {
            val file = resolve(opl, it)
            totalBytes += file.length()
            require(totalBytes <= MAX_EXPORT_BYTES) { "OPL export is too large" }
            attachments += it to file
        }
        val archive = File.createTempFile("opl-export-", ".zip", context.cacheDir)
        try {
            ZipOutputStream(archive.outputStream().buffered()).use { zip ->
                zip.putNextEntry(ZipEntry("OPL.txt"))
                zip.write(buildString {
                    appendLine(opl.title)
                    appendLine("One Point Lesson · $siteName")
                    appendLine("ID: ${opl.id}")
                    appendLine("Tipo: ${opl.typeName.orEmpty()}")
                    appendLine("Objetivo: ${opl.objective.orEmpty()}")
                    appendLine("Creado por: ${opl.creatorName.orEmpty()}")
                    appendLine("Revisado por: ${opl.reviewerName.orEmpty()}")
                    appendLine("Creado: ${opl.createdAt.orEmpty()}")
                    appendLine("Actualizado: ${opl.updatedAt.orEmpty()}")
                    appendLine("Uso CILT: ${opl.ciltUsageCount ?: 0} · Uso directo: ${opl.directUsageCount ?: 0}")
                    appendLine("Último uso: ${opl.lastUsedAt.orEmpty()}")
                    appendLine("Ubicaciones:")
                    opl.levels.forEach { appendLine("- ${it.name} ${it.machineId.orEmpty()} ${it.description.orEmpty()}") }
                    appendLine("\nContenido (orden de web):")
                    opl.content.sortedWith(compareBy<OplContent> { it.order }.thenBy { it.id }).forEach {
                        appendLine("\n${it.order}. ${it.typeCode} (${it.id})")
                        it.text?.let { text -> appendLine(text) }
                        if (!it.mediaUrl.isNullOrBlank()) appendLine("Archivo: archivos/${it.id}-${fileName(it)}")
                    }
                }.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                attachments.forEach { (content, file) ->
                    currentCoroutineContext().ensureActive()
                    zip.putNextEntry(ZipEntry("archivos/${content.id}-${fileName(content)}"))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            check(authorizedUser(opl) == user) { "Session changed during export" }
            context.contentResolver.openOutputStream(destination, "wt")?.use { output ->
                archive.inputStream().use { it.copyTo(output) }
            } ?: error("Cannot open export destination")
        } finally {
            archive.delete()
        }
    }

    private suspend fun authorizedUser(opl: Opl): Long {
        val user = session.currentUser() ?: error("Session expired")
        check(user.sites.any { it.id == opl.siteId }) { "Site access denied" }
        return user.id
    }

    private suspend fun fetch(initial: URI, base: URI, destination: File) {
        var url = initial
        repeat(6) {
            currentCoroutineContext().ensureActive()
            validateUrl(url, base)
            val connection = url.toURL().openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                // Never forward app credentials to public storage or cross-origin redirects.
                if (sameOrigin(url, base)) tokens.getToken()?.let {
                    connection.setRequestProperty("Authorization", "Bearer $it")
                }
                when (connection.responseCode) {
                    301, 302, 303, 307, 308 -> {
                        val next = url.resolve(connection.getHeaderField("Location") ?: error("Missing redirect"))
                        require(url.scheme != "https" || next.scheme == "https") { "Insecure redirect" }
                        url = next
                    }
                    in 200..299 -> {
                        require(connection.contentLengthLong <= MAX_FILE_BYTES) { "OPL file exceeds download limit" }
                        var total = 0L
                        connection.inputStream.use { input ->
                            destination.outputStream().buffered().use { output ->
                                val buffer = ByteArray(16 * 1024)
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    total += count
                                    require(total <= MAX_FILE_BYTES) { "OPL file exceeds download limit" }
                                    output.write(buffer, 0, count)
                                }
                            }
                        }
                        check(total > 0) { "Empty OPL file" }
                        return
                    }
                    else -> error("OPL file request failed (${connection.responseCode})")
                }
            } finally {
                connection.disconnect()
            }
        }
        error("Too many media redirects")
    }

    private fun validateUrl(url: URI, base: URI) {
        require(url.host != null && url.userInfo == null)
        require(url.scheme == "https" || (url.scheme == "http" && sameOrigin(url, base))) {
            "OPL media must use HTTPS"
        }
    }

    private fun sameOrigin(first: URI, second: URI): Boolean =
        first.scheme.equals(second.scheme, true) && first.host.equals(second.host, true) &&
            effectivePort(first) == effectivePort(second)

    private fun effectivePort(uri: URI): Int = if (uri.port >= 0) uri.port else if (uri.scheme == "https") 443 else 80

    private companion object {
        const val MAX_FILE_BYTES = 100L * 1024 * 1024
        const val MAX_EXPORT_BYTES = 256L * 1024 * 1024
    }
}

internal fun fileName(content: OplContent): String {
    val name = runCatching { Uri.parse(content.mediaUrl).lastPathSegment }.getOrNull()
        ?.takeIf(String::isNotBlank) ?: "${content.typeCode}-${content.id}"
    return name.replace(Regex("[^\\p{L}\\p{N}._ -]"), "_").take(120)
}
