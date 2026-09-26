package com.ih.osm.features.card.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.ih.osm.core.network.NetworkResult
import com.ih.osm.database.AppDatabase
import com.ih.osm.database.CardRecord
import com.ih.osm.features.card.data.remote.CardApiService
import com.ih.osm.features.card.data.remote.CreateCardRequestDto
import com.ih.osm.features.card.domain.model.Card
import com.ih.osm.features.card.domain.model.CardSyncState
import com.ih.osm.features.card.domain.repository.CardSyncOutcome
import com.ih.osm.features.card.domain.repository.CardRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class CardRepositoryImpl(
    private val api: CardApiService,
    private val database: AppDatabase,
) : CardRepository {
    override fun observeCards(): Flow<List<Card>> =
        database.cardsQueries
            .selectAllCards()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun refresh(siteIds: List<Long>): NetworkResult<Unit> {
        if (siteIds.isEmpty()) return NetworkResult.Success(Unit, statusCode = 200)

        val snapshots = linkedMapOf<Long, List<Card>>()
        var lastStatusCode = 200
        for (siteId in siteIds.distinct()) {
            val cards = mutableListOf<Card>()
            var page = FIRST_PAGE
            do {
                when (val result = api.getCards(siteId, page, PAGE_SIZE)) {
                    is NetworkResult.Failure -> return result
                    is NetworkResult.Success -> {
                        lastStatusCode = result.statusCode
                        val response = result.data
                        cards += response.data.map { it.toDomain(siteId) }
                        val hasAnotherPage = response.hasMore ||
                            (response.totalPages != null && page < response.totalPages)
                        page += 1
                        if (!hasAnotherPage || response.data.isEmpty()) break
                    }
                }
            } while (page <= MAX_PAGE_GUARD)
            snapshots[siteId] = cards.distinctBy(Card::uuid)
        }

        database.transaction {
            snapshots.forEach { (siteId, cards) ->
                database.cardsQueries.deleteRemoteCardsBySite(siteId)
                cards.forEach(::upsert)
            }
        }
        return NetworkResult.Success(Unit, lastStatusCode)
    }

    override suspend fun saveLocal(card: Card) {
        database.transaction {
            upsert(
                card.copy(
                    isLocal = true,
                    syncState = CardSyncState.PENDING,
                    syncError = null,
                ),
            )
        }
    }

    override suspend fun saveSynced(card: Card) {
        database.transaction {
            upsert(
                card.copy(
                    isLocal = false,
                    syncState = CardSyncState.SYNCED,
                    syncError = null,
                ),
            )
        }
    }

    override fun pendingCount(): Long = database.cardsQueries.countPendingCards().executeAsOne()

    override fun getPending(limit: Long): List<Card> =
        database.cardsQueries.selectPendingCards(limit).executeAsList().map { it.toDomain() }

    override fun markSyncing(uuid: String) {
        database.cardsQueries.markCardSyncing(uuid)
    }

    override fun markSyncFailed(uuid: String, message: String) {
        database.cardsQueries.markCardSyncFailed(message, uuid)
    }

    override suspend fun sync(cards: List<Card>): NetworkResult<List<CardSyncOutcome>> {
        if (cards.isEmpty()) return NetworkResult.Success(emptyList(), 200)
        val requests = cards.map { card ->
            val nodeId = card.nodeId?.toLongOrNull()
            val priorityId = card.priorityId?.toLongOrNull()
            val cardTypeId = card.cardTypeId?.toLongOrNull()
            val preclassifierId = card.preclassifierId?.toLongOrNull()
            if (nodeId == null || priorityId == null || cardTypeId == null || preclassifierId == null) {
                return NetworkResult.Failure(
                    com.ih.osm.core.network.NetworkError(
                        kind = com.ih.osm.core.network.NetworkErrorKind.SERIALIZATION,
                        message = "The local card is missing required catalog identifiers.",
                    ),
                )
            }
            CreateCardRequestDto(
                siteId = card.siteId,
                uuid = card.uuid,
                cardCreationDate = card.creationDate,
                nodeId = nodeId,
                priorityId = priorityId,
                cardTypeValue = card.cardTypeValue,
                cardTypeId = cardTypeId,
                preclassifierId = preclassifierId,
                comments = card.comments,
                evidences = emptyList(),
                appSo = card.appSo,
                appVersion = card.appVersion,
                customDueDate = card.customDueDate,
                notifyResponsible = card.notifyResponsible,
            )
        }
        val siteByUuid = cards.associate { it.uuid to it.siteId }
        return when (val result = api.syncCards(requests)) {
            is NetworkResult.Failure -> result
            is NetworkResult.Success -> NetworkResult.Success(
                data = result.data.results.map { item ->
                    CardSyncOutcome(
                        uuid = item.uuid,
                        success = item.success,
                        card = item.card?.toDomain(siteByUuid[item.uuid] ?: 0),
                        message = item.message,
                    )
                },
                statusCode = result.statusCode,
            )
        }
    }

    private fun upsert(card: Card) {
        database.cardsQueries.upsertCard(
            uuid = card.uuid,
            server_id = card.serverId,
            site_card_id = card.siteCardId,
            site_id = card.siteId,
            site_code = card.siteCode,
            card_type_color = card.cardTypeColor,
            status = card.status,
            creation_date = card.creationDate,
            due_date = card.dueDate,
            priority_id = card.priorityId,
            priority_code = card.priorityCode,
            priority_description = card.priorityDescription,
            node_id = card.nodeId,
            node_name = card.nodeName,
            card_type_id = card.cardTypeId,
            card_type_name = card.cardTypeName,
            card_type_value = card.cardTypeValue,
            preclassifier_id = card.preclassifierId,
            preclassifier_code = card.preclassifierCode,
            preclassifier_description = card.preclassifierDescription,
            creator_id = card.creatorId,
            creator_name = card.creatorName,
            responsible_id = card.responsibleId,
            responsible_name = card.responsibleName,
            mechanic_id = card.mechanicId,
            mechanic_name = card.mechanicName,
            comments = card.comments,
            evidence_audio_creation = card.evidenceAudioCreation,
            evidence_video_creation = card.evidenceVideoCreation,
            evidence_image_creation = card.evidenceImageCreation,
            card_location = card.location,
            definitive_solution_date = card.definitiveSolutionDate,
            is_local = if (card.isLocal) 1L else 0L,
            has_local_solutions = if (card.hasLocalSolutions) 1L else 0L,
            updated_at = card.updatedAt,
            app_so = card.appSo,
            app_version = card.appVersion,
            custom_due_date = card.customDueDate,
            notify_responsible = if (card.notifyResponsible) 1L else 0L,
            sync_state = card.syncState.name,
            sync_error = card.syncError,
            sync_attempts = card.syncAttempts,
        )
    }

    private fun CardRecord.toDomain() = Card(
        uuid = uuid,
        serverId = server_id,
        siteCardId = site_card_id,
        siteId = site_id,
        siteCode = site_code,
        cardTypeColor = card_type_color,
        status = status,
        creationDate = creation_date,
        dueDate = due_date,
        priorityId = priority_id,
        priorityCode = priority_code,
        priorityDescription = priority_description,
        nodeId = node_id,
        nodeName = node_name,
        cardTypeId = card_type_id,
        cardTypeName = card_type_name,
        cardTypeValue = card_type_value,
        preclassifierId = preclassifier_id,
        preclassifierCode = preclassifier_code,
        preclassifierDescription = preclassifier_description,
        creatorId = creator_id,
        creatorName = creator_name,
        responsibleId = responsible_id,
        responsibleName = responsible_name,
        mechanicId = mechanic_id,
        mechanicName = mechanic_name,
        comments = comments,
        evidenceAudioCreation = evidence_audio_creation,
        evidenceVideoCreation = evidence_video_creation,
        evidenceImageCreation = evidence_image_creation,
        location = card_location,
        definitiveSolutionDate = definitive_solution_date,
        isLocal = is_local != 0L,
        hasLocalSolutions = has_local_solutions != 0L,
        updatedAt = updated_at,
        appSo = app_so,
        appVersion = app_version,
        customDueDate = custom_due_date,
        notifyResponsible = notify_responsible != 0L,
        syncState = runCatching { CardSyncState.valueOf(sync_state) }.getOrDefault(CardSyncState.SYNCED),
        syncError = sync_error,
        syncAttempts = sync_attempts,
    )

    private companion object {
        const val FIRST_PAGE = 1
        const val PAGE_SIZE = 100
        const val MAX_PAGE_GUARD = 1_000
    }
}
