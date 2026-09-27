package com.ih.osm.features.catalog.domain.model

sealed interface CatalogSyncStatus {
    data object Idle : CatalogSyncStatus
    data object WaitingForNetwork : CatalogSyncStatus
    data class Downloading(
        val progress: Float,
        val catalog: CatalogKind?,
    ) : CatalogSyncStatus
    data class Failed(val message: String?) : CatalogSyncStatus
}
