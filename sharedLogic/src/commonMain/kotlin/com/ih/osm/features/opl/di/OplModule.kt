package com.ih.osm.features.opl.di

import com.ih.osm.features.opl.data.remote.OplApiService
import com.ih.osm.features.opl.data.repository.OplRepositoryImpl
import com.ih.osm.features.opl.domain.repository.OplRepository
import org.koin.dsl.module

val oplModule = module {
    single { OplApiService(get()) }
    single<OplRepository> { OplRepositoryImpl(get(), get(), get()) }
}
