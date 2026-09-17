package com.ih.osm.features.catalog.di

import com.ih.osm.features.catalog.data.remote.CatalogApiService
import com.ih.osm.features.catalog.data.repository.CatalogSyncRepositoryImpl
import com.ih.osm.features.catalog.domain.repository.CatalogSyncRepository
import com.ih.osm.features.cardtype.data.repository.CardTypeRepositoryImpl
import com.ih.osm.features.cardtype.domain.repository.CardTypeRepository
import com.ih.osm.features.employee.data.repository.EmployeeRepositoryImpl
import com.ih.osm.features.employee.domain.repository.EmployeeRepository
import com.ih.osm.features.level.data.repository.LevelRepositoryImpl
import com.ih.osm.features.level.domain.repository.LevelRepository
import com.ih.osm.features.preclassifier.data.repository.PreclassifierRepositoryImpl
import com.ih.osm.features.preclassifier.domain.repository.PreclassifierRepository
import com.ih.osm.features.priority.data.repository.PriorityRepositoryImpl
import com.ih.osm.features.priority.domain.repository.PriorityRepository
import com.ih.osm.features.catalog.domain.usecase.BuildCatalogSyncPlanUseCase
import com.ih.osm.features.catalog.domain.usecase.SyncCatalogsUseCase
import org.koin.dsl.module

val catalogModule = module {
    single { CatalogApiService(get()) }
    single<CardTypeRepository> { CardTypeRepositoryImpl(get(), get()) }
    single<PreclassifierRepository> { PreclassifierRepositoryImpl(get(), get()) }
    single<PriorityRepository> { PriorityRepositoryImpl(get(), get()) }
    single<LevelRepository> { LevelRepositoryImpl(get(), get()) }
    single<EmployeeRepository> { EmployeeRepositoryImpl(get(), get()) }
    single<CatalogSyncRepository> {
        CatalogSyncRepositoryImpl(get(), get(), get(), get(), get(), get())
    }
    single { SyncCatalogsUseCase(get(), get(), get(), get(), get(), get()) }
    single { BuildCatalogSyncPlanUseCase(get(), get(), get(), get(), get(), get()) }
}
