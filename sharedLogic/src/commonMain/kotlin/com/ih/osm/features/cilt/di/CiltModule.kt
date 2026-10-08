package com.ih.osm.features.cilt.di

import com.ih.osm.features.cilt.data.remote.CiltApiService
import com.ih.osm.features.cilt.data.repository.CiltRepositoryImpl
import com.ih.osm.features.cilt.domain.repository.CiltRepository
import org.koin.dsl.module

val ciltModule = module {
    single { CiltApiService(get()) }
    single<CiltRepository> { CiltRepositoryImpl(get(), get()) }
}
