package com.ih.osm.features.auth.login

import androidx.lifecycle.viewModelScope
import com.ih.osm.core.feature.viewmodel.GRViewModel
import com.ih.osm.core.config.AppEnvironment
import com.ih.osm.core.environment.ApiEnvironmentManager
import com.ih.osm.core.network.NetworkResult
import com.ih.osm.core.validation.EmailAddressValidator
import com.ih.osm.features.auth.domain.repository.AuthRepository
import kotlinx.coroutines.launch

class LoginViewModel(
    private val authRepository: AuthRepository,
    private val apiEnvironmentManager: ApiEnvironmentManager,
) : GRViewModel<
    LoginViewModel.UiState,
    LoginViewModel.Action,
    LoginViewModel.Event,
>(
    initialState = UiState(
        currentEnvironment = apiEnvironmentManager.currentEnvironment,
        pendingEnvironment = apiEnvironmentManager.currentEnvironment,
    ),
) {

    data class UiState(
        val email: String = "",
        val password: String = "",
        val emailError: EmailError? = null,
        val passwordError: PasswordError? = null,
        val bannerMessage: String? = null,
        val isLoading: Boolean = false,
        val currentEnvironment: AppEnvironment = AppEnvironment.DEV,
        val pendingEnvironment: AppEnvironment = currentEnvironment,
        val showEnvironmentDialog: Boolean = false,
    )

    enum class EmailError {
        REQUIRED,
        INVALID,
    }

    enum class PasswordError {
        REQUIRED,
        TOO_SHORT,
    }

    sealed interface Action {
        data class EmailChanged(val email: String) : Action
        data class PasswordChanged(val password: String) : Action
        data object LoginClicked : Action
        data object ForgotPasswordClicked : Action
        data object DismissBanner : Action
        data object EnvironmentSelectorRequested : Action
        data class EnvironmentPendingChanged(val environment: AppEnvironment) : Action
        data object EnvironmentSelectionConfirmed : Action
        data object EnvironmentSelectorDismissed : Action
    }

    sealed interface Event {
        data class ForgotPasswordRequested(val email: String) : Event
    }

    override fun processImpl(action: Action) {
        when (action) {
            is Action.EmailChanged -> setState {
                copy(
                    email = action.email,
                    emailError = null,
                    bannerMessage = null,
                )
            }

            is Action.PasswordChanged -> setState {
                copy(
                    password = action.password,
                    passwordError = null,
                    bannerMessage = null,
                )
            }

            Action.LoginClicked -> validateAndRequestLogin()
            Action.ForgotPasswordClicked -> requestPasswordReset()
            Action.DismissBanner -> setState { copy(bannerMessage = null) }
            Action.EnvironmentSelectorRequested -> setState {
                copy(
                    pendingEnvironment = currentEnvironment,
                    showEnvironmentDialog = true,
                )
            }

            is Action.EnvironmentPendingChanged -> setState {
                copy(pendingEnvironment = action.environment)
            }

            Action.EnvironmentSelectionConfirmed -> selectEnvironment()
            Action.EnvironmentSelectorDismissed -> setState {
                copy(
                    pendingEnvironment = currentEnvironment,
                    showEnvironmentDialog = false,
                )
            }
        }
    }

    private fun selectEnvironment() {
        val environment = getStateValue().pendingEnvironment
        apiEnvironmentManager.select(environment)
        setState {
            copy(
                currentEnvironment = environment,
                pendingEnvironment = environment,
                showEnvironmentDialog = false,
                bannerMessage = null,
            )
        }
    }

    private fun validateAndRequestLogin() {
        val state = getStateValue()
        val normalizedEmail = EMAIL_VALIDATOR.normalize(state.email)
        val emailError = when {
            normalizedEmail.isEmpty() -> EmailError.REQUIRED
            !EMAIL_VALIDATOR.isValid(normalizedEmail) -> EmailError.INVALID
            else -> null
        }
        val passwordError = when {
            state.password.isBlank() -> PasswordError.REQUIRED
            state.password.trim().length < MINIMUM_PASSWORD_LENGTH -> PasswordError.TOO_SHORT
            else -> null
        }

        if (emailError != null || passwordError != null) {
            setState {
                copy(
                    emailError = emailError,
                    passwordError = passwordError,
                )
            }
            return
        }

        viewModelScope.launch {
            setState { copy(isLoading = true, bannerMessage = null) }
            when (val result = authRepository.login(normalizedEmail, state.password)) {
                is NetworkResult.Success -> setState { copy(isLoading = false) }
                is NetworkResult.Failure -> setState {
                    copy(isLoading = false, bannerMessage = result.error.message)
                }
            }
        }
    }

    private fun requestPasswordReset() {
        viewModelScope.launch {
            sendNewEvent(
                Event.ForgotPasswordRequested(
                    EMAIL_VALIDATOR.normalize(getStateValue().email),
                ),
            )
        }
    }

    private companion object {
        val EMAIL_VALIDATOR = EmailAddressValidator()
        const val MINIMUM_PASSWORD_LENGTH = 8
    }
}
