package com.ih.osm.core.crashreporting

import com.google.firebase.crashlytics.FirebaseCrashlytics

object CrashReportingInitializer {
    fun initialize(
        buildEnvironment: String,
        apiEnvironment: String,
    ) {
        FirebaseCrashlytics.getInstance().apply {
            isCrashlyticsCollectionEnabled = true
            setCustomKey(BUILD_ENVIRONMENT_KEY, buildEnvironment)
            setCustomKey(API_ENVIRONMENT_KEY, apiEnvironment)
        }
    }

    fun updateApiEnvironment(environment: String) {
        FirebaseCrashlytics.getInstance().setCustomKey(API_ENVIRONMENT_KEY, environment)
    }

    private const val BUILD_ENVIRONMENT_KEY = "build_environment"
    private const val API_ENVIRONMENT_KEY = "api_environment"
}
