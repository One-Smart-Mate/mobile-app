package com.ih.osm.di

import com.ih.osm.AppViewModel
import com.ih.osm.core.network.AndroidNetworkStatusMonitor
import com.ih.osm.core.network.NetworkStatusMonitor
import com.ih.osm.features.auth.login.LoginViewModel
import com.ih.osm.features.cards.CardListViewModel
import com.ih.osm.features.cards.sync.CardSyncScheduler
import com.ih.osm.features.createcard.CreateCardViewModel
import com.ih.osm.features.home.HomeViewModel
import com.ih.osm.features.permissions.PermissionHelper
import com.ih.osm.features.permissions.PermissionsViewModel
import com.ih.osm.features.catalog.sync.CatalogSyncScheduler
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val androidAppModule = module {
    single<NetworkStatusMonitor> { AndroidNetworkStatusMonitor(get()) }
    single { CatalogSyncScheduler(get(), get()) }
    single { CardSyncScheduler(get(), get()) }
    single { PermissionHelper(get()) }
    viewModelOf(::AppViewModel)
    viewModelOf(::LoginViewModel)
    viewModelOf(::CardListViewModel)
    viewModelOf(::CreateCardViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::PermissionsViewModel)
}
