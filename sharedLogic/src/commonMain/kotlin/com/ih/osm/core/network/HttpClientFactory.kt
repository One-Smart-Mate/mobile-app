package com.ih.osm.core.network

import com.ih.osm.core.config.AppConfig
import com.ih.osm.core.auth.TokenStorage
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.encodedPath
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable

internal fun createHttpClient(
    config: AppConfig,
    tokenStorage: TokenStorage,
    onSessionInvalidated: suspend () -> Unit,
): HttpClient = HttpClient {
    configure(config, tokenStorage, onSessionInvalidated)
}

private fun <T : HttpClientEngineConfig> HttpClientConfig<T>.configure(
    config: AppConfig,
    tokenStorage: TokenStorage,
    onSessionInvalidated: suspend () -> Unit,
) {
    expectSuccess = false

    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                isLenient = true
            },
        )
    }

    install(HttpTimeout) {
        requestTimeoutMillis = 30_000
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = 30_000
    }

    install(Logging) {
        level = if (config.enableNetworkLogging) LogLevel.INFO else LogLevel.NONE
    }

    install(Auth) {
        bearer {
            cacheTokens = false
            loadTokens {
                tokenStorage.getToken()
                    ?.takeIf(String::isNotBlank)
                    ?.let { BearerTokens(it, it) }
            }
            refreshTokens {
                val currentToken = oldTokens?.refreshToken?.takeIf(String::isNotBlank)
                if (currentToken == null) {
                    onSessionInvalidated()
                    return@refreshTokens null
                }

                val refreshClient = HttpClient(client.engine)
                val (status, responseBody) = try {
                    val response = refreshClient.post(
                        "${config.baseUrl.trimEnd('/')}/auth/refresh-token",
                    ) {
                        bearerAuth(currentToken)
                        headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        setBody("""{"token":"$currentToken"}""")
                    }
                    response.status to response.bodyAsText()
                } finally {
                    refreshClient.close()
                }
                if (status == HttpStatusCode.Unauthorized ||
                    status == HttpStatusCode.Forbidden
                ) {
                    onSessionInvalidated()
                    return@refreshTokens null
                }
                if (status.value !in 200..299) return@refreshTokens null

                val token = NETWORK_JSON
                    .decodeFromString<RefreshTokenEnvelope>(responseBody)
                    .data
                    .token
                    .takeIf(String::isNotBlank)
                    ?: return@refreshTokens null
                tokenStorage.saveToken(token)
                BearerTokens(token, token)
            }
            sendWithoutRequest { request ->
                request.url.encodedPath !in PUBLIC_AUTH_PATHS
            }
        }
    }

    defaultRequest {
        url(config.baseUrl)
        accept(ContentType.Application.Json)
        headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    }
}

@Serializable
private data class RefreshTokenResponse(val token: String)

@Serializable
private data class RefreshTokenEnvelope(val data: RefreshTokenResponse)

private val NETWORK_JSON = Json { ignoreUnknownKeys = true }

private val PUBLIC_AUTH_PATHS = setOf(
    "/auth/login",
    "/auth/refresh-token",
)
