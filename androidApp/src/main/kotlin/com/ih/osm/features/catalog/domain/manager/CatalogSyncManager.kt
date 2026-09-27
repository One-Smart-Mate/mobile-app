package com.ih.osm.features.catalog.domain.manager

import com.ih.osm.features.auth.domain.model.AuthenticatedUser
import com.ih.osm.features.catalog.domain.model.CatalogKind
import com.ih.osm.features.catalog.domain.model.CatalogSyncStatus
import kotlinx.coroutines.flow.Flow

interface CatalogSyncManager {
    suspend fun enqueueIfNeeded(user: AuthenticatedUser)
    fun enqueueManual(user: AuthenticatedUser, catalogs: Set<CatalogKind>)
    fun observe(user: AuthenticatedUser): Flow<CatalogSyncStatus>
    suspend fun cancel(user: AuthenticatedUser)
}
