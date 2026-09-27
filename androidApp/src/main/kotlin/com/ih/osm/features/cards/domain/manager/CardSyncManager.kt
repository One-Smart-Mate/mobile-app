package com.ih.osm.features.cards.domain.manager

import com.ih.osm.features.cards.domain.model.CardSyncWorkStatus
import kotlinx.coroutines.flow.Flow

interface CardSyncManager {
    val workStatus: Flow<CardSyncWorkStatus>

    fun enqueueAfterLocalChange()
    fun enqueueRemoteChanges(siteIds: Collection<Long>)
    fun enqueueRemoteChanges(siteId: Long) = enqueueRemoteChanges(listOf(siteId))
    fun enqueueManually(): Boolean
    suspend fun cancel()
}
