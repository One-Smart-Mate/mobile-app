package com.ih.osm.di

import com.ih.osm.AppViewModel
import com.ih.osm.core.network.AndroidNetworkStatusMonitor
import com.ih.osm.core.network.NetworkStatusMonitor
import com.ih.osm.features.auth.login.LoginViewModel
import com.ih.osm.features.auth.passwordrecovery.ForgotPasswordViewModel
import com.ih.osm.features.cards.CardListViewModel
import com.ih.osm.features.cards.data.sync.CardSyncScheduler
import com.ih.osm.features.cards.data.sync.CardSyncNetworkPolicy
import com.ih.osm.features.cards.data.sync.CardSyncTriggerStore
import com.ih.osm.features.cards.data.sync.ServiceCardEvidenceUploader
import com.ih.osm.features.cards.domain.manager.CardSyncManager
import com.ih.osm.features.carddetail.CardDetailViewModel
import com.ih.osm.features.cardsolution.CardSolutionViewModel
import com.ih.osm.features.carddetail.data.cache.EvidenceFileCache
import com.ih.osm.features.carddetail.domain.cache.EvidenceCache
import com.ih.osm.features.catalog.data.sync.CatalogSyncScheduler
import com.ih.osm.features.catalog.domain.manager.CatalogSyncManager
import com.ih.osm.features.createcard.CreateCardViewModel
import com.ih.osm.features.createcard.data.storage.AndroidEvidenceStorage
import com.ih.osm.features.createcard.domain.storage.EvidenceStorage
import com.ih.osm.features.home.HomeViewModel
import com.ih.osm.features.notifications.data.firebase.FirebaseTokenRegistrationScheduler
import com.ih.osm.features.notifications.data.firebase.FirebaseTokenStore
import com.ih.osm.features.permissions.PermissionsViewModel
import com.ih.osm.features.permissions.data.manager.AndroidPermissionManager
import com.ih.osm.features.permissions.domain.manager.PermissionManager
import com.ih.osm.features.settings.SettingsViewModel
import com.ih.osm.features.settings.data.local.SharedPreferencesMobileDataSyncPreferences
import com.ih.osm.features.settings.data.manager.AndroidLogoutManager
import com.ih.osm.features.settings.domain.manager.LogoutManager
import com.ih.osm.features.settings.domain.preferences.MobileDataSyncPreferences
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val androidAppModule = module {
    single<NetworkStatusMonitor> { AndroidNetworkStatusMonitor(get()) }
    single<MobileDataSyncPreferences> { SharedPreferencesMobileDataSyncPreferences(get()) }
    single<CatalogSyncManager> { CatalogSyncScheduler(get(), get(), get()) }
    single { CardSyncTriggerStore(get()) }
    single { CardSyncNetworkPolicy(get(), get()) }
    single<CardSyncManager> { CardSyncScheduler(get(), get(), get(), get(), get()) }
    single { FirebaseTokenStore(get()) }
    single { FirebaseTokenRegistrationScheduler(get(), get()) }
    single<EvidenceCache> { EvidenceFileCache(get(), get()) }
    single { ServiceCardEvidenceUploader(get(), get()) }
    single<EvidenceStorage> { AndroidEvidenceStorage(get()) }
    single<PermissionManager> { AndroidPermissionManager(get()) }
    single<LogoutManager> { AndroidLogoutManager(get(), get(), get(), get(), get(), get()) }
    viewModelOf(::AppViewModel)
    viewModelOf(::LoginViewModel)
    viewModelOf(::ForgotPasswordViewModel)
    viewModelOf(::CardListViewModel)
    viewModelOf(::CardDetailViewModel)
    viewModelOf(::CreateCardViewModel)
    viewModelOf(::CardSolutionViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::PermissionsViewModel)
    viewModelOf(::SettingsViewModel)
}
