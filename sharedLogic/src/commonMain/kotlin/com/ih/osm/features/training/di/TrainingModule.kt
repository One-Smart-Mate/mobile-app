package com.ih.osm.features.training.di

import com.ih.osm.features.training.data.remote.TrainingApiService
import com.ih.osm.features.training.data.repository.TrainingRepositoryImpl
import com.ih.osm.features.training.domain.repository.TrainingRepository
import org.koin.dsl.module

val trainingModule = module {
    single { TrainingApiService(get()) }
    single<TrainingRepository> { TrainingRepositoryImpl(get(), get()) }
}
