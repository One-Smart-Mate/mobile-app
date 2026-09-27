package com.ih.osm.features.auth.passwordrecovery.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
internal data class SendRecoveryCodeRequest(
    val email: String,
    val translation: String,
)

@Serializable
internal data class VerifyRecoveryCodeRequest(
    val email: String,
    val resetCode: String,
)

@Serializable
internal data class ResetPasswordRequest(
    val email: String,
    val newPassword: String,
    val resetCode: String,
)

@Serializable
internal data class PasswordRecoveryApiResponse(
    val data: JsonElement? = null,
    val status: Int? = null,
    val message: String? = null,
)
