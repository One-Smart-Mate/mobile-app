package com.ih.osm.features.opl.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/** A read-only lesson published by the web application. */
@Serializable
data class Opl(
    val id: Long,
    val siteId: Long,
    val title: String,
    val objective: String?,
    val creatorId: Long?,
    val creatorName: String?,
    val reviewerId: Long?,
    val reviewerName: String?,
    val typeId: Long?,
    val typeName: String?,
    val order: Int,
    val ciltUsageCount: Long?,
    val directUsageCount: Long?,
    val lastUsedAt: String?,
    val createdAt: String?,
    val updatedAt: String?,
    val levelRelationId: Long?,
    val content: List<OplContent>,
    val levels: List<OplLevel>,
    @Transient val isDownloaded: Boolean = false,
    @Transient val downloadRevision: String? = null,
)

/** Content stays in the order defined on the web, including mixed media. */
@Serializable
data class OplContent(
    val id: Long,
    val oplId: Long,
    val siteId: Long,
    val order: Int,
    val type: OplContentType,
    val typeCode: String,
    val text: String?,
    val mediaUrl: String?,
    val createdAt: String?,
    val updatedAt: String?,
)

@Serializable
enum class OplContentType {
    TEXT,
    IMAGE,
    VIDEO,
    PDF,
    UNKNOWN,
}

/** IDs match the String IDs of the synchronized LEVEL catalog. */
@Serializable
data class OplLevel(
    val id: String,
    val relationId: Long?,
    val name: String,
    val description: String?,
    val machineId: String?,
    val depth: Long,
    val superiorId: String,
)
