package com.ih.osm.features.catalog.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.ih.osm.features.auth.domain.model.AuthenticatedUser
import com.ih.osm.features.catalog.domain.manager.CatalogSyncManager
import com.ih.osm.features.catalog.domain.model.CatalogKind
import com.ih.osm.features.catalog.domain.model.CatalogSyncPlan
import com.ih.osm.features.catalog.domain.model.CatalogSyncStatus
import com.ih.osm.features.catalog.domain.usecase.BuildCatalogSyncPlanUseCase
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class CatalogSyncScheduler(
    context: Context,
    private val buildCatalogSyncPlan: BuildCatalogSyncPlanUseCase,
) : CatalogSyncManager {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    override suspend fun enqueueIfNeeded(user: AuthenticatedUser) {
        val siteIds = user.sites.map { it.id }
        val plan = buildCatalogSyncPlan(user.id, siteIds)
        if (plan.isEmpty) return
        enqueue(user, plan, ExistingWorkPolicy.KEEP)
    }

    override fun enqueueManual(user: AuthenticatedUser, catalogs: Set<CatalogKind>) {
        val plan = buildCatalogSyncPlan.manual(
            userId = user.id,
            siteIds = user.sites.map { it.id },
            catalogs = catalogs,
        )
        if (!plan.isEmpty) enqueue(user, plan, ExistingWorkPolicy.REPLACE)
    }

    override fun observe(user: AuthenticatedUser): Flow<CatalogSyncStatus> =
        workManager.getWorkInfosForUniqueWorkFlow(workName(user))
            .map { workInfos -> workInfos.lastOrNull().toSyncStatus() }

    override suspend fun cancel(user: AuthenticatedUser) {
        withContext(Dispatchers.IO) {
            workManager.cancelUniqueWork(workName(user)).result.get()
        }
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

        workManager.enqueueUniqueWork(workName(user), policy, request)
    }

    private fun workName(user: AuthenticatedUser): String {
        val siteScope = user.sites.map { it.id }.distinct().sorted().joinToString("-")
        return "${CatalogSyncWorker.UNIQUE_WORK_PREFIX}-${user.id}-$siteScope"
    }

    private fun WorkInfo?.toSyncStatus(): CatalogSyncStatus = when (this?.state) {
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> CatalogSyncStatus.WaitingForNetwork
        WorkInfo.State.RUNNING -> {
            val completed = progress.getInt(CatalogSyncWorker.KEY_COMPLETED_STEPS, 0)
            val total = progress.getInt(CatalogSyncWorker.KEY_TOTAL_STEPS, 0)
            val catalog = progress.getString(CatalogSyncWorker.KEY_CATALOG)
                ?.let { runCatching { CatalogKind.valueOf(it) }.getOrNull() }
            CatalogSyncStatus.Downloading(
                progress = if (total == 0) 0f else completed.toFloat() / total,
                catalog = catalog,
            )
        }
        WorkInfo.State.FAILED, WorkInfo.State.CANCELLED ->
            CatalogSyncStatus.Failed(outputData.getString(CatalogSyncWorker.KEY_ERROR_MESSAGE))
        WorkInfo.State.SUCCEEDED, null -> CatalogSyncStatus.Idle
    }
}

private fun Set<CatalogKind>.toMask(): Int = fold(0) { mask, kind -> mask or (1 shl kind.ordinal) }
