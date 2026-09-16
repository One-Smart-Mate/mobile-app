package com.ih.osm.core.network

import com.ih.osm.core.config.AppConfig
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.accept
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

internal fun createHttpClient(config: AppConfig): HttpClient = HttpClient {
    configure(config)
}

private fun <T : HttpClientEngineConfig> HttpClientConfig<T>.configure(config: AppConfig) {
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

    defaultRequest {
        url(config.baseUrl)
        accept(ContentType.Application.Json)
        headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    }
}
