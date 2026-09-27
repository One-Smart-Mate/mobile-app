package com.ih.osm.features.notifications.data.firebase

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.messaging.FirebaseMessaging

class FirebaseTokenRegistrationScheduler(
    context: Context,
    private val store: FirebaseTokenStore,
) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    fun ensureRegistered() {
        FirebaseMessaging.getInstance().token.addOnSuccessListener(::onTokenRefreshed)
    }

    fun onTokenRefreshed(token: String) {
        if (token.isBlank()) return
        store.save(token)
        val request = OneTimeWorkRequestBuilder<FirebaseTokenRegistrationWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        workManager.enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private companion object {
        const val UNIQUE_WORK_NAME = "firebase-token-registration"
    }
}
