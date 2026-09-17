package com.ih.osm.core.database

import app.cash.sqldelight.db.SqlDriver
import com.ih.osm.database.AppDatabase
import org.koin.dsl.module

val databaseModule = module {
    single<SqlDriver> { get<DatabaseDriverFactory>().createDriver() }
    single { AppDatabase(get()) }
}
