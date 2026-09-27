package com.ih.osm.features.notifications

import com.ih.osm.core.network.NetworkResult
import com.ih.osm.features.auth.data.remote.PushTokenRegistrar
import com.ih.osm.features.auth.domain.session.SessionRepository

data class IosPushTokenRegistrationOutcome(
    val succeeded: Boolean,
    val sessionAvailable: Boolean,
    val errorMessage: String?,
)

class IosPushNotificationController(
    private val registrar: PushTokenRegistrar,
    private val sessionRepository: SessionRepository,
) {
    suspend fun registerToken(token: String): IosPushTokenRegistrationOutcome {
        val user = sessionRepository.currentUser()
            ?: return IosPushTokenRegistrationOutcome(false, false, null)
        return when (val result = registrar.register(user.id, token)) {
            is NetworkResult.Success -> IosPushTokenRegistrationOutcome(true, true, null)
            is NetworkResult.Failure -> IosPushTokenRegistrationOutcome(
                succeeded = false,
                sessionAvailable = true,
                errorMessage = result.error.message,
            )
        }
    }
}
