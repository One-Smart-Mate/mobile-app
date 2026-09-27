package com.ih.osm.di

import com.ih.osm.core.config.AppConfig
import com.ih.osm.core.config.AppEnvironment
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatform
import com.ih.osm.features.auth.IosAuthController
import com.ih.osm.features.auth.IosSessionController
import com.ih.osm.features.auth.domain.repository.AuthRepository
import com.ih.osm.features.auth.domain.session.SessionRepository
import com.ih.osm.features.catalog.IosCatalogSyncController
import com.ih.osm.features.card.IosCardListController
import com.ih.osm.features.card.domain.repository.CardRepository

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
        modules(sharedModules(config) + iosPlatformModule)
    }
}

fun createIosAuthController(): IosAuthController = IosAuthController(
    authRepository = KoinPlatform.getKoin().get<AuthRepository>(),
)

fun createIosSessionController(): IosSessionController = IosSessionController(
    sessionRepository = KoinPlatform.getKoin().get<SessionRepository>(),
)

fun createIosCatalogSyncController(): IosCatalogSyncController =
    KoinPlatform.getKoin().get<IosCatalogSyncController>()

fun createIosCardListController(): IosCardListController = IosCardListController(
    repository = KoinPlatform.getKoin().get<CardRepository>(),
)
