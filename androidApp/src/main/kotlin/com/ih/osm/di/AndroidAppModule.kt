package com.ih.osm.di

import com.ih.osm.AppViewModel
import com.ih.osm.core.network.AndroidNetworkStatusMonitor
import com.ih.osm.core.network.NetworkStatusMonitor
import com.ih.osm.features.auth.login.LoginViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val androidAppModule = module {
    single<NetworkStatusMonitor> { AndroidNetworkStatusMonitor(get()) }
    viewModelOf(::AppViewModel)
    viewModelOf(::LoginViewModel)
}
