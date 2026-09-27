package com.ih.osm.features.auth.passwordrecovery.data.remote

import com.ih.osm.core.network.NetworkClient
import com.ih.osm.core.network.NetworkResult

internal class PasswordRecoveryApiService(
    private val networkClient: NetworkClient,
) {
    suspend fun requestCode(email: String, translation: String): NetworkResult<Unit> =
        networkClient.post<SendRecoveryCodeRequest, PasswordRecoveryApiResponse>(
            endpoint = "/users/send-code",
            body = SendRecoveryCodeRequest(email = email, translation = translation),
        ).asUnit()

    suspend fun verifyCode(email: String, resetCode: String): NetworkResult<Unit> =
        networkClient.post<VerifyRecoveryCodeRequest, PasswordRecoveryApiResponse>(
            endpoint = "/users/verify-code",
            body = VerifyRecoveryCodeRequest(email = email, resetCode = resetCode),
        ).asUnit()

    suspend fun resetPassword(
        email: String,
        resetCode: String,
        newPassword: String,
    ): NetworkResult<Unit> =
        networkClient.post<ResetPasswordRequest, PasswordRecoveryApiResponse>(
            endpoint = "/users/reset-password",
            body = ResetPasswordRequest(
                email = email,
                resetCode = resetCode,
                newPassword = newPassword,
            ),
        ).asUnit()

    private fun NetworkResult<PasswordRecoveryApiResponse>.asUnit(): NetworkResult<Unit> =
        when (this) {
            is NetworkResult.Success -> NetworkResult.Success(Unit, statusCode)
            is NetworkResult.Failure -> this
        }
}
