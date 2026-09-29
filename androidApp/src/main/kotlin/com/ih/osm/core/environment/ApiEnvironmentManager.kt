package com.ih.osm.core.environment

import com.ih.osm.core.config.AppConfig
import com.ih.osm.core.config.AppEnvironment
import com.ih.osm.core.crashreporting.CrashReportingInitializer

class ApiEnvironmentManager(
    private val appConfig: AppConfig,
    private val preferences: ApiEnvironmentPreferences,
    private val developmentBaseUrl: String,
    private val productionBaseUrl: String,
) {
    val currentEnvironment: AppEnvironment
        get() = appConfig.environment

    fun select(environment: AppEnvironment) {
        appConfig.update(
            baseUrl = baseUrlFor(environment),
            environment = environment,
        )
        preferences.save(environment)
        CrashReportingInitializer.updateApiEnvironment(environment.name.lowercase())
    }

    private fun baseUrlFor(environment: AppEnvironment): String = when (environment) {
        AppEnvironment.DEV -> developmentBaseUrl
        AppEnvironment.PROD -> productionBaseUrl
    }
}
