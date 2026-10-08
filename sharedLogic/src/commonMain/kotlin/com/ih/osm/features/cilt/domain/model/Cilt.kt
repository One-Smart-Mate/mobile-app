package com.ih.osm.features.cilt.domain.model

/**
 * CILT daily routine domain models (ported from m2-android-app).
 * Scope of this port: list the day's routine (positions -> masters -> sequences ->
 * executions) and run one sequence (start -> capture parameters -> complete).
 */
data class CiltRoutine(
    val userId: Long,
    val userName: String,
    val positions: List<CiltPosition>,
)

data class CiltPosition(
    val id: Long,
    val name: String,
    val siteName: String,
    val areaName: String,
    val ciltMasters: List<CiltMaster>,
)

data class CiltMaster(
    val id: Long,
    val siteId: Long,
    val ciltName: String,
    val ciltDescription: String,
    val creatorName: String,
    val reviewerName: String,
    val approvedByName: String,
    val ciltDueDate: String,
    val standardTime: Long,
    val urlImgLayout: String?,
    val status: String,
    val sequences: List<CiltSequence>,
)

data class CiltSequence(
    val id: Long,
    val siteId: Long,
    val ciltMstrId: Long,
    val ciltMstrName: String,
    val ciltTypeId: Long,
    val ciltTypeName: String,
    val secuenceList: String,
    val secuenceColor: String,
    val toolsRequired: String,
    val standardTime: Long,
    val standardOk: String,
    val referencePoint: String?,
    val order: Int,
    val status: String,
    val executions: List<CiltExecution>,
)

/** One scheduled run of a sequence. status "A" = active/editable, "R" = completed/read-only. */
data class CiltExecution(
    val id: Long,
    val siteExecutionId: Long,
    val secuenceStart: String?,
    val secuenceStop: String?,
    val duration: Long,
    val standardOk: String,
    val initialParameter: String?,
    val finalParameter: String?,
    val nok: Boolean,
    val secuenceList: String,
    val ciltTypeName: String,
    val status: String,
) {
    val isCompleted: Boolean get() = status.uppercase() != "A"
}
