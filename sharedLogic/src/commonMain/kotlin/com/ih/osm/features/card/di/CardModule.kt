package com.ih.osm.features.card.di

import com.ih.osm.features.card.data.remote.CardApiService
import com.ih.osm.features.card.data.repository.CardRepositoryImpl
import com.ih.osm.features.card.domain.create.CreateCardManager
import com.ih.osm.features.card.domain.repository.CardRepository
import com.ih.osm.features.card.domain.solution.CardSolutionManager
import com.ih.osm.features.card.domain.usecase.SyncPendingCardsUseCase
import com.ih.osm.features.card.domain.usecase.SyncPendingSolutionsUseCase
import org.koin.dsl.module

val cardModule = module {
    single { CardApiService(get()) }
    single<CardRepository> { CardRepositoryImpl(get(), get()) }
    single { SyncPendingCardsUseCase(get()) }
    single { SyncPendingSolutionsUseCase(get()) }
    factory { CreateCardManager(get(), get(), get(), get(), get(), get()) }
    factory { CardSolutionManager(get(), get(), get()) }
}
