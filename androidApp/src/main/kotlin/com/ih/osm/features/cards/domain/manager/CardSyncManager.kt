package com.ih.osm.features.cards.domain.manager

import com.ih.osm.features.cards.domain.model.CardSyncWorkStatus
import kotlinx.coroutines.flow.Flow

interface CardSyncManager {
    val workStatus: Flow<CardSyncWorkStatus>

    fun enqueueAfterCardCreated()
    fun enqueueManually(): Boolean
    suspend fun cancel()
}
