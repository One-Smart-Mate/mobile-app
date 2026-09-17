package com.ih.osm.di

import com.ih.osm.core.database.AndroidDatabaseDriverFactory
import com.ih.osm.core.database.DatabaseDriverFactory
import eu.anifantakis.lib.ksafe.KSafe
import org.koin.android.ext.koin.androidApplication
import org.koin.dsl.module

val androidPlatformModule = module {
    single { KSafe(context = androidApplication()) }
    single<DatabaseDriverFactory> { AndroidDatabaseDriverFactory(androidApplication()) }
}
