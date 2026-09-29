package com.ih.osm

import android.app.Application
import com.ih.osm.core.config.AppConfig
import com.ih.osm.core.config.AppEnvironment
import com.ih.osm.core.crashreporting.CrashReportingInitializer
import com.ih.osm.core.environment.ApiEnvironmentPreferences
import com.ih.osm.di.androidAppModule
import com.ih.osm.di.initKoinAndroid
import org.koin.core.context.loadKoinModules

class OneSmartMateApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val apiEnvironment = ApiEnvironmentPreferences(this)
            .selectedEnvironmentOrNull()
            ?: AppEnvironment.from(BuildConfig.APP_ENVIRONMENT)
        val apiBaseUrl = when (apiEnvironment) {
            AppEnvironment.DEV -> BuildConfig.DEV_API_BASE_URL
            AppEnvironment.PROD -> BuildConfig.PROD_API_BASE_URL
        }

        CrashReportingInitializer.initialize(
            buildEnvironment = BuildConfig.APP_ENVIRONMENT,
            apiEnvironment = apiEnvironment.name.lowercase(),
        )

        initKoinAndroid(
            application = this,
            config = AppConfig.create(
                baseUrl = apiBaseUrl,
                environment = apiEnvironment,
                enableNetworkLogging = BuildConfig.ENABLE_NETWORK_LOGGING,
            ),
        )
        loadKoinModules(androidAppModule)
    }
}
