package com.ih.osm.features.catalog.domain.repository

import com.ih.osm.features.catalog.domain.model.CatalogSyncMetadata
import com.ih.osm.features.catalog.domain.model.SiteCatalogs

interface CatalogSyncRepository {
    fun replaceAllTransactionally(
        userId: Long,
        siteScope: String,
        completedAtEpochMs: Long,
        catalogs: List<SiteCatalogs>,
        resetAllBeforeSave: Boolean,
    )

    fun getMetadata(userId: Long): CatalogSyncMetadata?

    fun invalidate(userId: Long)
}
