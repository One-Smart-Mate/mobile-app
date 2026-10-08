package com.ih.osm.features.cilt.data.remote

import com.ih.osm.features.cilt.domain.model.CiltExecution
import com.ih.osm.features.cilt.domain.model.CiltMaster
import com.ih.osm.features.cilt.domain.model.CiltPosition
import com.ih.osm.features.cilt.domain.model.CiltRoutine
import com.ih.osm.features.cilt.domain.model.CiltSequence
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

@Serializable
internal data class CiltApiResponse<T>(
    val data: T,
    val status: Int? = null,
    val message: String? = null,
)

// ---- GET /cilt-sequences-executions/of-day ----

@Serializable
internal data class CiltRoutineDto(
    val userInfo: CiltUserInfoDto? = null,
    val positions: List<CiltPositionDto> = emptyList(),
) {
    fun toDomain() = CiltRoutine(
        userId = userInfo?.id.optionalId("userInfo.id") ?: 0L,
        userName = userInfo?.name.orEmpty(),
        positions = positions.map { it.toDomain() },
    )
}

@Serializable
internal data class CiltUserInfoDto(
    val id: JsonElement? = null,
    val name: String? = null,
    val email: String? = null,
)

@Serializable
internal data class CiltPositionDto(
    val id: JsonElement,
    val name: String? = null,
    val siteName: String? = null,
    val areaName: String? = null,
    val ciltMasters: List<CiltMasterDto> = emptyList(),
) {
    fun toDomain() = CiltPosition(
        id = id.requiredId("position.id"),
        name = name.orEmpty(),
        siteName = siteName.orEmpty(),
        areaName = areaName.orEmpty(),
        ciltMasters = ciltMasters.map { it.toDomain() },
    )
}

@Serializable
internal data class CiltMasterDto(
    val id: JsonElement,
    val siteId: JsonElement? = null,
    val ciltName: String? = null,
    val ciltDescription: String? = null,
    val creatorName: String? = null,
    val reviewerName: String? = null,
    val approvedByName: String? = null,
    val ciltDueDate: String? = null,
    val standardTime: JsonElement? = null,
    val urlImgLayout: String? = null,
    val status: String? = null,
    val sequences: List<CiltSequenceDto> = emptyList(),
) {
    fun toDomain() = CiltMaster(
        id = id.requiredId("ciltMaster.id"),
        siteId = siteId.optionalId("ciltMaster.siteId") ?: 0L,
        ciltName = ciltName.orEmpty(),
        ciltDescription = ciltDescription.orEmpty(),
        creatorName = creatorName.orEmpty(),
        reviewerName = reviewerName.orEmpty(),
        approvedByName = approvedByName.orEmpty(),
        ciltDueDate = ciltDueDate.orEmpty(),
        standardTime = standardTime.optionalId("standardTime") ?: 0L,
        urlImgLayout = urlImgLayout,
        status = status.orEmpty(),
        sequences = sequences.map { it.toDomain() },
    )
}

@Serializable
internal data class CiltSequenceDto(
    val id: JsonElement,
    val siteId: JsonElement? = null,
    val ciltMstrId: JsonElement? = null,
    val ciltMstrName: String? = null,
    val ciltTypeId: JsonElement? = null,
    val ciltTypeName: String? = null,
    val secuenceList: String? = null,
    val secuenceColor: String? = null,
    val toolsRequired: String? = null,
    val standardTime: JsonElement? = null,
    val standardOk: String? = null,
    val referencePoint: String? = null,
    val order: Int? = null,
    val status: String? = null,
    val executions: List<CiltExecutionDto> = emptyList(),
) {
    fun toDomain() = CiltSequence(
        id = id.requiredId("sequence.id"),
        siteId = siteId.optionalId("sequence.siteId") ?: 0L,
        ciltMstrId = ciltMstrId.optionalId("sequence.ciltMstrId") ?: 0L,
        ciltMstrName = ciltMstrName.orEmpty(),
        ciltTypeId = ciltTypeId.optionalId("sequence.ciltTypeId") ?: 0L,
        ciltTypeName = ciltTypeName.orEmpty(),
        secuenceList = secuenceList.orEmpty(),
        secuenceColor = secuenceColor.orEmpty(),
        toolsRequired = toolsRequired.orEmpty(),
        standardTime = standardTime.optionalId("sequence.standardTime") ?: 0L,
        standardOk = standardOk.orEmpty(),
        referencePoint = referencePoint,
        order = order ?: 0,
        status = status.orEmpty(),
        executions = executions.map { it.toDomain() },
    )
}

@Serializable
internal data class CiltExecutionDto(
    val id: JsonElement,
    val siteExecutionId: JsonElement? = null,
    val secuenceStart: String? = null,
    val secuenceStop: String? = null,
    val duration: JsonElement? = null,
    val standardOk: String? = null,
    val initialParameter: String? = null,
    val finalParameter: String? = null,
    val nok: JsonElement? = null,
    val secuenceList: String? = null,
    val ciltTypeName: String? = null,
    val status: String? = null,
) {
    fun toDomain() = CiltExecution(
        id = id.requiredId("execution.id"),
        siteExecutionId = siteExecutionId.optionalId("execution.siteExecutionId") ?: 0L,
        secuenceStart = secuenceStart,
        secuenceStop = secuenceStop,
        duration = duration.optionalId("execution.duration") ?: 0L,
        standardOk = standardOk.orEmpty(),
        initialParameter = initialParameter,
        finalParameter = finalParameter,
        nok = (nok as? JsonPrimitive)?.booleanOrNull ?: false,
        secuenceList = secuenceList.orEmpty(),
        ciltTypeName = ciltTypeName.orEmpty(),
        status = status.orEmpty(),
    )
}

// ---- PUT start / stop ----

@Serializable
internal data class StartSequenceExecutionRequest(
    val id: Long,
    val startDate: String,
)

@Serializable
internal data class StopSequenceExecutionRequest(
    val id: Long,
    val stopDate: String,
    val initialParameter: String,
    val evidenceAtCreation: Boolean = false,
    val finalParameter: String,
    val evidenceAtFinal: Boolean = false,
    val nok: Boolean,
    val amTagId: Int = 0,
)

/** TypeORM can return bigint IDs as JSON strings or numbers. */
private fun JsonElement?.requiredId(field: String): Long = optionalId(field)
    ?: throw SerializationException("Missing CILT identifier: $field")

private fun JsonElement?.optionalId(field: String): Long? {
    if (this == null || this == kotlinx.serialization.json.JsonNull) return null
    val primitive = this as? JsonPrimitive ?: return null
    return primitive.contentOrNull?.toLongOrNull()
}
