package com.ih.osm.core.config

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

@ConsistentCopyVisibility
data class AppConfig private constructor(
    val baseUrl: String,
    val environment: AppEnvironment,
    val enableNetworkLogging: Boolean,
) {
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
}
