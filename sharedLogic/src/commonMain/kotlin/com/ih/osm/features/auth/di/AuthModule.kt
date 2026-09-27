package com.ih.osm.features.auth.di

import com.ih.osm.core.auth.TokenStorage
import com.ih.osm.core.auth.TokenStorageImpl
import com.ih.osm.features.auth.data.local.AuthLocalDataSource
import com.ih.osm.features.auth.data.local.AuthLocalDataSourceImpl
import com.ih.osm.features.auth.data.remote.AuthApiService
import com.ih.osm.features.auth.data.remote.PushTokenRegistrar
import com.ih.osm.features.auth.data.repository.AuthRepositoryImpl
import com.ih.osm.features.auth.data.repository.SessionRepositoryImpl
import com.ih.osm.features.auth.domain.repository.AuthRepository
import com.ih.osm.features.auth.domain.session.SessionRepository
import com.ih.osm.features.auth.passwordrecovery.data.remote.PasswordRecoveryApiService
import com.ih.osm.features.auth.passwordrecovery.data.repository.PasswordRecoveryRepositoryImpl
import com.ih.osm.features.auth.passwordrecovery.domain.manager.PasswordRecoveryManager
import com.ih.osm.features.auth.passwordrecovery.domain.repository.PasswordRecoveryRepository
import org.koin.dsl.module

val authModule = module {
    single<TokenStorage> { TokenStorageImpl(get()) }
    single<AuthLocalDataSource> { AuthLocalDataSourceImpl(get()) }
    single<SessionRepository> { SessionRepositoryImpl(get(), get()) }
    single { AuthApiService(get(), get()) }
    single { PushTokenRegistrar(get(), get()) }
    single<AuthRepository> { AuthRepositoryImpl(get(), get(), get()) }
    single { PasswordRecoveryApiService(get()) }
    single<PasswordRecoveryRepository> { PasswordRecoveryRepositoryImpl(get()) }
    factory { PasswordRecoveryManager(get()) }
}
