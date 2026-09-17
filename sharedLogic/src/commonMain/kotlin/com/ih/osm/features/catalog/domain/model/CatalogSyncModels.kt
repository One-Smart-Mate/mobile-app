package com.ih.osm.features.catalog.domain.model

import com.ih.osm.features.cardtype.domain.model.CardType
import com.ih.osm.features.employee.domain.model.Employee
import com.ih.osm.features.level.domain.model.Level
import com.ih.osm.features.preclassifier.domain.model.Preclassifier
import com.ih.osm.features.priority.domain.model.Priority

data class SiteCatalogs(
    val siteId: Long,
    val cardTypes: List<CardType>? = null,
    val preclassifiers: List<Preclassifier>? = null,
    val priorities: List<Priority>? = null,
    val levels: List<Level>? = null,
    val employees: List<Employee>? = null,
)

enum class CatalogKind {
    CARD_TYPES,
    PRECLASSIFIERS,
    PRIORITIES,
    LEVELS,
    EMPLOYEES,
}

data class SiteCatalogSyncRequest(
    val siteId: Long,
    val catalogs: Set<CatalogKind>,
)

data class CatalogSyncPlan(
    val userId: Long,
    val siteScope: List<Long>,
    val sites: List<SiteCatalogSyncRequest>,
    val resetAllBeforeSave: Boolean,
) {
    val isEmpty: Boolean get() = sites.all { it.catalogs.isEmpty() }
    val totalSteps: Int get() = sites.sumOf { it.catalogs.size }
}

data class CatalogSyncProgress(
    val completedSteps: Int,
    val totalSteps: Int,
    val catalog: CatalogKind,
    val siteId: Long,
) {
    val fraction: Float
        get() = if (totalSteps == 0) 0f else completedSteps.toFloat() / totalSteps
}

data class CatalogSyncMetadata(
    val userId: Long,
    val siteScope: String,
    val completedAtEpochMs: Long,
)
