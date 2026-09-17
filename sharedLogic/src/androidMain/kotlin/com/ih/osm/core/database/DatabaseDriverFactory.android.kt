package com.ih.osm.core.database

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.ih.osm.database.AppDatabase

class AndroidDatabaseDriverFactory(
    private val context: Context,
) : DatabaseDriverFactory {
    override fun createDriver(): SqlDriver =
        AndroidSqliteDriver(AppDatabase.Schema, context, DATABASE_NAME)

    private companion object {
        const val DATABASE_NAME = "one_smart_mate.db"
    }
}
