package com.ih.osm.features.card.domain.repository

import com.ih.osm.core.network.NetworkResult
import com.ih.osm.features.card.domain.model.Card
import kotlinx.coroutines.flow.Flow

interface CardRepository {
    /** The database is the only source consumed by presentation layers. */
    fun observeCards(): Flow<List<Card>>

    /**
     * Downloads a complete snapshot first and only then updates the database.
     * Existing local cards are preserved until the server acknowledges them.
     */
    suspend fun refresh(siteIds: List<Long>): NetworkResult<Unit>

    /** Prepared for the create-card flow: observers are notified immediately. */
    suspend fun saveLocal(card: Card)

    suspend fun saveSynced(card: Card)

    fun observePendingCount(): Flow<Long>

    fun pendingCount(): Long

    fun getPending(limit: Long = 25): List<Card>

    fun markSyncing(uuid: String)

    fun markSyncFailed(uuid: String, message: String)

    suspend fun sync(cards: List<Card>): NetworkResult<List<CardSyncOutcome>>
}

data class CardSyncOutcome(
    val uuid: String,
    val success: Boolean,
    val card: Card? = null,
    val statusCode: Int? = null,
    val message: String? = null,
)
