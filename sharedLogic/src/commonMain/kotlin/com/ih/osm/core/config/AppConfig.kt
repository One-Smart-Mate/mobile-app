package com.ih.osm.core.config

import kotlinx.coroutines.flow.MutableStateFlow

enum class AppEnvironment {
    DEV,
    PROD;

    companion object {
        fun from(value: String): AppEnvironment = when (value.trim().lowercase()) {
            "dev", "development" -> DEV
            "prod", "production" -> PROD
            else -> error("Unsupported application environment: $value")
        }
    }
}

class AppConfig private constructor(
    baseUrl: String,
    environment: AppEnvironment,
    enableNetworkLogging: Boolean,
) {
    private val values = MutableStateFlow(
        Values(
            baseUrl = baseUrl,
            environment = environment,
            enableNetworkLogging = enableNetworkLogging,
        ),
    )

    val baseUrl: String
        get() = values.value.baseUrl
    val environment: AppEnvironment
        get() = values.value.environment
    val enableNetworkLogging: Boolean
        get() = values.value.enableNetworkLogging

    fun update(
        baseUrl: String,
        environment: AppEnvironment,
    ) {
        val updated = create(
            baseUrl = baseUrl,
            environment = environment,
            enableNetworkLogging = environment == AppEnvironment.DEV,
        )
        values.value = updated.values.value
    }

    companion object {
        fun create(
            baseUrl: String,
            environment: AppEnvironment,
            enableNetworkLogging: Boolean,
        ): AppConfig {
            val normalizedBaseUrl = baseUrl.trim().trimEnd('/') + "/"
            require(
                normalizedBaseUrl.startsWith("https://") ||
                    normalizedBaseUrl.startsWith("http://"),
            ) {
                "API base URL must use HTTP or HTTPS."
            }
            require(environment != AppEnvironment.PROD || normalizedBaseUrl.startsWith("https://")) {
                "Production API base URL must use HTTPS."
            }

            return AppConfig(
                baseUrl = normalizedBaseUrl,
                environment = environment,
                enableNetworkLogging = environment == AppEnvironment.DEV,
            )
        }
    }

    private data class Values(
        val baseUrl: String,
        val environment: AppEnvironment,
        val enableNetworkLogging: Boolean,
    )
}
