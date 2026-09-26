package com.ih.osm.features.card.data.remote

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CardDtosTest {
    private val json = Json {
        explicitNulls = false
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun emptyEvidenceListIsAlwaysIncludedInSyncPayload() {
        val request = SyncCardsRequestDto(
            cards = listOf(
                CreateCardRequestDto(
                    siteId = 1,
                    uuid = "offline-card-uuid",
                    cardCreationDate = "2026-09-26T09:00:00Z",
                    nodeId = 2,
                    priorityId = 3,
                    cardTypeId = 4,
                    preclassifierId = 5,
                    evidences = emptyList(),
                ),
            ),
        )

        val payload = json.encodeToString(request)
        val card = json.parseToJsonElement(payload)
            .jsonObject
            .getValue("cards")
            .jsonArray
            .single()
            .jsonObject

        assertTrue("evidences" in card)
        assertEquals(0, card.getValue("evidences").jsonArray.size)
    }
}
