package com.ih.osm.di

import com.ih.osm.AppViewModel
import com.ih.osm.features.auth.login.LoginViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val androidAppModule = module {
    viewModelOf(::AppViewModel)
    viewModelOf(::LoginViewModel)
}
