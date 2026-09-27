package com.ih.osm.features.catalog.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ih.osm.features.catalog.domain.model.CatalogKind
import com.ih.osm.features.catalog.domain.model.CatalogSyncPlan
import com.ih.osm.features.catalog.domain.model.SiteCatalogSyncRequest
import com.ih.osm.features.catalog.domain.usecase.CatalogSyncResult
import com.ih.osm.features.catalog.domain.usecase.SyncCatalogsUseCase
import kotlin.coroutines.cancellation.CancellationException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

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

private fun Int.toCatalogKinds(): Set<CatalogKind> =
    CatalogKind.entries.filterTo(mutableSetOf()) { kind -> this and (1 shl kind.ordinal) != 0 }
