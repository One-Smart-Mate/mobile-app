package com.ih.osm.features.auth

import com.ih.osm.core.network.NetworkError
import com.ih.osm.core.network.NetworkErrorKind
import com.ih.osm.features.auth.passwordrecovery.domain.manager.PasswordRecoveryManager
import com.ih.osm.features.auth.passwordrecovery.domain.manager.PasswordRecoveryResult
import com.ih.osm.features.auth.passwordrecovery.domain.manager.PasswordRecoveryValidationError

enum class IosPasswordRecoveryFailure {
    TOO_MANY_ATTEMPTS,
    NO_CONNECTION,
    EXPIRED_CODE,
    INVALID_CODE,
    REQUEST_FAILED,
}

data class IosPasswordRecoveryOutcome(
    val email: String? = null,
    val resetCode: String? = null,
    val validationError: PasswordRecoveryValidationError? = null,
    val failure: IosPasswordRecoveryFailure? = null,
) {
    val succeeded: Boolean get() = validationError == null && failure == null
}

/**
 * Thin Swift-facing adapter. Validation and network operations remain in the shared manager.
 */
class IosPasswordRecoveryController(
    private val manager: PasswordRecoveryManager,
) {
    suspend fun requestCode(email: String): IosPasswordRecoveryOutcome =
        manager.requestCode(email).toIosOutcome()

    suspend fun verifyCode(email: String, resetCode: String): IosPasswordRecoveryOutcome =
        manager.verifyCode(email, resetCode).toIosOutcome()

    suspend fun resetPassword(
        email: String,
        resetCode: String,
        newPassword: String,
        confirmation: String,
    ): IosPasswordRecoveryOutcome = manager.resetPassword(
        email = email,
        resetCode = resetCode,
        newPassword = newPassword,
        confirmation = confirmation,
    ).toIosOutcome()

    fun normalizeCode(value: String): String = manager.normalizeCode(value)

    private fun PasswordRecoveryResult.toIosOutcome(): IosPasswordRecoveryOutcome = when (this) {
        is PasswordRecoveryResult.Success -> IosPasswordRecoveryOutcome(
            email = session.email,
            resetCode = session.resetCode,
        )
        is PasswordRecoveryResult.ValidationFailure -> IosPasswordRecoveryOutcome(
            validationError = error,
        )
        is PasswordRecoveryResult.NetworkFailure -> IosPasswordRecoveryOutcome(
            failure = error.toIosFailure(),
        )
    }

    private fun NetworkError.toIosFailure(): IosPasswordRecoveryFailure {
        val normalizedMessage = message.lowercase()
        return when {
            statusCode == TOO_MANY_REQUESTS_STATUS ->
                IosPasswordRecoveryFailure.TOO_MANY_ATTEMPTS
            kind == NetworkErrorKind.CONNECTIVITY ->
                IosPasswordRecoveryFailure.NO_CONNECTION
            "expired" in normalizedMessage ->
                IosPasswordRecoveryFailure.EXPIRED_CODE
            "wrong reset code" in normalizedMessage ->
                IosPasswordRecoveryFailure.INVALID_CODE
            else -> IosPasswordRecoveryFailure.REQUEST_FAILED
        }
    }

    private companion object {
        const val TOO_MANY_REQUESTS_STATUS = 429
    }
}
