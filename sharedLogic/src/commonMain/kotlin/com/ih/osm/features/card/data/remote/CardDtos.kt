package com.ih.osm.features.card.data.remote

import com.ih.osm.features.card.domain.model.Card
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Serializable
internal data class CreateCardRequestDto(
    val siteId: Long,
    @SerialName("cardUUID") val uuid: String,
    val cardCreationDate: String,
    val nodeId: Long,
    val priorityId: Long,
    val cardTypeValue: String? = null,
    val cardTypeId: Long,
    val preclassifierId: Long,
    val comments: String? = null,
    val evidences: List<CreateCardEvidenceDto>,
    val appSo: String? = null,
    val appVersion: String? = null,
    val customDueDate: String? = null,
    val notifyResponsible: Boolean = false,
)

@Serializable
internal data class CreateCardEvidenceDto(
    val type: String,
    val url: String,
)

@Serializable
internal data class SyncCardsRequestDto(
    val cards: List<CreateCardRequestDto>,
)

@Serializable
internal data class CardSyncApiResponse(
    val data: CardSyncResponseDto,
    val status: Int? = null,
    val message: String? = null,
)

@Serializable
internal data class CardSyncResponseDto(
    val total: Int = 0,
    val succeeded: Int = 0,
    val failed: Int = 0,
    val results: List<CardSyncItemDto> = emptyList(),
)

@Serializable
internal data class CardSyncItemDto(
    @SerialName("cardUUID") val uuid: String,
    val success: Boolean,
    val outcome: String? = null,
    val card: CardDto? = null,
    val statusCode: Int? = null,
    val message: String? = null,
)

@Serializable
internal data class CardApiResponse(
    val data: CardPageDto = CardPageDto(),
    val status: Int? = null,
    val message: String? = null,
)

@Serializable
internal data class CardPageDto(
    val data: List<CardDto> = emptyList(),
    val totalPages: Int? = null,
    val page: Int? = null,
    val hasMore: Boolean = false,
    val limit: Int? = null,
)

@Serializable
internal data class CardDto(
    val id: JsonElement? = null,
    val siteCardId: Long = 0,
    val siteCode: String? = null,
    @SerialName("cardUUID") val uuid: String? = null,
    val cardTypeColor: String? = null,
    val status: String = "A",
    @SerialName("cardCreationDate") val creationDate: String = "",
    @SerialName("cardDueDate") val dueDate: String? = null,
    @SerialName("priorityId") val priorityId: JsonElement? = null,
    val priorityCode: String? = null,
    val priorityDescription: String? = null,
    @SerialName("nodeId") val nodeId: JsonElement? = null,
    val nodeName: String? = null,
    @SerialName("cardTypeId") val cardTypeId: JsonElement? = null,
    val cardTypeName: String? = null,
    val cardTypeValue: String? = null,
    val preclassifierId: JsonElement? = null,
    val preclassifierCode: String? = null,
    val preclassifierDescription: String? = null,
    @SerialName("creatorId") val creatorId: JsonElement? = null,
    val creatorName: String? = null,
    @SerialName("responsableId") val responsibleId: JsonElement? = null,
    val responsableName: String? = null,
    @SerialName("mechanicId") val mechanicId: JsonElement? = null,
    val mechanicName: String? = null,
    val commentsAtCardCreation: String? = null,
    @SerialName("evidenceAucr") val evidenceAudioCreation: Long = 0,
    @SerialName("evidenceVicr") val evidenceVideoCreation: Long = 0,
    @SerialName("evidenceImcr") val evidenceImageCreation: Long = 0,
    val cardLocation: String? = null,
    val cardDefinitiveSolutionDate: String? = null,
    val updatedAt: String? = null,
) {
    fun toDomain(siteId: Long): Card {
        val serverId = id.asString()
        return Card(
            uuid = uuid?.takeIf(String::isNotBlank)
                ?: serverId?.takeIf(String::isNotBlank)
                ?: "$siteId:$siteCardId",
            serverId = serverId,
            siteCardId = siteCardId,
            siteId = siteId,
            siteCode = siteCode,
            cardTypeColor = cardTypeColor,
            status = status,
            creationDate = creationDate,
            dueDate = dueDate,
            priorityId = priorityId.asString(),
            priorityCode = priorityCode,
            priorityDescription = priorityDescription,
            nodeId = nodeId.asString(),
            nodeName = nodeName,
            cardTypeId = cardTypeId.asString(),
            cardTypeName = cardTypeName,
            cardTypeValue = cardTypeValue,
            preclassifierId = preclassifierId.asString(),
            preclassifierCode = preclassifierCode,
            preclassifierDescription = preclassifierDescription,
            creatorId = creatorId.asString(),
            creatorName = creatorName,
            responsibleId = responsibleId.asString(),
            responsibleName = responsableName,
            mechanicId = mechanicId.asString(),
            mechanicName = mechanicName,
            comments = commentsAtCardCreation,
            evidenceAudioCreation = evidenceAudioCreation,
            evidenceVideoCreation = evidenceVideoCreation,
            evidenceImageCreation = evidenceImageCreation,
            location = cardLocation,
            definitiveSolutionDate = cardDefinitiveSolutionDate,
            isLocal = false,
            hasLocalSolutions = false,
            updatedAt = updatedAt,
            syncState = com.ih.osm.features.card.domain.model.CardSyncState.SYNCED,
        )
    }
}

private fun JsonElement?.asString(): String? =
    (this as? JsonPrimitive)?.contentOrNull
