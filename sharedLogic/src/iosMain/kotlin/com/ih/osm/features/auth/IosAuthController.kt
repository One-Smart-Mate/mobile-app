package com.ih.osm.features.auth

import com.ih.osm.core.network.NetworkResult
import com.ih.osm.features.auth.domain.repository.AuthRepository

data class IosAuthOutcome(
    val errorMessage: String? = null,
) {
    val isSuccess: Boolean get() = errorMessage == null
}

class IosAuthController(
    private val authRepository: AuthRepository,
) {
    suspend fun authenticate(email: String, password: String): IosAuthOutcome =
        when (val result = authRepository.login(email, password)) {
            is NetworkResult.Success -> IosAuthOutcome()
            is NetworkResult.Failure -> IosAuthOutcome(result.error.message)
        }

    suspend fun logout() {
        authRepository.logout()
    }
}
