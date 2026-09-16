package com.ih.osm.di

import com.ih.osm.core.config.AppConfig
import com.ih.osm.core.config.AppEnvironment
import org.koin.core.context.startKoin

fun initKoinIos(
    baseUrl: String,
    environment: String,
    enableNetworkLogging: Boolean,
) {
    val config = AppConfig.create(
        baseUrl = baseUrl,
        environment = AppEnvironment.from(environment),
        enableNetworkLogging = enableNetworkLogging,
    )

    startKoin {
        modules(sharedModules(config))
    }
}
