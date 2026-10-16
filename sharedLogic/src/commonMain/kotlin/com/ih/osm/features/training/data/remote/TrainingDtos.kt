package com.ih.osm.features.training.data.remote

import com.ih.osm.features.training.domain.model.TrainingRecord
import com.ih.osm.features.training.domain.model.TrainingStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Serializable
internal data class TrainingApiResponse<T>(
    val data: T,
    val status: Int? = null,
    val message: String? = null,
)

@Serializable
internal data class TrainingOplDto(
    val id: JsonElement? = null,
    val title: String? = null,
    @kotlinx.serialization.SerialName("objetive") val objective: String? = null,
)

@Serializable
internal data class TrainingRecordDto(
    val id: JsonElement,
    val oplId: JsonElement,
    val status: String? = null,
    val selfCompletedAt: String? = null,
    val validatedAt: String? = null,
    val expiresAt: String? = null,
    val opl: TrainingOplDto? = null,
) {
    fun toDomain(): TrainingRecord =
        TrainingRecord(
            id = id.requiredId("id"),
            oplId = oplId.requiredId("oplId"),
            status = when (status?.trim()?.uppercase()) {
                "PENDING" -> TrainingStatus.PENDING
                "SELF_COMPLETED" -> TrainingStatus.SELF_COMPLETED
                "VALIDATED" -> TrainingStatus.VALIDATED
                "EXPIRED" -> TrainingStatus.EXPIRED
                else -> TrainingStatus.UNKNOWN
            },
            selfCompletedAt = selfCompletedAt,
            validatedAt = validatedAt,
            expiresAt = expiresAt,
            oplTitle = opl?.title ?: "OPL #${oplId.requiredId("oplId")}",
            oplObjective = opl?.objective,
        )
}

@Serializable
internal data class SelfCompleteRequest(
    val oplId: Long,
)

/** TypeORM can return bigint IDs as JSON strings or numbers. */
private fun JsonElement?.requiredId(field: String): Long {
    if (this == null || this == JsonNull) throw SerializationException("Missing training identifier: $field")
    return (this as? JsonPrimitive)?.contentOrNull?.toLongOrNull()?.takeIf { it > 0 }
        ?: throw SerializationException("Invalid training identifier: $field")
}
