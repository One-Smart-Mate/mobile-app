package com.ih.osm.features.catalog.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ih.osm.features.auth.domain.model.AuthenticatedUser
import com.ih.osm.features.catalog.domain.model.CatalogKind
import com.ih.osm.features.catalog.domain.model.CatalogSyncPlan
import com.ih.osm.features.catalog.domain.model.SiteCatalogSyncRequest
import com.ih.osm.features.catalog.domain.usecase.BuildCatalogSyncPlanUseCase
import com.ih.osm.features.catalog.domain.usecase.CatalogSyncResult
import com.ih.osm.features.catalog.domain.usecase.SyncCatalogsUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.coroutines.cancellation.CancellationException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

sealed interface CatalogSyncUiState {
    data object Idle : CatalogSyncUiState
    data object WaitingForNetwork : CatalogSyncUiState
    data class Downloading(
        val progress: Float,
        val catalog: CatalogKind?,
    ) : CatalogSyncUiState
    data class Failed(val message: String?) : CatalogSyncUiState
}

class CatalogSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {
    private val syncCatalogs: SyncCatalogsUseCase by inject()

    override suspend fun doWork(): Result {
        val userId = inputData.getLong(KEY_USER_ID, INVALID_ID)
        val siteIds = inputData.getLongArray(KEY_SITE_IDS)?.toList().orEmpty()
        val siteScope = inputData.getLongArray(KEY_SITE_SCOPE)?.toList().orEmpty()
        val catalogMasks = inputData.getIntArray(KEY_CATALOG_MASKS)?.toList().orEmpty()
        if (userId == INVALID_ID || siteScope.isEmpty() || siteIds.isEmpty() || siteIds.size != catalogMasks.size) {
            return Result.failure()
        }
        val plan = CatalogSyncPlan(
            userId = userId,
            siteScope = siteScope,
            sites = siteIds.zip(catalogMasks).map { (siteId, mask) ->
                SiteCatalogSyncRequest(siteId, mask.toCatalogKinds())
            },
            resetAllBeforeSave = inputData.getBoolean(KEY_RESET_ALL, false),
        )

        return try {
            setProgress(workDataOf(KEY_COMPLETED_STEPS to 0, KEY_TOTAL_STEPS to plan.totalSteps))
            when (val syncResult = syncCatalogs(plan) { progress ->
                setProgress(
                    workDataOf(
                        KEY_COMPLETED_STEPS to progress.completedSteps,
                        KEY_TOTAL_STEPS to progress.totalSteps,
                        KEY_CATALOG to progress.catalog.name,
                    ),
                )
            }) {
                CatalogSyncResult.Success -> Result.success()
                is CatalogSyncResult.Failure -> failureOrRetry(syncResult.error.message)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            failureOrRetry(throwable.message)
        }
    }

    private fun failureOrRetry(message: String?): Result =
        if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
            Result.retry()
        } else {
            Result.failure(
                if (message.isNullOrBlank()) {
                    workDataOf()
                } else {
                    workDataOf(KEY_ERROR_MESSAGE to message)
                }
            )
        }

    companion object {
        internal const val UNIQUE_WORK_PREFIX = "catalog-sync"
        internal const val KEY_USER_ID = "user-id"
        internal const val KEY_SITE_IDS = "site-ids"
        internal const val KEY_SITE_SCOPE = "site-scope"
        internal const val KEY_CATALOG_MASKS = "catalog-masks"
        internal const val KEY_RESET_ALL = "reset-all"
        internal const val KEY_COMPLETED_STEPS = "completed-steps"
        internal const val KEY_TOTAL_STEPS = "total-steps"
        internal const val KEY_CATALOG = "catalog"
        internal const val KEY_ERROR_MESSAGE = "error-message"
        private const val INVALID_ID = -1L
        private const val MAX_RETRY_ATTEMPTS = 3
    }
}

class CatalogSyncScheduler(
    context: Context,
    private val buildCatalogSyncPlan: BuildCatalogSyncPlanUseCase,
) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    suspend fun enqueueIfNeeded(user: AuthenticatedUser) {
        val siteIds = user.sites.map { it.id }
        val plan = buildCatalogSyncPlan(user.id, siteIds)
        if (plan.isEmpty) return
        enqueue(user, plan, ExistingWorkPolicy.KEEP)
    }

    fun enqueueManual(
        user: AuthenticatedUser,
        catalogs: Set<CatalogKind>,
    ) {
        val plan = buildCatalogSyncPlan.manual(
            userId = user.id,
            siteIds = user.sites.map { it.id },
            catalogs = catalogs,
        )
        if (!plan.isEmpty) enqueue(user, plan, ExistingWorkPolicy.REPLACE)
    }

    private fun enqueue(
        user: AuthenticatedUser,
        plan: CatalogSyncPlan,
        policy: ExistingWorkPolicy,
    ) {
        val request = OneTimeWorkRequestBuilder<CatalogSyncWorker>()
            .setInputData(
                workDataOf(
                    CatalogSyncWorker.KEY_USER_ID to user.id,
                    CatalogSyncWorker.KEY_SITE_SCOPE to plan.siteScope.toLongArray(),
                    CatalogSyncWorker.KEY_SITE_IDS to plan.sites.map { it.siteId }.toLongArray(),
                    CatalogSyncWorker.KEY_CATALOG_MASKS to plan.sites
                        .map { it.catalogs.toMask() }
                        .toIntArray(),
                    CatalogSyncWorker.KEY_RESET_ALL to plan.resetAllBeforeSave,
                ),
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniqueWork(
            workName(user),
            policy,
            request,
        )
    }

    fun observe(user: AuthenticatedUser): Flow<CatalogSyncUiState> =
        workManager.getWorkInfosForUniqueWorkFlow(workName(user))
            .map { workInfos -> workInfos.lastOrNull().toUiState() }

    private fun workName(user: AuthenticatedUser): String {
        val siteScope = user.sites.map { it.id }.distinct().sorted().joinToString("-")
        return "${CatalogSyncWorker.UNIQUE_WORK_PREFIX}-${user.id}-$siteScope"
    }

    private fun WorkInfo?.toUiState(): CatalogSyncUiState = when (this?.state) {
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> CatalogSyncUiState.WaitingForNetwork
        WorkInfo.State.RUNNING -> {
            val completed = progress.getInt(CatalogSyncWorker.KEY_COMPLETED_STEPS, 0)
            val total = progress.getInt(CatalogSyncWorker.KEY_TOTAL_STEPS, 0)
            val catalog = progress.getString(CatalogSyncWorker.KEY_CATALOG)
                ?.let { runCatching { CatalogKind.valueOf(it) }.getOrNull() }
            CatalogSyncUiState.Downloading(
                progress = if (total == 0) 0f else completed.toFloat() / total,
                catalog = catalog,
            )
        }
        WorkInfo.State.FAILED, WorkInfo.State.CANCELLED ->
            CatalogSyncUiState.Failed(outputData.getString(CatalogSyncWorker.KEY_ERROR_MESSAGE))
        WorkInfo.State.SUCCEEDED, null -> CatalogSyncUiState.Idle
    }
}

private fun Set<CatalogKind>.toMask(): Int = fold(0) { mask, kind -> mask or (1 shl kind.ordinal) }

private fun Int.toCatalogKinds(): Set<CatalogKind> =
    CatalogKind.entries.filterTo(mutableSetOf()) { kind -> this and (1 shl kind.ordinal) != 0 }
