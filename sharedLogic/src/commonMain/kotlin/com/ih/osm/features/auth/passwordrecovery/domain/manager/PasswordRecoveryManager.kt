package com.ih.osm.features.auth.passwordrecovery.domain.manager

import com.ih.osm.core.network.NetworkError
import com.ih.osm.core.network.NetworkResult
import com.ih.osm.core.validation.EmailAddressValidator
import com.ih.osm.features.auth.passwordrecovery.domain.repository.PasswordRecoveryRepository

data class PasswordRecoverySession(
    val email: String,
    val resetCode: String = "",
)

enum class PasswordRecoveryValidationError {
    EMAIL_REQUIRED,
    EMAIL_INVALID,
    CODE_INVALID,
    PASSWORD_REQUIRED,
    PASSWORD_TOO_SHORT,
    CONFIRMATION_REQUIRED,
    PASSWORDS_DO_NOT_MATCH,
}

sealed interface PasswordRecoveryResult {
    data class Success(val session: PasswordRecoverySession) : PasswordRecoveryResult
    data class ValidationFailure(val error: PasswordRecoveryValidationError) : PasswordRecoveryResult
    data class NetworkFailure(val error: NetworkError) : PasswordRecoveryResult
}

class PasswordRecoveryManager(
    private val repository: PasswordRecoveryRepository,
) {
    private val emailValidator = EmailAddressValidator()

    suspend fun requestCode(email: String): PasswordRecoveryResult {
        val normalizedEmail = normalizeEmail(email)
        validateEmail(normalizedEmail)?.let {
            return PasswordRecoveryResult.ValidationFailure(it)
        }
        return repository.requestCode(normalizedEmail, SPANISH_LANGUAGE).toRecoveryResult(
            PasswordRecoverySession(email = normalizedEmail),
        )
    }

    suspend fun verifyCode(email: String, resetCode: String): PasswordRecoveryResult {
        val normalizedEmail = normalizeEmail(email)
        validateEmail(normalizedEmail)?.let {
            return PasswordRecoveryResult.ValidationFailure(it)
        }
        val normalizedCode = normalizeCode(resetCode)
        if (!RESET_CODE_PATTERN.matches(normalizedCode)) {
            return PasswordRecoveryResult.ValidationFailure(
                PasswordRecoveryValidationError.CODE_INVALID,
            )
        }
        return repository.verifyCode(normalizedEmail, normalizedCode).toRecoveryResult(
            PasswordRecoverySession(email = normalizedEmail, resetCode = normalizedCode),
        )
    }

    suspend fun resetPassword(
        email: String,
        resetCode: String,
        newPassword: String,
        confirmation: String,
    ): PasswordRecoveryResult {
        val normalizedEmail = normalizeEmail(email)
        validateEmail(normalizedEmail)?.let {
            return PasswordRecoveryResult.ValidationFailure(it)
        }
        val normalizedCode = normalizeCode(resetCode)
        if (!RESET_CODE_PATTERN.matches(normalizedCode)) {
            return PasswordRecoveryResult.ValidationFailure(
                PasswordRecoveryValidationError.CODE_INVALID,
            )
        }
        val normalizedPassword = newPassword.trim()
        val normalizedConfirmation = confirmation.trim()
        val passwordError = when {
            normalizedPassword.isEmpty() -> PasswordRecoveryValidationError.PASSWORD_REQUIRED
            normalizedPassword.length < MINIMUM_PASSWORD_LENGTH ->
                PasswordRecoveryValidationError.PASSWORD_TOO_SHORT
            normalizedConfirmation.isEmpty() -> PasswordRecoveryValidationError.CONFIRMATION_REQUIRED
            normalizedPassword != normalizedConfirmation ->
                PasswordRecoveryValidationError.PASSWORDS_DO_NOT_MATCH
            else -> null
        }
        passwordError?.let { return PasswordRecoveryResult.ValidationFailure(it) }

        return repository.resetPassword(
            email = normalizedEmail,
            resetCode = normalizedCode,
            newPassword = normalizedPassword,
        ).toRecoveryResult(PasswordRecoverySession(normalizedEmail, normalizedCode))
    }

    fun normalizeCode(value: String): String = value
        .filter(Char::isLetterOrDigit)
        .uppercase()
        .take(RESET_CODE_LENGTH)

    private fun normalizeEmail(value: String): String = emailValidator.normalize(value).lowercase()

    private fun validateEmail(email: String): PasswordRecoveryValidationError? = when {
        email.isEmpty() -> PasswordRecoveryValidationError.EMAIL_REQUIRED
        !emailValidator.isValid(email) -> PasswordRecoveryValidationError.EMAIL_INVALID
        else -> null
    }

    private fun NetworkResult<Unit>.toRecoveryResult(
        session: PasswordRecoverySession,
    ): PasswordRecoveryResult = when (this) {
        is NetworkResult.Success -> PasswordRecoveryResult.Success(session)
        is NetworkResult.Failure -> PasswordRecoveryResult.NetworkFailure(error)
    }

    private companion object {
        const val SPANISH_LANGUAGE = "ES"
        const val MINIMUM_PASSWORD_LENGTH = 8
        const val RESET_CODE_LENGTH = 6
        val RESET_CODE_PATTERN = Regex("^[A-Z0-9]{$RESET_CODE_LENGTH}$")
    }
}
