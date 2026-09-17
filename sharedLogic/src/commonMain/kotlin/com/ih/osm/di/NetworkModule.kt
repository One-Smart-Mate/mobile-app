package com.ih.osm.di

import com.ih.osm.core.config.AppConfig
import com.ih.osm.core.network.NetworkClient
import com.ih.osm.core.network.createHttpClient
import com.ih.osm.core.database.databaseModule
import com.ih.osm.features.auth.di.authModule
import com.ih.osm.features.auth.domain.session.SessionRepository
import com.ih.osm.getPlatform
import org.koin.dsl.module

fun networkModule(config: AppConfig) = module {
    single { config }
    single { getPlatform() }
    single {
        val sessionRepository = get<SessionRepository>()
        createHttpClient(
            config = get(),
            tokenStorage = get(),
            onSessionInvalidated = sessionRepository::invalidate,
        )
    }
    single { NetworkClient(get()) }
}

fun sharedModules(config: AppConfig) = listOf(
    databaseModule,
    authModule,
    networkModule(config),
)
