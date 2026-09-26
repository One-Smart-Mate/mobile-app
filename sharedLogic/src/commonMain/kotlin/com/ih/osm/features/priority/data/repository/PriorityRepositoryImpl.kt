package com.ih.osm.features.priority.data.repository

import com.ih.osm.core.network.NetworkResult
import com.ih.osm.database.AppDatabase
import com.ih.osm.features.catalog.data.remote.CatalogApiService
import com.ih.osm.features.catalog.data.remote.mapSuccess
import com.ih.osm.features.priority.domain.model.Priority
import com.ih.osm.features.priority.domain.repository.PriorityRepository

internal class PriorityRepositoryImpl(
    private val api: CatalogApiService,
    private val database: AppDatabase,
) : PriorityRepository {
    override suspend fun fetchRemote(siteId: Long): NetworkResult<List<Priority>> =
        api.getPriorities(siteId).mapSuccess { items -> items.map { it.toDomain() } }

    override fun getAll(siteId: Long): List<Priority> =
        database.catalogsQueries.selectPrioritiesBySite(siteId).executeAsList().map {
            Priority(it.id, it.code, it.description, it.days, it.status)
        }

    override fun count(siteId: Long): Long =
        database.catalogsQueries.countPrioritiesBySite(siteId).executeAsOne()

    override fun replaceAll(siteId: Long, items: List<Priority>) {
        database.catalogsQueries.deletePrioritiesBySite(siteId)
        items.forEach {
            database.catalogsQueries.insertPriority(siteId, it.id, it.code, it.description, it.days, it.status)
        }
    }

    override fun deleteAll() {
        database.catalogsQueries.deleteAllPriorities()
    }
}
