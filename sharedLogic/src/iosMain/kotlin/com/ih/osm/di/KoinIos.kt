package com.ih.osm.di

import com.ih.osm.core.config.AppConfig
import com.ih.osm.core.config.AppEnvironment
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatform
import com.ih.osm.features.auth.IosAuthController
import com.ih.osm.features.auth.IosSessionController
import com.ih.osm.features.auth.domain.repository.AuthRepository
import com.ih.osm.features.auth.domain.session.SessionRepository
import com.ih.osm.features.auth.data.remote.PushTokenRegistrar
import com.ih.osm.features.catalog.IosCatalogSyncController
import com.ih.osm.features.card.IosCardListController
import com.ih.osm.features.card.IosCardDetailController
import com.ih.osm.features.card.IosCardSyncController
import com.ih.osm.features.card.IosCardSolutionController
import com.ih.osm.features.card.IosCreateCardController
import com.ih.osm.features.card.domain.create.CreateCardManager
import com.ih.osm.features.card.domain.repository.CardRepository
import com.ih.osm.features.card.domain.solution.CardSolutionManager
import com.ih.osm.features.card.domain.usecase.SyncPendingCardsUseCase
import com.ih.osm.features.card.domain.usecase.SyncPendingSolutionsUseCase
import com.ih.osm.features.notifications.IosPushNotificationController

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

fun createIosCardDetailController(): IosCardDetailController = IosCardDetailController(
    repository = KoinPlatform.getKoin().get<CardRepository>(),
)

fun createIosCreateCardController(): IosCreateCardController = IosCreateCardController(
    manager = KoinPlatform.getKoin().get<CreateCardManager>(),
    sessionRepository = KoinPlatform.getKoin().get<SessionRepository>(),
)

fun createIosCardSolutionController(): IosCardSolutionController = IosCardSolutionController(
    manager = KoinPlatform.getKoin().get<CardSolutionManager>(),
    sessionRepository = KoinPlatform.getKoin().get<SessionRepository>(),
)

fun createIosCardSyncController(): IosCardSyncController = IosCardSyncController(
    repository = KoinPlatform.getKoin().get<CardRepository>(),
    syncPendingCards = KoinPlatform.getKoin().get<SyncPendingCardsUseCase>(),
    syncPendingSolutions = KoinPlatform.getKoin().get<SyncPendingSolutionsUseCase>(),
)

fun createIosPushNotificationController(): IosPushNotificationController = IosPushNotificationController(
    registrar = KoinPlatform.getKoin().get<PushTokenRegistrar>(),
    sessionRepository = KoinPlatform.getKoin().get<SessionRepository>(),
)
