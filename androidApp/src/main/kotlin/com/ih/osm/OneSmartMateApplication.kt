package com.ih.osm

import android.app.Application
import com.ih.osm.core.config.AppConfig
import com.ih.osm.core.config.AppEnvironment
import com.ih.osm.core.crashreporting.CrashReportingInitializer
import com.ih.osm.di.androidAppModule
import com.ih.osm.di.initKoinAndroid
import org.koin.core.context.loadKoinModules

class OneSmartMateApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        CrashReportingInitializer.initialize(
            environment = BuildConfig.APP_ENVIRONMENT,
        )

        initKoinAndroid(
            application = this,
            config = AppConfig.create(
                baseUrl = BuildConfig.API_BASE_URL,
                environment = AppEnvironment.from(BuildConfig.APP_ENVIRONMENT),
                enableNetworkLogging = BuildConfig.ENABLE_NETWORK_LOGGING,
            ),
        )
        loadKoinModules(androidAppModule)
    }
}
