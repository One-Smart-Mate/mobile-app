package com.ih.osm.features.opl.data.remote

import com.ih.osm.features.opl.domain.model.Opl
import com.ih.osm.features.opl.domain.model.OplContent
import com.ih.osm.features.opl.domain.model.OplContentType
import com.ih.osm.features.opl.domain.model.OplLevel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Serializable
internal data class OplApiResponse<T>(
    val data: T,
    val status: Int? = null,
    val message: String? = null,
)

@Serializable
internal data class OplDto(
    val id: JsonElement,
    val siteId: JsonElement? = null,
    val title: String,
    @SerialName("objetive") val objective: String? = null,
    val creatorId: JsonElement? = null,
    val creatorName: String? = null,
    val reviewerId: JsonElement? = null,
    val reviewerName: String? = null,
    @SerialName("oplTypeId") val typeId: JsonElement? = null,
    @SerialName("oplType") val typeName: String? = null,
    val order: Int? = null,
    val ciltUsageCount: Long? = null,
    val directUsageCount: Long? = null,
    val lastUsedAt: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    @SerialName("oplLevelId") val levelRelationId: JsonElement? = null,
    val details: List<OplContentDto> = emptyList(),
    val levels: List<OplLevelDto> = emptyList(),
) {
    fun toDomain(): Opl {
        val oplId = id.requiredId("id")
        val oplSiteId = siteId.requiredId("siteId")
        return Opl(
            id = oplId,
            siteId = oplSiteId,
            title = title,
            objective = objective,
            creatorId = creatorId.optionalId("creatorId"),
            creatorName = creatorName,
            reviewerId = reviewerId.optionalId("reviewerId"),
            reviewerName = reviewerName,
            typeId = typeId.optionalId("oplTypeId"),
            typeName = typeName,
            order = order ?: 0,
            ciltUsageCount = ciltUsageCount,
            directUsageCount = directUsageCount,
            lastUsedAt = lastUsedAt,
            createdAt = createdAt,
            updatedAt = updatedAt,
            levelRelationId = levelRelationId.optionalId("oplLevelId"),
            content = details.map { it.toDomain(oplId, oplSiteId) }
                .sortedWith(compareBy<OplContent> { it.order }.thenBy { it.id }),
            levels = levels.map { it.toDomain() },
        )
    }
}

@Serializable
internal data class OplContentDto(
    val id: JsonElement,
    val oplId: JsonElement,
    val siteId: JsonElement? = null,
    val order: Int = 1,
    val type: String,
    val text: String? = null,
    val mediaUrl: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
) {
    fun toDomain(parentOplId: Long, parentSiteId: Long): OplContent {
        val contentOplId = oplId.requiredId("details.oplId")
        // Legacy content with no site inherits its authorized parent OPL's site.
        val contentSiteId = siteId.optionalId("details.siteId") ?: parentSiteId
        if (contentOplId != parentOplId || contentSiteId != parentSiteId) {
            throw SerializationException("OPL content does not belong to its parent lesson/site.")
        }
        return OplContent(
            id = id.requiredId("details.id"),
            oplId = contentOplId,
            siteId = contentSiteId,
            order = order,
            type = when (type.trim().lowercase()) {
                "texto" -> OplContentType.TEXT
                "imagen" -> OplContentType.IMAGE
                "video" -> OplContentType.VIDEO
                "pdf" -> OplContentType.PDF
                else -> OplContentType.UNKNOWN
            },
            typeCode = type,
            text = text,
            mediaUrl = mediaUrl,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }
}

@Serializable
internal data class OplLevelDto(
    val id: JsonElement,
    @SerialName("oplLevelId") val relationId: JsonElement? = null,
    val name: String,
    val description: String? = null,
    @SerialName("levelMachineId") val machineId: String? = null,
    @SerialName("level") val depth: Long = 0,
    val superiorId: JsonElement? = null,
) {
    fun toDomain() = OplLevel(
        id = id.requiredId("levels.id").toString(),
        relationId = relationId.optionalId("levels.oplLevelId"),
        name = name,
        description = description,
        machineId = machineId,
        depth = depth,
        superiorId = (superiorId as? JsonPrimitive)?.contentOrNull ?: "0",
    )
}

/** TypeORM can return bigint IDs as JSON strings or numbers. */
private fun JsonElement?.requiredId(field: String): Long = optionalId(field)
    ?: throw SerializationException("Missing OPL identifier: $field")

private fun JsonElement?.optionalId(field: String): Long? {
    if (this == null || this == kotlinx.serialization.json.JsonNull) return null
    return (this as? JsonPrimitive)?.contentOrNull?.toLongOrNull()?.takeIf { it > 0 }
        ?: throw SerializationException("Invalid OPL identifier: $field")
}
