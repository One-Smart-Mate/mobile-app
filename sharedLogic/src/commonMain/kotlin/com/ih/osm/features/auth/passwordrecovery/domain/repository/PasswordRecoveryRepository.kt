package com.ih.osm.features.auth.passwordrecovery.domain.repository

import com.ih.osm.core.network.NetworkResult

interface PasswordRecoveryRepository {
    suspend fun requestCode(email: String, translation: String): NetworkResult<Unit>

    suspend fun verifyCode(email: String, resetCode: String): NetworkResult<Unit>

    suspend fun resetPassword(
        email: String,
        resetCode: String,
        newPassword: String,
    ): NetworkResult<Unit>
}
