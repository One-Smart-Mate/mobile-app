package com.ih.osm.features.notifications.data.firebase

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ih.osm.core.network.NetworkResult
import com.ih.osm.features.auth.data.remote.PushTokenRegistrar
import com.ih.osm.features.auth.domain.session.SessionRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class FirebaseTokenRegistrationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {
    private val store: FirebaseTokenStore by inject()
    private val registrar: PushTokenRegistrar by inject()
    private val sessionRepository: SessionRepository by inject()

    override suspend fun doWork(): Result {
        val token = store.token() ?: return Result.success()
        val user = sessionRepository.currentUser() ?: return Result.success()
        return when (registrar.register(user.id, token)) {
            is NetworkResult.Success -> Result.success()
            is NetworkResult.Failure -> if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
    }
}
