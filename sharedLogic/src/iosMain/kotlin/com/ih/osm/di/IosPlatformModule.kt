package com.ih.osm.di

import com.ih.osm.core.database.DatabaseDriverFactory
import com.ih.osm.core.database.IosDatabaseDriverFactory
import com.ih.osm.features.auth.domain.session.SessionRepository
import com.ih.osm.features.catalog.IosCatalogSyncController
import com.ih.osm.features.catalog.domain.usecase.BuildCatalogSyncPlanUseCase
import com.ih.osm.features.catalog.domain.usecase.SyncCatalogsUseCase
import eu.anifantakis.lib.ksafe.KSafe
import org.koin.dsl.module

val iosPlatformModule = module {
    single { KSafe() }
    single<DatabaseDriverFactory> { IosDatabaseDriverFactory() }
    single {
        IosCatalogSyncController(
            sessionRepository = get<SessionRepository>(),
            buildCatalogSyncPlan = get<BuildCatalogSyncPlanUseCase>(),
            syncCatalogs = get<SyncCatalogsUseCase>(),
        )
    }
}
