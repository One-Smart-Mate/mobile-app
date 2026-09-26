package com.ih.osm.features.catalog

import com.ih.osm.core.network.NetworkErrorKind
import com.ih.osm.features.auth.domain.session.SessionRepository
import com.ih.osm.features.catalog.domain.model.CatalogKind
import com.ih.osm.features.catalog.domain.model.CatalogSyncPlan
import com.ih.osm.features.catalog.domain.model.CatalogSyncProgress
import com.ih.osm.features.catalog.domain.usecase.BuildCatalogSyncPlanUseCase
import com.ih.osm.features.catalog.domain.usecase.CatalogSyncResult
import com.ih.osm.features.catalog.domain.usecase.SyncCatalogsUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class IosCatalogSyncProgress(
    val completedSteps: Int,
    val totalSteps: Int,
    val catalogKey: String,
    val siteId: Long,
) {
    val fraction: Double
        get() = if (totalSteps == 0) 0.0 else completedSteps.toDouble() / totalSteps
}

data class IosCatalogSyncOutcome(
    val didRun: Boolean,
    val succeeded: Boolean,
    val hasSession: Boolean,
    val connectivityFailure: Boolean,
    val errorMessage: String?,
)

class IosCatalogSyncController(
    private val sessionRepository: SessionRepository,
    private val buildCatalogSyncPlan: BuildCatalogSyncPlanUseCase,
    private val syncCatalogs: SyncCatalogsUseCase,
) {
    private val executionMutex = Mutex()
    private var activeJob: Job? = null

    suspend fun syncIfNeeded(
        onProgress: (IosCatalogSyncProgress) -> Unit,
    ): IosCatalogSyncOutcome = execute(onProgress) { userId, siteIds ->
        buildCatalogSyncPlan(userId, siteIds)
    }

    suspend fun syncManual(
        catalogKeys: List<String>,
        onProgress: (IosCatalogSyncProgress) -> Unit,
    ): IosCatalogSyncOutcome {
        val selectedCatalogs = catalogKeys.mapNotNullTo(mutableSetOf()) { key ->
            CatalogKind.entries.firstOrNull { it.name == key }
        }
        return execute(onProgress) { userId, siteIds ->
            buildCatalogSyncPlan.manual(userId, siteIds, selectedCatalogs)
        }
    }

    fun cancelActiveSync() {
        activeJob?.cancel()
    }

    private suspend fun execute(
        onProgress: (IosCatalogSyncProgress) -> Unit,
        plan: (userId: Long, siteIds: List<Long>) -> CatalogSyncPlan,
    ): IosCatalogSyncOutcome = executionMutex.withLock {
        val user = sessionRepository.currentUser()
            ?: return@withLock IosCatalogSyncOutcome(
                didRun = false,
                succeeded = true,
                hasSession = false,
                connectivityFailure = false,
                errorMessage = null,
            )
        val syncPlan = plan(user.id, user.sites.map { it.id })
        if (syncPlan.isEmpty) {
            return@withLock IosCatalogSyncOutcome(
                didRun = false,
                succeeded = true,
                hasSession = true,
                connectivityFailure = false,
                errorMessage = null,
            )
        }

        activeJob = currentCoroutineContext()[Job]
        try {
            when (val result = syncCatalogs(syncPlan) { progress ->
                onProgress(progress.toIosProgress())
            }) {
                CatalogSyncResult.Success -> IosCatalogSyncOutcome(
                    didRun = true,
                    succeeded = true,
                    hasSession = true,
                    connectivityFailure = false,
                    errorMessage = null,
                )
                is CatalogSyncResult.Failure -> IosCatalogSyncOutcome(
                    didRun = true,
                    succeeded = false,
                    hasSession = true,
                    connectivityFailure = result.error.kind == NetworkErrorKind.CONNECTIVITY,
                    errorMessage = result.error.message,
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } finally {
            activeJob = null
        }
    }
}

private fun CatalogSyncProgress.toIosProgress() = IosCatalogSyncProgress(
    completedSteps = completedSteps,
    totalSteps = totalSteps,
    catalogKey = catalog.name,
    siteId = siteId,
)
