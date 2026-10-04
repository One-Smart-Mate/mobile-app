package com.ih.osm.features.opl

import com.ih.osm.core.config.AppConfig
import com.ih.osm.core.network.NetworkResult
import com.ih.osm.features.auth.domain.session.SessionRepository
import com.ih.osm.features.level.domain.model.Level
import com.ih.osm.features.level.domain.repository.LevelRepository
import com.ih.osm.features.opl.domain.model.Opl
import com.ih.osm.features.opl.domain.model.OplDownloadProgress
import com.ih.osm.features.opl.domain.repository.OplRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

data class IosOplListOutcome(val opls: List<Opl>, val errorKind: String? = null, val statusCode: Int = 0)
data class IosOplDetailOutcome(val opl: Opl?, val errorKind: String? = null, val statusCode: Int = 0)
data class IosOplLevelsOutcome(val levels: List<Level>, val succeeded: Boolean)
data class IosOplOperationOutcome(val succeeded: Boolean, val errorMessage: String? = null)
data class IosOplMediaOutcome(val path: String?, val errorMessage: String? = null)

/** Logout waits for cancelled OPL work before removing offline data. */
@OptIn(ExperimentalAtomicApi::class)
internal object IosOplOperations {
    private val root = SupervisorJob()
    private val paused = AtomicBoolean(false)

    fun context(): CoroutineContext {
        return SupervisorJob(root).also { if (paused.load()) it.cancel() } + Dispatchers.Default
    }

    @Throws(Exception::class)
    suspend fun cancelAll() {
        paused.store(true)
        val jobs = root.children.toList()
        jobs.forEach { it.cancel() }
        jobs.forEach { it.join() }
    }

    fun resume() { paused.store(false) }
}

/** Thin Swift interop adapter. Swift owns UI state; shared repositories own authorization and offline rules. */
class IosOplController(
    private val repository: OplRepository,
    private val levels: LevelRepository,
    private val session: SessionRepository,
    private val config: AppConfig,
) {
    private var scope = CoroutineScope(IosOplOperations.context())

    private fun workerContext(): CoroutineContext {
        if (!scope.isActive) scope = CoroutineScope(IosOplOperations.context())
        return scope.coroutineContext
    }
    private var observation: Job? = null

    @Throws(Exception::class)
    suspend fun getLevels(siteId: Long): IosOplLevelsOutcome = withContext(workerContext()) {
        val user = session.currentUser()
        if (user == null || user.sites.none { it.id == siteId }) return@withContext IosOplLevelsOutcome(emptyList(), false)
        IosOplLevelsOutcome(levels.getAll(siteId), true)
    }

    @Throws(Exception::class)
    suspend fun search(siteId: Long, query: String): IosOplListOutcome = withContext(workerContext()) {
        listOutcome(repository.search(siteId, query))
    }

    @Throws(Exception::class)
    suspend fun getByLevel(siteId: Long, levelId: String): IosOplListOutcome = withContext(workerContext()) {
        listOutcome(repository.getByLevel(siteId, levelId))
    }

    @Throws(Exception::class)
    suspend fun getDownloaded(siteId: Long): IosOplListOutcome = withContext(workerContext()) {
        listOutcome(repository.getDownloaded(siteId))
    }

    fun observeDownloads(siteId: Long, onChanged: (List<Opl>) -> Unit) {
        observation?.cancel()
        workerContext()
        observation = scope.launch {
            try {
                repository.observeDownloadedIds(siteId).collect {
                    val result = repository.getDownloaded(siteId)
                    withContext(Dispatchers.Main) {
                        onChanged((result as? NetworkResult.Success)?.data.orEmpty())
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Keep the last visible snapshots until a retry. */ }
        }
    }

    @Throws(Exception::class)
    suspend fun getById(siteId: Long, oplId: Long): IosOplDetailOutcome = withContext(workerContext()) {
        detailOutcome(repository.getById(siteId, oplId))
    }

    @Throws(Exception::class)
    suspend fun download(siteId: Long, oplId: Long, onProgress: (OplDownloadProgress) -> Unit): IosOplDetailOutcome =
        withContext(workerContext()) {
            val result = repository.download(siteId, oplId, onProgress)
            if (result is NetworkResult.Success) IosOplMediaCache.clearLesson(session, config, siteId, oplId)
            detailOutcome(result)
        }

    @Throws(Exception::class)
    suspend fun deleteDownloaded(siteId: Long, oplId: Long): IosOplOperationOutcome = withContext(workerContext()) {
        when (val result = repository.deleteDownloaded(siteId, oplId)) {
            is NetworkResult.Failure -> IosOplOperationOutcome(false, result.error.message)
            is NetworkResult.Success -> {
                IosOplMediaCache.clearLesson(session, config, siteId, oplId)
                IosOplOperationOutcome(true)
            }
        }
    }

    @Throws(Exception::class)
    suspend fun resolveMedia(siteId: Long, oplId: Long, contentId: Long): IosOplMediaOutcome = withContext(workerContext()) {
        IosOplMediaCache.resolve(repository, session, config, siteId, oplId, contentId)
    }

    fun stop() {
        observation = null
        scope.cancel()
    }

    private fun listOutcome(result: NetworkResult<List<Opl>>): IosOplListOutcome = when (result) {
        is NetworkResult.Success -> IosOplListOutcome(result.data)
        is NetworkResult.Failure -> IosOplListOutcome(emptyList(), result.error.kind.name, result.error.statusCode ?: 0)
    }

    private fun detailOutcome(result: NetworkResult<Opl>): IosOplDetailOutcome = when (result) {
        is NetworkResult.Success -> IosOplDetailOutcome(result.data)
        is NetworkResult.Failure -> IosOplDetailOutcome(null, result.error.kind.name, result.error.statusCode ?: 0)
    }
}
