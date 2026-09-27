package com.ih.osm.features.cards.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.ih.osm.features.card.domain.repository.CardRepository
import com.ih.osm.features.cards.domain.manager.CardSyncManager
import com.ih.osm.features.cards.domain.model.CardSyncWorkStatus
import com.ih.osm.features.settings.domain.preferences.MobileDataSyncPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class CardSyncScheduler(
    context: Context,
    private val repository: CardRepository,
    private val syncPreferences: MobileDataSyncPreferences,
) : CardSyncManager {
    private val applicationContext = context.applicationContext
    private val workManager = WorkManager.getInstance(applicationContext)

    override val workStatus: Flow<CardSyncWorkStatus> = workManager
        .getWorkInfosForUniqueWorkFlow(UNIQUE_WORK_NAME)
        .map { workInfos ->
            val activeWork = workInfos.lastOrNull { !it.state.isFinished }
            if (activeWork == null) {
                CardSyncWorkStatus()
            } else {
                CardSyncWorkStatus(
                    isActive = activeWork.state == WorkInfo.State.ENQUEUED ||
                        activeWork.state == WorkInfo.State.RUNNING ||
                        activeWork.state == WorkInfo.State.BLOCKED,
                    completed = activeWork.progress.getInt(CARD_SYNC_COMPLETED, 0),
                    total = activeWork.progress.getInt(CARD_SYNC_TOTAL, 0),
                )
            }
        }
        .distinctUntilChanged()

    override fun enqueueAfterCardCreated() {
        if (!applicationContext.hasValidatedInternet()) return
        enqueuePending()
    }

    override fun enqueueManually(): Boolean {
        if (!applicationContext.hasValidatedInternet()) return false
        if (repository.pendingCount() == 0L) return false
        enqueuePending()
        return true
    }

    private fun enqueuePending() {
        if (repository.pendingCount() == 0L) return
        val request = OneTimeWorkRequestBuilder<CardSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(
                        if (syncPreferences.allowMobileData.value) {
                            NetworkType.CONNECTED
                        } else {
                            NetworkType.UNMETERED
                        },
                    )
                    .build(),
            )
            .build()

        workManager.enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    override suspend fun cancel() {
        withContext(Dispatchers.IO) {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME).result.get()
        }
    }

    private fun Context.hasValidatedInternet(): Boolean {
        val connectivity = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private companion object {
        const val UNIQUE_WORK_NAME = "pending-card-sync"
        const val CARD_SYNC_COMPLETED = "card-sync-completed"
        const val CARD_SYNC_TOTAL = "card-sync-total"
    }
}
