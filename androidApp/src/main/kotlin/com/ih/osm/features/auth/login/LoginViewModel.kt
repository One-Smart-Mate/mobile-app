package com.ih.osm.features.auth.login

import androidx.lifecycle.viewModelScope
import com.ih.osm.core.feature.viewmodel.GRViewModel
import kotlinx.coroutines.launch

class LoginViewModel : GRViewModel<
    LoginViewModel.UiState,
    LoginViewModel.Action,
    LoginViewModel.Event,
>(initialState = UiState()) {

    data class UiState(
        val email: String = "",
        val password: String = "",
        val emailError: EmailError? = null,
        val passwordError: PasswordError? = null,
        val bannerMessage: String? = null,
        val isLoading: Boolean = false,
    )

    enum class EmailError {
        REQUIRED,
        INVALID,
    }

    enum class PasswordError {
        REQUIRED,
    }

    sealed interface Action {
        data class EmailChanged(val email: String) : Action
        data class PasswordChanged(val password: String) : Action
        data object LoginClicked : Action
        data object ForgotPasswordClicked : Action
        data object DismissBanner : Action
    }

    sealed interface Event {
        data class LoginRequested(
            val email: String,
            val password: String,
        ) : Event

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
        }
    }

    private fun validateAndRequestLogin() {
        val state = getStateValue()
        val normalizedEmail = state.email.trim()
        val emailError = when {
            normalizedEmail.isEmpty() -> EmailError.REQUIRED
            !normalizedEmail.matches(EMAIL_PATTERN) -> EmailError.INVALID
            else -> null
        }
        val passwordError = if (state.password.isBlank()) PasswordError.REQUIRED else null

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
            sendNewEvent(
                Event.LoginRequested(
                    email = normalizedEmail,
                    password = state.password,
                ),
            )
        }
    }

    private fun requestPasswordReset() {
        viewModelScope.launch {
            sendNewEvent(Event.ForgotPasswordRequested(getStateValue().email.trim()))
        }
    }

    private companion object {
        val EMAIL_PATTERN = Regex("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", RegexOption.IGNORE_CASE)
    }
}
