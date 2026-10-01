package com.ih.osm.features.opl.data.repository

import com.ih.osm.core.config.AppConfig
import com.ih.osm.core.network.NetworkError
import com.ih.osm.core.network.NetworkErrorKind
import com.ih.osm.core.network.NetworkResult
import com.ih.osm.features.auth.domain.session.SessionRepository
import com.ih.osm.features.auth.domain.session.SessionStatus
import com.ih.osm.features.level.domain.repository.LevelRepository
import com.ih.osm.features.opl.data.local.OplLocalDataSource
import com.ih.osm.features.opl.data.local.OplLocalScope
import com.ih.osm.features.opl.data.remote.OplApiService
import com.ih.osm.features.opl.data.remote.OplDto
import com.ih.osm.features.opl.data.remote.OplMediaApiService
import com.ih.osm.features.opl.domain.model.Opl
import com.ih.osm.features.opl.domain.model.OplContentType
import com.ih.osm.features.opl.domain.model.OplDownloadProgress
import com.ih.osm.features.opl.domain.model.OplDownloadStage
import com.ih.osm.features.opl.domain.repository.OplRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random

internal class OplRepositoryImpl(
    private val api: OplApiService,
    private val sessionRepository: SessionRepository,
    private val levelRepository: LevelRepository,
    private val local: OplLocalDataSource,
    private val media: OplMediaApiService,
    private val config: AppConfig,
) : OplRepository {
    // Serializes replacement/deletion and chunk reads so a reader never mixes generations.
    private val mutationMutex = Mutex()

    override suspend fun getBySite(siteId: Long): NetworkResult<List<Opl>> = withSiteAccess(siteId) {
        combineWithLocal(api.getBySite(siteId).toDomainList(siteId), scope(siteId)) { true }
    }

    override suspend fun search(siteId: Long, query: String): NetworkResult<List<Opl>> {
        val normalized = query.trim()
        if (normalized.isEmpty()) return getBySite(siteId)
        if (normalized.length > MAXIMUM_QUERY_LENGTH) return clientFailure("OPL search cannot exceed 100 characters.", 400)
        return withSiteAccess(siteId) {
            combineWithLocal(api.search(siteId, normalized).toDomainList(siteId), scope(siteId)) { opl ->
                opl.title.contains(normalized, true) || opl.levels.any { it.name.contains(normalized, true) }
            }
        }
    }

    override suspend fun getByLevel(siteId: Long, levelId: String): NetworkResult<List<Opl>> = withSiteAccess(siteId) {
        val id = levelId.trim().toLongOrNull()?.takeIf { it > 0 }
            ?: return@withSiteAccess clientFailure("Invalid LEVEL identifier.", 400)
        if (levelRepository.getAll(siteId).none { it.id.toLongOrNull() == id }) {
            return@withSiteAccess clientFailure("The LEVEL is not in this site's synchronized catalog.", 400)
        }
        combineWithLocal(api.getByLevel(id).toDomainList(siteId), scope(siteId)) { opl ->
            opl.levels.any { it.id.toLongOrNull() == id }
        }
    }

    override suspend fun getById(siteId: Long, oplId: Long): NetworkResult<Opl> = withSiteAccess(siteId) {
        if (oplId <= 0) return@withSiteAccess clientFailure("Invalid OPL identifier.", 400)
        // A complete download is authoritative. Do not contact the network in this branch.
        val record = local.record(scope(siteId), oplId)
        if (record != null) NetworkResult.Success(local.decode(record), 200) else remoteLesson(siteId, oplId)
    }

    override suspend fun getDownloaded(siteId: Long): NetworkResult<List<Opl>> = withSiteAccess(siteId) {
        NetworkResult.Success(local.lessons(scope(siteId)), 200)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeDownloadedIds(siteId: Long): Flow<Set<Long>> = sessionRepository.status.flatMapLatest { status ->
        val user = (status as? SessionStatus.Authenticated)?.user
        if (user == null || user.sites.none { it.id == siteId }) flowOf(emptySet())
        else local.observeIds(OplLocalScope(user.id, config.baseUrl, siteId))
    }

    override suspend fun download(
        siteId: Long,
        oplId: Long,
        onProgress: (OplDownloadProgress) -> Unit,
    ): NetworkResult<Opl> = withSiteAccess(siteId) {
        mutationMutex.withLock {
            val scope = scope(siteId)
            val generation = Random.nextLong().toString(16) + Random.nextLong().toString(16)
            var committed = false
            try {
                onProgress(OplDownloadProgress(OplDownloadStage.INFORMATION, 0f))
                // Unlike getById, an explicit download always fetches the newest server version.
                val result = remoteLesson(siteId, oplId)
                if (result is NetworkResult.Failure) return@withLock result
                val opl = (result as NetworkResult.Success).data
                val attachments = opl.content.filter { !it.mediaUrl.isNullOrBlank() }
                require(opl.content.none { it.type != OplContentType.TEXT && it.mediaUrl.isNullOrBlank() }) {
                    "An OPL attachment has no media URL"
                }
                local.begin(scope, opl, generation)
                var downloadedBytes = 0L
                attachments.forEachIndexed { index, content ->
                    currentCoroutineContext().ensureActive()
                    var chunkIndex = 0L
                    val bytesBeforeFile = downloadedBytes
                    val read = media.read(requireNotNull(content.mediaUrl), onChunk = { bytes ->
                        downloadedBytes += bytes.size
                        require(downloadedBytes <= MAX_LESSON_BYTES) { "OPL exceeds the offline storage limit" }
                        local.append(scope, opl.id, generation, content.id, chunkIndex++, bytes)
                    }, onBytes = { bytes, total ->
                        val fraction = if (total != null) (bytes.toDouble() / total).coerceIn(0.0, 1.0).toFloat() else 0f
                        onProgress(OplDownloadProgress(
                            OplDownloadStage.MEDIA, 0.05f + 0.9f * (index + fraction) / attachments.size,
                            index, attachments.size, bytesBeforeFile + bytes,
                        ))
                    })
                    if (read is NetworkResult.Failure) return@withLock read
                    check(chunkIndex > 0)
                    assertScope(scope)
                    onProgress(OplDownloadProgress(OplDownloadStage.MEDIA,
                        0.05f + 0.9f * (index + 1f) / attachments.size, index + 1, attachments.size, downloadedBytes))
                }
                onProgress(OplDownloadProgress(OplDownloadStage.SAVING, 0.95f,
                    attachments.size, attachments.size, downloadedBytes))
                currentCoroutineContext().ensureActive()
                assertScope(scope)
                local.commit(scope, opl.id, generation)
                committed = true
                onProgress(OplDownloadProgress(OplDownloadStage.COMPLETE, 1f,
                    attachments.size, attachments.size, downloadedBytes))
                NetworkResult.Success(opl.copy(isDownloaded = true, downloadRevision = generation), 200)
            } finally {
                if (!committed) withContext(NonCancellable) { local.discard(scope, oplId, generation) }
            }
        }
    }

    override suspend fun deleteDownloaded(siteId: Long, oplId: Long): NetworkResult<Unit> = withSiteAccess(siteId) {
        mutationMutex.withLock {
            local.delete(scope(siteId), oplId)
            NetworkResult.Success(Unit, 200)
        }
    }

    override suspend fun readMedia(
        siteId: Long,
        oplId: Long,
        contentId: Long,
        onChunk: (ByteArray) -> Unit,
    ): NetworkResult<Unit> = withSiteAccess(siteId) {
        mutationMutex.withLock {
            val scope = scope(siteId)
            val record = local.record(scope, oplId)
            if (record != null) {
                val opl = local.decode(record)
                require(opl.content.any { it.id == contentId && !it.mediaUrl.isNullOrBlank() })
                val count = local.chunks(scope, oplId, record.generation, contentId)
                check(count > 0) { "Offline OPL evidence is incomplete" }
                for (index in 0 until count) {
                    currentCoroutineContext().ensureActive()
                    onChunk(local.chunk(scope, oplId, record.generation, contentId, index))
                }
                assertScope(scope)
                NetworkResult.Success(Unit, 200)
            } else {
                val result = remoteLesson(siteId, oplId)
                if (result is NetworkResult.Failure) return@withLock result
                val opl = (result as NetworkResult.Success).data
                val content = opl.content.firstOrNull { it.id == contentId }
                    ?: return@withLock clientFailure("OPL content not found.", 404)
                val reference = content.mediaUrl?.takeIf(String::isNotBlank)
                    ?: return@withLock clientFailure("OPL media URL is missing.", 404)
                media.read(reference, onChunk)
            }
        }
    }

    private suspend fun scope(siteId: Long) = OplLocalScope(
        requireNotNull(sessionRepository.currentUser()).id, config.baseUrl, siteId,
    )

    private suspend fun assertScope(scope: OplLocalScope) {
        val user = sessionRepository.currentUser()
        check(user?.id == scope.ownerId && user.sites.any { it.id == scope.siteId } && config.baseUrl == scope.apiBase) {
            "Session or API environment changed during OPL download"
        }
    }

    private suspend fun remoteLesson(siteId: Long, oplId: Long): NetworkResult<Opl> {
        if (oplId <= 0) return clientFailure("Invalid OPL identifier.", 400)
        return api.getById(oplId).mapData { dto ->
            dto.toDomain().also {
                if (it.siteId != siteId || it.id != oplId) throw SerializationException("OPL response does not match lesson/site.")
            }
        }
    }

    private fun combineWithLocal(
        result: NetworkResult<List<Opl>>,
        scope: OplLocalScope,
        matches: (Opl) -> Boolean,
    ): NetworkResult<List<Opl>> {
        val downloaded = local.lessons(scope).filter(matches)
        return when (result) {
            is NetworkResult.Success -> {
                val snapshots = downloaded.associateBy { it.id }
                NetworkResult.Success((result.data.map { snapshots[it.id] ?: it } + downloaded)
                    .distinctBy { it.id }.sortedWith(compareBy<Opl> { it.order }.thenBy { it.id }), result.statusCode)
            }
            is NetworkResult.Failure -> if (downloaded.isNotEmpty() &&
                (result.error.kind == NetworkErrorKind.CONNECTIVITY || result.error.statusCode == 404)) {
                NetworkResult.Success(downloaded, 200)
            } else result
        }
    }

    private suspend fun <T> withSiteAccess(siteId: Long, read: suspend () -> NetworkResult<T>): NetworkResult<T> {
        try {
            if (siteId <= 0) return clientFailure("Invalid site identifier.", 400)
            val user = sessionRepository.currentUser() ?: return clientFailure("User not authenticated.", 401)
            if (user.sites.none { it.id == siteId }) return clientFailure("Site access denied.", 403)
            val base = config.baseUrl
            val result = read()
            if (result is NetworkResult.Failure) return result
            val current = sessionRepository.currentUser()
            if (current?.id != user.id || config.baseUrl != base) return clientFailure("Session changed during OPL request.", 401)
            if (current.sites.none { it.id == siteId }) return clientFailure("Site access denied.", 403)
            return result
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: SerializationException) {
            return NetworkResult.Failure(NetworkError(NetworkErrorKind.SERIALIZATION, error.message ?: "Invalid OPL data."))
        } catch (error: Exception) {
            return NetworkResult.Failure(NetworkError(NetworkErrorKind.UNKNOWN, error.message ?: "OPL storage operation failed."))
        }
    }

    private fun NetworkResult<List<OplDto>>.toDomainList(siteId: Long): NetworkResult<List<Opl>> = mapData { items ->
        items.map { it.toDomain() }.also { opls ->
            if (opls.any { it.siteId != siteId }) throw SerializationException("OPL response contains lessons from another site.")
        }.distinctBy { it.id }.sortedWith(compareBy<Opl> { it.order }.thenBy { it.id })
    }

    private inline fun <T, R> NetworkResult<T>.mapData(mapper: (T) -> R): NetworkResult<R> = when (this) {
        is NetworkResult.Failure -> this
        is NetworkResult.Success -> try { NetworkResult.Success(mapper(data), statusCode) }
        catch (error: SerializationException) {
            NetworkResult.Failure(NetworkError(NetworkErrorKind.SERIALIZATION, error.message ?: "Invalid OPL data.", statusCode))
        }
    }

    private fun clientFailure(message: String, statusCode: Int) =
        NetworkResult.Failure(NetworkError(NetworkErrorKind.CLIENT, message, statusCode))

    private companion object {
        const val MAXIMUM_QUERY_LENGTH = 100
        const val MAX_LESSON_BYTES = 256L * 1024 * 1024
    }
}
