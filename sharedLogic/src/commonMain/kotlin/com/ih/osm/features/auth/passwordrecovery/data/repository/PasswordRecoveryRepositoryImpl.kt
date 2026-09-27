package com.ih.osm.features.auth.passwordrecovery.data.repository

import com.ih.osm.core.network.NetworkResult
import com.ih.osm.features.auth.passwordrecovery.data.remote.PasswordRecoveryApiService
import com.ih.osm.features.auth.passwordrecovery.domain.repository.PasswordRecoveryRepository

internal class PasswordRecoveryRepositoryImpl(
    private val apiService: PasswordRecoveryApiService,
) : PasswordRecoveryRepository {
    override suspend fun requestCode(email: String, translation: String): NetworkResult<Unit> =
        apiService.requestCode(email = email.trim().lowercase(), translation = translation)

    override suspend fun verifyCode(email: String, resetCode: String): NetworkResult<Unit> =
        apiService.verifyCode(
            email = email.trim().lowercase(),
            resetCode = resetCode.trim().uppercase(),
        )

    override suspend fun resetPassword(
        email: String,
        resetCode: String,
        newPassword: String,
    ): NetworkResult<Unit> = apiService.resetPassword(
        email = email.trim().lowercase(),
        resetCode = resetCode.trim().uppercase(),
        newPassword = newPassword.trim(),
    )
}
