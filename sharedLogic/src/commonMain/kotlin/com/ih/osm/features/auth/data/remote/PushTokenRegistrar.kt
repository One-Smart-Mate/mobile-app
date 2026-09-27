package com.ih.osm.features.auth.data.remote

import com.ih.osm.Platform
import com.ih.osm.core.network.NetworkClient
import com.ih.osm.core.network.NetworkResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

class PushTokenRegistrar internal constructor(
    private val networkClient: NetworkClient,
    private val platform: Platform,
) {
    suspend fun register(userId: Long, token: String): NetworkResult<Unit> = when (
        val result = networkClient.post<SetAppTokenRequest, JsonElement>(
            endpoint = "/users/app-token",
            body = SetAppTokenRequest(
                userId = userId,
                appToken = token,
                osName = platform.authenticationName,
                osVersion = platform.name,
            ),
        )
    ) {
        is NetworkResult.Success -> NetworkResult.Success(Unit, result.statusCode)
        is NetworkResult.Failure -> result
    }
}

@Serializable
private data class SetAppTokenRequest(
    val userId: Long,
    val appToken: String,
    val osName: String,
    val osVersion: String,
)
