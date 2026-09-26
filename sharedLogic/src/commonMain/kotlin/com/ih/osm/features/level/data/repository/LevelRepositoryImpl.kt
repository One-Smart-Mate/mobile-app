package com.ih.osm.features.level.data.repository

import com.ih.osm.core.network.NetworkResult
import com.ih.osm.database.AppDatabase
import com.ih.osm.features.catalog.data.remote.CatalogApiService
import com.ih.osm.features.level.domain.model.Level
import com.ih.osm.features.level.domain.repository.LevelRepository

internal class LevelRepositoryImpl(
    private val api: CatalogApiService,
    private val database: AppDatabase,
) : LevelRepository {
    override suspend fun fetchRemote(siteId: Long): NetworkResult<List<Level>> {
        val levels = mutableListOf<Level>()
        var page = 1
        while (true) {
            when (val result = api.getLevels(siteId, page, PAGE_SIZE)) {
                is NetworkResult.Failure -> return result
                is NetworkResult.Success -> {
                    val response = result.data
                    levels += response.data.map { it.toDomain() }
                    val hasMore = response.hasMore
                        ?: response.totalPages?.let { page < it }
                        ?: (response.data.size == PAGE_SIZE)
                    if (!hasMore) return NetworkResult.Success(levels, result.statusCode)
                    page += 1
                }
            }
        }
    }

    override fun getAll(siteId: Long): List<Level> =
        database.catalogsQueries.selectLevelsBySite(siteId).executeAsList().map {
            Level(
                id = it.id,
                ownerId = it.owner_id,
                ownerName = it.owner_name,
                superiorId = it.superior_id,
                name = it.name,
                description = it.description,
                status = it.status,
                depth = it.depth,
                machineId = it.machine_id,
                notifyResponsible = it.notify_responsible != 0L,
                assignResponsibleOnCreate = it.assign_responsible_on_create != 0L,
            )
        }

    override fun count(siteId: Long): Long =
        database.catalogsQueries.countLevelsBySite(siteId).executeAsOne()

    override fun replaceAll(siteId: Long, items: List<Level>) {
        database.catalogsQueries.deleteLevelsBySite(siteId)
        items.forEach {
            database.catalogsQueries.insertLevel(
                siteId, it.id, it.ownerId, it.ownerName, it.superiorId, it.name, it.description, it.status,
                it.depth, it.machineId, if (it.notifyResponsible) 1 else 0,
                if (it.assignResponsibleOnCreate) 1 else 0,
            )
        }
    }

    override fun deleteAll() {
        database.catalogsQueries.deleteAllLevels()
    }

    private companion object {
        const val PAGE_SIZE = 500
    }
}
