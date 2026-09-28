package com.ih.osm.core.crashreporting

import com.google.firebase.crashlytics.FirebaseCrashlytics

object CrashReportingInitializer {
    fun initialize(environment: String) {
        FirebaseCrashlytics.getInstance().apply {
            setCrashlyticsCollectionEnabled(true)
            setCustomKey(ENVIRONMENT_KEY, environment)
        }
    }

    private const val ENVIRONMENT_KEY = "environment"
}
