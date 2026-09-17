package com.ih.osm.di

import com.ih.osm.core.database.DatabaseDriverFactory
import com.ih.osm.core.database.IosDatabaseDriverFactory
import eu.anifantakis.lib.ksafe.KSafe
import org.koin.dsl.module

val iosPlatformModule = module {
    single { KSafe() }
    single<DatabaseDriverFactory> { IosDatabaseDriverFactory() }
}
