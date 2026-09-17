package com.ih.osm.features.catalog.data.repository

import com.ih.osm.database.AppDatabase
import com.ih.osm.features.cardtype.domain.repository.CardTypeRepository
import com.ih.osm.features.catalog.domain.model.CatalogSyncMetadata
import com.ih.osm.features.catalog.domain.model.SiteCatalogs
import com.ih.osm.features.catalog.domain.repository.CatalogSyncRepository
import com.ih.osm.features.employee.domain.repository.EmployeeRepository
import com.ih.osm.features.level.domain.repository.LevelRepository
import com.ih.osm.features.preclassifier.domain.repository.PreclassifierRepository
import com.ih.osm.features.priority.domain.repository.PriorityRepository

internal class CatalogSyncRepositoryImpl(
    private val database: AppDatabase,
    private val cardTypes: CardTypeRepository,
    private val preclassifiers: PreclassifierRepository,
    private val priorities: PriorityRepository,
    private val levels: LevelRepository,
    private val employees: EmployeeRepository,
) : CatalogSyncRepository {
    override fun replaceAllTransactionally(
        userId: Long,
        siteScope: String,
        completedAtEpochMs: Long,
        catalogs: List<SiteCatalogs>,
        resetAllBeforeSave: Boolean,
    ) {
        database.transaction {
            if (resetAllBeforeSave) {
                cardTypes.deleteAll()
                preclassifiers.deleteAll()
                priorities.deleteAll()
                levels.deleteAll()
                employees.deleteAll()
                database.catalogsQueries.deleteAllCatalogSyncMetadata()
            }
            catalogs.forEach { site ->
                site.cardTypes?.let { cardTypes.replaceAll(site.siteId, it) }
                site.preclassifiers?.let { preclassifiers.replaceAll(site.siteId, it) }
                site.priorities?.let { priorities.replaceAll(site.siteId, it) }
                site.levels?.let { levels.replaceAll(site.siteId, it) }
                site.employees?.let { employees.replaceAll(site.siteId, it) }
            }
            database.catalogsQueries.upsertCatalogSyncMetadata(userId, siteScope, completedAtEpochMs)
        }
    }

    override fun getMetadata(userId: Long): CatalogSyncMetadata? =
        database.catalogsQueries.selectCatalogSyncMetadata(userId).executeAsOneOrNull()?.let {
            CatalogSyncMetadata(it.user_id, it.site_scope, it.completed_at_epoch_ms)
        }

    override fun invalidate(userId: Long) {
        database.catalogsQueries.deleteCatalogSyncMetadata(userId)
    }
}
