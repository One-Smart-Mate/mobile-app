package com.ih.osm.features.notifications.data.firebase

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.ih.osm.MainActivity
import com.ih.osm.R
import com.ih.osm.features.cards.domain.manager.CardSyncManager
import org.koin.android.ext.android.inject

class OsmFirebaseMessagingService : FirebaseMessagingService() {
    private val cardSyncManager: CardSyncManager by inject()
    private val tokenScheduler: FirebaseTokenRegistrationScheduler by inject()

    override fun onNewToken(token: String) {
        tokenScheduler.onTokenRefreshed(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val siteId = data[KEY_SITE_ID]?.toLongOrNull()
        if (data[KEY_SYNC_SCOPE] == SYNC_SCOPE_CARDS && siteId != null) {
            cardSyncManager.enqueueRemoteChanges(siteId)
        }
        showNotification(
            siteId = siteId,
            title = data[KEY_TITLE] ?: getString(R.string.card_remote_update_title),
            body = data[KEY_DESCRIPTION] ?: getString(R.string.card_remote_update_body),
        )
    }

    private fun showNotification(siteId: Long?, title: String, body: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val systemManager = getSystemService(NotificationManager::class.java)
        systemManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.card_updates_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = getString(R.string.card_updates_channel_description) },
        )
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        NotificationManagerCompat.from(this).notify(
            siteId?.hashCode() ?: DEFAULT_NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_more)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build(),
        )
    }

    private companion object {
        const val CHANNEL_ID = "card-updates"
        const val DEFAULT_NOTIFICATION_ID = 4_803
        const val KEY_SYNC_SCOPE = "sync_scope"
        const val KEY_SITE_ID = "site_id"
        const val KEY_TITLE = "notification_title"
        const val KEY_DESCRIPTION = "notification_description"
        const val SYNC_SCOPE_CARDS = "cards"
    }
}
