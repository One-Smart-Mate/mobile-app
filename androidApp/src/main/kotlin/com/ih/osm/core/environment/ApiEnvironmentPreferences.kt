package com.ih.osm.core.environment

import android.content.Context
import com.ih.osm.core.config.AppEnvironment

class ApiEnvironmentPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun selectedEnvironmentOrNull(): AppEnvironment? = preferences
        .getString(KEY_SELECTED_ENVIRONMENT, null)
        ?.let { stored -> runCatching { AppEnvironment.from(stored) }.getOrNull() }

    fun save(environment: AppEnvironment) {
        preferences.edit()
            .putString(KEY_SELECTED_ENVIRONMENT, environment.name.lowercase())
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "api_environment_preferences"
        const val KEY_SELECTED_ENVIRONMENT = "selected_environment"
    }
}
