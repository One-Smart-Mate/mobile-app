package com.ih.osm.features.settings

import com.ih.osm.database.AppDatabase
import com.ih.osm.features.auth.domain.repository.AuthRepository
import com.ih.osm.features.card.IosEvidenceFileCache
import com.ih.osm.features.card.domain.repository.CardRepository
import com.ih.osm.features.opl.IosOplMediaCache
import com.ih.osm.features.opl.IosOplOperations
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * iOS-facing adapter for settings operations whose source of truth lives in shared code.
 * SwiftUI only owns presentation state; pending work and local-session cleanup stay here.
 */
class IosSettingsController(
    private val database: AppDatabase,
    private val repository: CardRepository,
    private val authRepository: AuthRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var pendingObservation: Job? = null

    fun start(onPendingCountChanged: (Long) -> Unit) {
        pendingObservation?.cancel()
        pendingObservation = scope.launch {
            repository.observePendingCount().collectLatest(onPendingCountChanged)
        }
    }

    fun pendingCount(): Long = repository.pendingCount()

    @Throws(Exception::class)
    suspend fun logout() {
        try {
            IosOplOperations.cancelAll()
            IosOplMediaCache.clearAll()
            database.transaction {
                database.oplOfflineQueries.deleteAllChunks()
                database.oplOfflineQueries.deleteAllLessons()
                database.cardsQueries.deleteAllEvidences()
                database.cardsQueries.deleteAllCards()
                database.cardsQueries.deleteAllSyncCursors()
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
            IosEvidenceFileCache.clearAll()
            authRepository.logout()
        } finally {
            IosOplOperations.resume()
        }
    }

    fun stop() {
        pendingObservation?.cancel()
        pendingObservation = null
    }
}
