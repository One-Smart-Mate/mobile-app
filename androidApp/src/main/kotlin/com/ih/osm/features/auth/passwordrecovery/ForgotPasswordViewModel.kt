package com.ih.osm.features.auth.passwordrecovery

import androidx.lifecycle.viewModelScope
import com.ih.osm.core.feature.viewmodel.GRViewModel
import com.ih.osm.core.network.NetworkError
import com.ih.osm.core.network.NetworkErrorKind
import com.ih.osm.features.auth.passwordrecovery.domain.manager.PasswordRecoveryManager
import com.ih.osm.features.auth.passwordrecovery.domain.manager.PasswordRecoveryResult
import com.ih.osm.features.auth.passwordrecovery.domain.manager.PasswordRecoveryValidationError
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ForgotPasswordViewModel(
    private val passwordRecoveryManager: PasswordRecoveryManager,
) : GRViewModel<
    ForgotPasswordViewModel.UiState,
    ForgotPasswordViewModel.Action,
    ForgotPasswordViewModel.Event,
>(initialState = UiState()) {

    enum class Step {
        EMAIL,
        CODE,
        PASSWORD,
        SUCCESS,
    }

    enum class BannerMessage {
        CODE_SENT,
        INVALID_CODE,
        EXPIRED_CODE,
        TOO_MANY_ATTEMPTS,
        NO_CONNECTION,
        REQUEST_FAILED,
    }

    data class UiState(
        val step: Step = Step.EMAIL,
        val email: String = "",
        val code: String = "",
        val password: String = "",
        val passwordConfirmation: String = "",
        val emailError: PasswordRecoveryValidationError? = null,
        val codeError: PasswordRecoveryValidationError? = null,
        val passwordError: PasswordRecoveryValidationError? = null,
        val confirmationError: PasswordRecoveryValidationError? = null,
        val bannerMessage: BannerMessage? = null,
        val isLoading: Boolean = false,
        val resendSeconds: Int = 0,
    )

    sealed interface Action {
        data class Initialize(val email: String) : Action
        data class EmailChanged(val value: String) : Action
        data class CodeChanged(val value: String) : Action
        data class PasswordChanged(val value: String) : Action
        data class PasswordConfirmationChanged(val value: String) : Action
        data object SubmitClicked : Action
        data object ResendCodeClicked : Action
        data object BackClicked : Action
        data object DismissBanner : Action
    }

    sealed interface Event {
        data object NavigateBack : Event
        data object Finished : Event
    }

    private var resendCountdownJob: Job? = null

    override fun processImpl(action: Action) {
        when (action) {
            is Action.Initialize -> initializeEmail(action.email)
            is Action.EmailChanged -> setState {
                copy(email = action.value, emailError = null, bannerMessage = null)
            }
            is Action.CodeChanged -> setState {
                copy(
                    code = passwordRecoveryManager.normalizeCode(action.value),
                    codeError = null,
                    bannerMessage = null,
                )
            }
            is Action.PasswordChanged -> setState {
                copy(
                    password = action.value,
                    passwordError = null,
                    confirmationError = null,
                    bannerMessage = null,
                )
            }
            is Action.PasswordConfirmationChanged -> setState {
                copy(
                    passwordConfirmation = action.value,
                    confirmationError = null,
                    bannerMessage = null,
                )
            }
            Action.SubmitClicked -> submitCurrentStep()
            Action.ResendCodeClicked -> resendCode()
            Action.BackClicked -> navigateBack()
            Action.DismissBanner -> setState { copy(bannerMessage = null) }
        }
    }

    private fun initializeEmail(email: String) {
        if (getStateValue().email.isBlank()) {
            setState { copy(email = email) }
        }
    }

    private fun submitCurrentStep() {
        if (getStateValue().isLoading) return
        when (getStateValue().step) {
            Step.EMAIL -> requestCode(moveToCodeStep = true)
            Step.CODE -> verifyCode()
            Step.PASSWORD -> resetPassword()
            Step.SUCCESS -> viewModelScope.launch { sendNewEvent(Event.Finished) }
        }
    }

    private fun requestCode(moveToCodeStep: Boolean) {
        val email = getStateValue().email
        viewModelScope.launch {
            setState { copy(isLoading = true, emailError = null, bannerMessage = null) }
            when (val result = passwordRecoveryManager.requestCode(email)) {
                is PasswordRecoveryResult.Success -> {
                    setState {
                        copy(
                            step = if (moveToCodeStep) Step.CODE else step,
                            email = result.session.email,
                            code = if (moveToCodeStep) "" else code,
                            codeError = null,
                            bannerMessage = BannerMessage.CODE_SENT,
                            isLoading = false,
                        )
                    }
                    startResendCountdown()
                }
                is PasswordRecoveryResult.ValidationFailure -> setState {
                    copy(
                        emailError = result.error,
                        isLoading = false,
                    )
                }
                is PasswordRecoveryResult.NetworkFailure -> setNetworkFailure(result.error)
            }
        }
    }

    private fun verifyCode() {
        val state = getStateValue()
        viewModelScope.launch {
            setState { copy(isLoading = true, codeError = null, bannerMessage = null) }
            when (val result = passwordRecoveryManager.verifyCode(state.email, state.code)) {
                is PasswordRecoveryResult.Success -> setState {
                    copy(
                        step = Step.PASSWORD,
                        email = result.session.email,
                        code = result.session.resetCode,
                        isLoading = false,
                    )
                }
                is PasswordRecoveryResult.ValidationFailure -> setState {
                    copy(codeError = result.error, isLoading = false)
                }
                is PasswordRecoveryResult.NetworkFailure -> setNetworkFailure(result.error)
            }
        }
    }

    private fun resetPassword() {
        val state = getStateValue()
        viewModelScope.launch {
            setState {
                copy(
                    isLoading = true,
                    passwordError = null,
                    confirmationError = null,
                    bannerMessage = null,
                )
            }
            when (
                val result = passwordRecoveryManager.resetPassword(
                    email = state.email,
                    resetCode = state.code,
                    newPassword = state.password,
                    confirmation = state.passwordConfirmation,
                )
            ) {
                is PasswordRecoveryResult.Success -> setState {
                    copy(
                        step = Step.SUCCESS,
                        password = "",
                        passwordConfirmation = "",
                        isLoading = false,
                    )
                }
                is PasswordRecoveryResult.ValidationFailure -> setPasswordValidationFailure(result.error)
                is PasswordRecoveryResult.NetworkFailure -> setNetworkFailure(result.error)
            }
        }
    }

    private fun resendCode() {
        val state = getStateValue()
        if (state.step != Step.CODE || state.resendSeconds > 0 || state.isLoading) return
        requestCode(moveToCodeStep = false)
    }

    private fun navigateBack() {
        when (getStateValue().step) {
            Step.EMAIL -> viewModelScope.launch { sendNewEvent(Event.NavigateBack) }
            Step.CODE -> {
                resendCountdownJob?.cancel()
                setState {
                    copy(
                        step = Step.EMAIL,
                        code = "",
                        codeError = null,
                        bannerMessage = null,
                        resendSeconds = 0,
                    )
                }
            }
            Step.PASSWORD -> setState {
                copy(
                    step = Step.CODE,
                    password = "",
                    passwordConfirmation = "",
                    passwordError = null,
                    confirmationError = null,
                    bannerMessage = null,
                )
            }
            Step.SUCCESS -> viewModelScope.launch { sendNewEvent(Event.Finished) }
        }
    }

    private fun setPasswordValidationFailure(error: PasswordRecoveryValidationError) {
        setState {
            when (error) {
                PasswordRecoveryValidationError.CONFIRMATION_REQUIRED,
                PasswordRecoveryValidationError.PASSWORDS_DO_NOT_MATCH,
                -> copy(confirmationError = error, isLoading = false)
                PasswordRecoveryValidationError.PASSWORD_REQUIRED,
                PasswordRecoveryValidationError.PASSWORD_TOO_SHORT,
                -> copy(passwordError = error, isLoading = false)
                PasswordRecoveryValidationError.CODE_INVALID -> copy(
                    codeError = error,
                    isLoading = false,
                )
                else -> copy(emailError = error, isLoading = false)
            }
        }
    }

    private fun setNetworkFailure(error: NetworkError) {
        val normalizedMessage = error.message.lowercase()
        val message = when {
            error.statusCode == TOO_MANY_REQUESTS_STATUS -> BannerMessage.TOO_MANY_ATTEMPTS
            error.kind == NetworkErrorKind.CONNECTIVITY -> BannerMessage.NO_CONNECTION
            "expired" in normalizedMessage -> BannerMessage.EXPIRED_CODE
            "wrong reset code" in normalizedMessage -> BannerMessage.INVALID_CODE
            else -> BannerMessage.REQUEST_FAILED
        }
        setState { copy(isLoading = false, bannerMessage = message) }
    }

    private fun startResendCountdown() {
        resendCountdownJob?.cancel()
        resendCountdownJob = viewModelScope.launch {
            for (remaining in RESEND_COOLDOWN_SECONDS downTo 1) {
                setState { copy(resendSeconds = remaining) }
                delay(ONE_SECOND_MILLIS)
            }
            setState { copy(resendSeconds = 0) }
        }
    }

    private companion object {
        const val TOO_MANY_REQUESTS_STATUS = 429
        const val RESEND_COOLDOWN_SECONDS = 60
        const val ONE_SECOND_MILLIS = 1_000L
    }
}
