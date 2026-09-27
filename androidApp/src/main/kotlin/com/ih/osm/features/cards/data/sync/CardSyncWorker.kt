package com.ih.osm.features.cards.data.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ih.osm.R
import com.ih.osm.features.card.domain.repository.CardRepository
import com.ih.osm.features.card.domain.usecase.PendingCardSyncResult
import com.ih.osm.features.card.domain.usecase.SyncPendingCardsUseCase
import com.ih.osm.features.card.domain.usecase.SyncPendingSolutionsUseCase
import com.ih.osm.core.network.NetworkResult
import kotlin.coroutines.cancellation.CancellationException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class CardSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {
    private val syncPendingCards: SyncPendingCardsUseCase by inject()
    private val syncPendingSolutions: SyncPendingSolutionsUseCase by inject()
    private val repository: CardRepository by inject()
    private val evidenceUploader: ServiceCardEvidenceUploader by inject()
    private val triggerStore: CardSyncTriggerStore by inject()
    private val networkPolicy: CardSyncNetworkPolicy by inject()
    private val notifications = CardSyncNotifications(appContext)

    override suspend fun doWork(): Result {
        if (!networkPolicy.canSynchronizeNow()) {
            return Result.retry()
        }
        val pendingWork = repository.getPendingWork(MAX_PENDING_WORK)
        val pendingCards = pendingWork.count { it.isLocal }
        val pendingSolutions = pendingWork.sumOf { card ->
            (if (card.provisionalSolutionPending) 1 else 0) +
                (if (card.definitiveSolutionPending) 1 else 0)
        }
        val pendingEvidences = evidenceUploader.pendingEvidenceCount()
        val initialRemoteSites = triggerStore.snapshot()
        val outboundSites = pendingWork.map { it.siteId }.toSet()
        val hasOutboundWork = pendingCards + pendingSolutions + pendingEvidences > 0
        val totalOperations = pendingCards + pendingSolutions + pendingEvidences +
            (initialRemoteSites.keys + outboundSites).size
        if (totalOperations == 0) return Result.success()
        setProgress(syncProgressData(completed = 0, total = totalOperations))
        if (hasOutboundWork) {
            setForeground(notifications.foreground(completed = 0, total = totalOperations))
        }
        return try {
            val uploadedEvidences = when (val upload = evidenceUploader.uploadPending { completed, _ ->
                setProgress(syncProgressData(completed, totalOperations))
                if (hasOutboundWork) setForeground(notifications.foreground(completed, totalOperations))
            }) {
                is EvidenceUploadResult.Success -> upload.uploaded
                is EvidenceUploadResult.Failure -> {
                    notifications.failure(upload.message)
                    return Result.failure()
                }
            }
            val cardResult = syncPendingCards { progress ->
                val completed = uploadedEvidences + progress.completed
                setProgress(syncProgressData(completed, totalOperations))
                if (hasOutboundWork) setForeground(notifications.foreground(completed, totalOperations))
            }
            when (cardResult) {
                is PendingCardSyncResult.Failure -> {
                    notifications.failure(cardResult.message)
                    Result.failure()
                }
                is PendingCardSyncResult.Success -> when (
                    val solutionResult = syncPendingSolutions { progress ->
                        val completed = uploadedEvidences + pendingCards + progress.completed
                        setProgress(syncProgressData(completed, totalOperations))
                        if (hasOutboundWork) setForeground(notifications.foreground(completed, totalOperations))
                    }
                ) {
                    is PendingCardSyncResult.Failure -> {
                        notifications.failure(solutionResult.message)
                        Result.failure()
                    }
                    is PendingCardSyncResult.Success -> {
                        val remoteResult = pullRemoteChanges(
                            outboundSites = outboundSites,
                            baseCompleted = uploadedEvidences + pendingCards + pendingSolutions,
                            totalOperations = totalOperations,
                            showForeground = hasOutboundWork,
                        )
                        when (remoteResult) {
                            is RemotePullResult.Failure -> {
                                notifications.failure(remoteResult.message)
                                if (runAttemptCount < MAX_RETRY_ATTEMPTS) Result.retry() else Result.failure()
                            }
                            is RemotePullResult.Success -> {
                                val uploaded = cardResult.synced + solutionResult.synced
                                if (uploaded > 0 || remoteResult.changes > 0) {
                                    notifications.success(uploaded, remoteResult.changes)
                                }
                                Result.success()
                            }
                        }
                    }
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            notifications.failure(throwable.message)
            Result.failure()
        }
    }

    private suspend fun pullRemoteChanges(
        outboundSites: Set<Long>,
        baseCompleted: Int,
        totalOperations: Int,
        showForeground: Boolean,
    ): RemotePullResult {
        var remoteChanges = 0
        var completedSites = 0
        var firstRound = true
        repeat(MAX_TRIGGER_DRAIN_ROUNDS) {
            val triggered = triggerStore.snapshot()
            val sites = buildSet {
                addAll(triggered.keys)
                if (firstRound) addAll(outboundSites)
            }
            firstRound = false
            if (sites.isEmpty()) return RemotePullResult.Success(remoteChanges)

            for (siteId in sites) {
                when (val result = repository.syncChanges(siteId)) {
                    is NetworkResult.Failure -> return RemotePullResult.Failure(result.error.message)
                    is NetworkResult.Success -> {
                        remoteChanges += result.data
                        completedSites += 1
                        triggered[siteId]?.let { generation ->
                            triggerStore.acknowledge(siteId, generation)
                        }
                        val completed = (baseCompleted + completedSites).coerceAtMost(totalOperations)
                        setProgress(syncProgressData(completed, totalOperations))
                        if (showForeground) setForeground(notifications.foreground(completed, totalOperations))
                    }
                }
            }
        }
        return if (triggerStore.snapshot().isEmpty()) {
            RemotePullResult.Success(remoteChanges)
        } else {
            RemotePullResult.Failure("Hay más cambios pendientes por descargar.")
        }
    }
}

private sealed interface RemotePullResult {
    data class Success(val changes: Int) : RemotePullResult
    data class Failure(val message: String) : RemotePullResult
}

private const val MAX_PENDING_WORK = 10_000L
private const val MAX_TRIGGER_DRAIN_ROUNDS = 3
private const val MAX_RETRY_ATTEMPTS = 5

private const val CARD_SYNC_COMPLETED = "card-sync-completed"
private const val CARD_SYNC_TOTAL = "card-sync-total"

private fun syncProgressData(completed: Int, total: Int) = workDataOf(
    CARD_SYNC_COMPLETED to completed,
    CARD_SYNC_TOTAL to total,
)

private class CardSyncNotifications(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    init {
        val systemManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        systemManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.card_sync_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.card_sync_channel_description)
            },
        )
    }

    fun foreground(completed: Int, total: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(context.getString(R.string.card_sync_in_progress_title))
            .setContentText(
                if (total > 0) {
                    context.getString(R.string.card_sync_progress, completed, total)
                } else {
                    context.getString(R.string.card_sync_preparing)
                },
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(total.coerceAtLeast(0), completed.coerceAtLeast(0), total <= 0)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(FOREGROUND_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    fun success(uploadedCount: Int, downloadedCount: Int) {
        runCatching {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            manager.notify(
                RESULT_NOTIFICATION_ID,
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_upload_done)
                    .setContentTitle(context.getString(R.string.card_sync_complete_title))
                    .setContentText(
                        when {
                            uploadedCount > 0 -> context.resources.getQuantityString(
                                R.plurals.card_sync_complete_body,
                                uploadedCount,
                                uploadedCount,
                            )
                            downloadedCount > 0 -> context.resources.getQuantityString(
                                R.plurals.card_sync_download_complete_body,
                                downloadedCount,
                                downloadedCount,
                            )
                            else -> context.getString(R.string.card_sync_up_to_date)
                        },
                    )
                    .setAutoCancel(true)
                    .build(),
            )
        }
    }

    fun failure(message: String?) {
        runCatching {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
            }
            manager.notify(
                RESULT_NOTIFICATION_ID,
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_notify_error)
                    .setContentTitle(context.getString(R.string.card_sync_failed_title))
                    .setContentText(message ?: context.getString(R.string.card_sync_failed_body))
                    .setStyle(
                        NotificationCompat.BigTextStyle()
                            .bigText(message ?: context.getString(R.string.card_sync_failed_body)),
                    )
                    .setAutoCancel(true)
                    .build(),
            )
        }
    }

    private companion object {
        const val CHANNEL_ID = "card-sync"
        const val FOREGROUND_NOTIFICATION_ID = 4_801
        const val RESULT_NOTIFICATION_ID = 4_802
    }
}
