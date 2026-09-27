package com.ih.osm.features.cards.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ih.osm.R
import com.ih.osm.features.card.domain.repository.CardRepository
import com.ih.osm.features.card.domain.usecase.PendingCardSyncResult
import com.ih.osm.features.card.domain.usecase.SyncPendingCardsUseCase
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class CardSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {
    private val syncPendingCards: SyncPendingCardsUseCase by inject()
    private val notifications = CardSyncNotifications(appContext)

    override suspend fun doWork(): Result {
        if (!applicationContext.hasValidatedInternet()) {
            notifications.failure(applicationContext.getString(R.string.card_sync_no_internet))
            return Result.failure()
        }
        setProgress(syncProgressData(completed = 0, total = 0))
        setForeground(notifications.foreground(completed = 0, total = 0))
        return try {
            when (val result = syncPendingCards { progress ->
                setProgress(syncProgressData(progress.completed, progress.total))
                setForeground(notifications.foreground(progress.completed, progress.total))
            }) {
                is PendingCardSyncResult.Success -> {
                    notifications.success(result.synced)
                    Result.success()
                }
                is PendingCardSyncResult.Failure -> {
                    notifications.failure(result.message)
                    Result.failure()
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            notifications.failure(throwable.message)
            Result.failure()
        }
    }
}

data class CardSyncWorkStatus(
    val isActive: Boolean = false,
    val completed: Int = 0,
    val total: Int = 0,
)

private const val CARD_SYNC_COMPLETED = "card-sync-completed"
private const val CARD_SYNC_TOTAL = "card-sync-total"

private fun syncProgressData(completed: Int, total: Int) = workDataOf(
    CARD_SYNC_COMPLETED to completed,
    CARD_SYNC_TOTAL to total,
)

private fun Context.hasValidatedInternet(): Boolean {
    val connectivity = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

class CardSyncScheduler(
    context: Context,
    private val repository: CardRepository,
) {
    private val applicationContext = context.applicationContext
    private val workManager = WorkManager.getInstance(applicationContext)

    val workStatus: Flow<CardSyncWorkStatus> = workManager
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

    fun enqueueAfterCardCreated() {
        if (!applicationContext.hasValidatedInternet()) return
        enqueuePending()
    }

    fun enqueueManually(): Boolean {
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
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()

        workManager.enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    private companion object {
        const val UNIQUE_WORK_NAME = "pending-card-sync"
    }
}

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

    fun success(count: Int) {
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
                    .setContentText(context.resources.getQuantityString(R.plurals.card_sync_complete_body, count, count))
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
