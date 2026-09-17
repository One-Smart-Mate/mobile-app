package com.ih.osm.features.auth.data.repository

import com.ih.osm.core.network.NetworkError
import com.ih.osm.core.network.NetworkErrorKind
import com.ih.osm.core.network.NetworkResult
import com.ih.osm.features.auth.data.remote.AuthApiService
import com.ih.osm.features.auth.data.remote.AuthResponse
import com.ih.osm.features.auth.domain.model.AuthenticatedUser
import com.ih.osm.features.auth.domain.model.UserSite
import com.ih.osm.features.auth.domain.repository.AuthRepository
import com.ih.osm.features.auth.domain.session.SessionRepository
import kotlin.coroutines.cancellation.CancellationException

internal class AuthRepositoryImpl(
    private val apiService: AuthApiService,
    private val sessionRepository: SessionRepository,
) : AuthRepository {
    override suspend fun login(email: String, password: String): NetworkResult<AuthenticatedUser> =
        when (val response = apiService.login(email, password)) {
            is NetworkResult.Failure -> response
            is NetworkResult.Success -> establishSession(response.data, response.statusCode)
        }

    override suspend fun logout() {
        sessionRepository.invalidate()
    }

    private suspend fun establishSession(
        response: AuthResponse,
        statusCode: Int,
    ): NetworkResult<AuthenticatedUser> {
        val user = response.toDomain()
        return try {
            sessionRepository.establish(user, response.token)
            NetworkResult.Success(user, statusCode)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            NetworkResult.Failure(
                NetworkError(
                    kind = NetworkErrorKind.UNKNOWN,
                    message = "No fue posible guardar la sesión.",
                ),
            )
        }
    }

    private fun AuthResponse.toDomain() = AuthenticatedUser(
        id = userId,
        name = name,
        email = email,
        roles = roles,
        logo = logo,
        companyId = companyId,
        companyName = companyName,
        sites = sites.map { UserSite(it.id, it.name, it.logo) },
        appHistory = appHistory,
        dueDate = dueDate,
    )
}
