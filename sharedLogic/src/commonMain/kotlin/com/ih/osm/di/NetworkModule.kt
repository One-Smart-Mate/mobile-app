package com.ih.osm.di

import com.ih.osm.core.config.AppConfig
import com.ih.osm.core.network.NetworkClient
import com.ih.osm.core.network.createHttpClient
import org.koin.dsl.module

fun networkModule(config: AppConfig) = module {
    single { config }
    single { createHttpClient(get()) }
    single { NetworkClient(get()) }
}

fun sharedModules(config: AppConfig) = listOf(
    networkModule(config),
)
