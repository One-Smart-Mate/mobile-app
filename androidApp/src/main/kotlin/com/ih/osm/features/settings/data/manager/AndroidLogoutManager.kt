package com.ih.osm.features.settings.data.manager

import android.content.Context
import com.ih.osm.database.AppDatabase
import com.ih.osm.features.auth.domain.model.AuthenticatedUser
import com.ih.osm.features.auth.domain.repository.AuthRepository
import com.ih.osm.features.cards.domain.manager.CardSyncManager
import com.ih.osm.features.catalog.domain.manager.CatalogSyncManager
import com.ih.osm.features.settings.domain.manager.LogoutManager
import com.ih.osm.features.settings.domain.preferences.MobileDataSyncPreferences
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidLogoutManager(
    context: Context,
    private val database: AppDatabase,
    private val authRepository: AuthRepository,
    private val cardSyncManager: CardSyncManager,
    private val catalogSyncManager: CatalogSyncManager,
    private val syncPreferences: MobileDataSyncPreferences,
) : LogoutManager {
    private val appContext = context.applicationContext

    override suspend fun logout(user: AuthenticatedUser) = withContext(Dispatchers.IO) {
        runCatching { cardSyncManager.cancel() }
        runCatching { catalogSyncManager.cancel(user) }

        database.transaction {
            database.cardsQueries.deleteAllEvidences()
            database.cardsQueries.deleteAllCards()
            database.catalogsQueries.deleteAllCatalogSyncStates()
            database.catalogsQueries.deleteAllCatalogSyncMetadata()
            database.catalogsQueries.deleteAllEmployees()
            database.catalogsQueries.deleteAllLevels()
            database.catalogsQueries.deleteAllPriorities()
            database.catalogsQueries.deleteAllPreclassifiers()
            database.catalogsQueries.deleteAllCardTypes()
            database.userSessionQueries.deleteRoles()
            database.userSessionQueries.deleteSites()
            database.userSessionQueries.deleteUser()
        }

        deleteDirectoryContents(File(appContext.filesDir, CARD_EVIDENCE_DIRECTORY))
        deleteDirectoryContents(File(appContext.cacheDir, CARD_EVIDENCE_DIRECTORY))
        syncPreferences.reset()
        authRepository.logout()
    }

    private fun deleteDirectoryContents(directory: File) {
        directory.listFiles()?.forEach { file -> runCatching { file.deleteRecursively() } }
    }

    private companion object {
        const val CARD_EVIDENCE_DIRECTORY = "card_evidence"
    }
}
