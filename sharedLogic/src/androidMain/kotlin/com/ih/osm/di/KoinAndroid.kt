package com.ih.osm.di

import android.app.Application
import com.ih.osm.core.config.AppConfig
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

fun initKoinAndroid(
    application: Application,
    config: AppConfig,
) {
    startKoin {
        androidContext(application)
        modules(sharedModules(config) + androidPlatformModule)
    }
}
