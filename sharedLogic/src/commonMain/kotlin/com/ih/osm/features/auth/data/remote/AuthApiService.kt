package com.ih.osm.features.auth.data.remote

import com.ih.osm.Platform
import com.ih.osm.core.network.NetworkClient
import com.ih.osm.core.network.NetworkResult

internal class AuthApiService(
    private val networkClient: NetworkClient,
    private val platform: Platform,
) {
    suspend fun login(email: String, password: String): NetworkResult<AuthResponse> =
        when (val result = networkClient.post<LoginRequest, ApiResponse<AuthResponse>>(
            endpoint = "/auth/login",
            body = LoginRequest(
                email = email.trim().lowercase(),
                password = password,
                timezone = platform.timeZone,
                platform = platform.authenticationName,
            ),
        )) {
            is NetworkResult.Success -> NetworkResult.Success(
                data = result.data.data,
                statusCode = result.statusCode,
            )
            is NetworkResult.Failure -> result
        }
}
