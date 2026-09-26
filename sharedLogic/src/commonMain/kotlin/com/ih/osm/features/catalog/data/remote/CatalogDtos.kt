package com.ih.osm.features.catalog.data.remote

import com.ih.osm.features.cardtype.domain.model.CardType
import com.ih.osm.features.employee.domain.model.Employee
import com.ih.osm.features.level.domain.model.Level
import com.ih.osm.features.preclassifier.domain.model.Preclassifier
import com.ih.osm.features.priority.domain.model.Priority
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class CatalogApiResponse<T>(
    val data: T,
    val status: Int? = null,
    val message: String? = null,
)

@Serializable
internal data class CardTypeDto(
    val id: String,
    val methodology: String,
    val name: String,
    val description: String,
    val color: String,
    @SerialName("responsableName") val owner: String? = null,
    val status: String,
    @SerialName("quantityPicturesCreate") val quantityImagesCreate: Long? = null,
    val quantityAudiosCreate: Long? = null,
    val quantityVideosCreate: Long? = null,
    val audiosDurationCreate: Long? = null,
    val videosDurationCreate: Long? = null,
    @SerialName("quantityPicturesClose") val quantityImagesClose: Long? = null,
    val quantityAudiosClose: Long? = null,
    val quantityVideosClose: Long? = null,
    val audiosDurationClose: Long? = null,
    val videosDurationClose: Long? = null,
    @SerialName("quantityPicturesPs") val quantityImagesPs: Long? = null,
    val quantityAudiosPs: Long? = null,
    val quantityVideosPs: Long? = null,
    val audiosDurationPs: Long? = null,
    val videosDurationPs: Long? = null,
    val cardTypeMethodology: String? = null,
) {
    fun toDomain() = CardType(
        id = id,
        methodology = methodology,
        name = name,
        description = description,
        color = color,
        owner = owner,
        status = status,
        quantityImagesCreate = quantityImagesCreate,
        quantityAudiosCreate = quantityAudiosCreate,
        quantityVideosCreate = quantityVideosCreate,
        audiosDurationCreate = audiosDurationCreate,
        videosDurationCreate = videosDurationCreate,
        quantityImagesClose = quantityImagesClose,
        quantityAudiosClose = quantityAudiosClose,
        quantityVideosClose = quantityVideosClose,
        audiosDurationClose = audiosDurationClose,
        videosDurationClose = videosDurationClose,
        quantityImagesPs = quantityImagesPs,
        quantityAudiosPs = quantityAudiosPs,
        quantityVideosPs = quantityVideosPs,
        audiosDurationPs = audiosDurationPs,
        videosDurationPs = videosDurationPs,
        cardTypeMethodology = cardTypeMethodology,
    )
}

@Serializable
internal data class PreclassifierDto(
    val id: String,
    @SerialName("preclassifierCode") val code: String,
    @SerialName("preclassifierDescription") val description: String,
    val cardTypeId: String,
) {
    fun toDomain() = Preclassifier(id, code, description, cardTypeId)
}

@Serializable
internal data class PriorityDto(
    val id: String,
    @SerialName("priorityCode") val code: String,
    @SerialName("priorityDescription") val description: String,
    @SerialName("priorityDays") val days: Long,
    val status: String,
) {
    fun toDomain() = Priority(id, code, description, days, status)
}

@Serializable
internal data class LevelDto(
    val id: String,
    @SerialName("responsibleId") val ownerId: String? = null,
    @SerialName("responsibleName") val ownerName: String? = null,
    val superiorId: String = "",
    val name: String,
    val description: String = "",
    val status: String,
    val level: Long = 0,
    val levelMachineId: String? = null,
    val notify: Long = 0,
    val assignWhileCreate: Long = 0,
) {
    fun toDomain() = Level(
        id = id,
        ownerId = ownerId,
        ownerName = ownerName,
        superiorId = superiorId,
        name = name,
        description = description,
        status = status,
        depth = level,
        machineId = levelMachineId,
        notifyResponsible = notify != 0L,
        assignResponsibleOnCreate = assignWhileCreate != 0L,
    )
}

@Serializable
internal data class EmployeeDto(
    val id: String,
    val name: String,
    val email: String,
) {
    fun toDomain() = Employee(id, name, email)
}

@Serializable
internal data class LevelPageDto(
    val total: Int? = null,
    val page: Int? = null,
    val limit: Int? = null,
    val totalPages: Int? = null,
    val hasMore: Boolean? = null,
    val data: List<LevelDto> = emptyList(),
)
