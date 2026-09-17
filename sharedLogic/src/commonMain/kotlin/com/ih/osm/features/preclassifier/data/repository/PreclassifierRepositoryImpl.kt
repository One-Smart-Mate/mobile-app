package com.ih.osm.features.preclassifier.data.repository

import com.ih.osm.core.network.NetworkResult
import com.ih.osm.database.AppDatabase
import com.ih.osm.features.catalog.data.remote.CatalogApiService
import com.ih.osm.features.catalog.data.remote.mapSuccess
import com.ih.osm.features.preclassifier.domain.model.Preclassifier
import com.ih.osm.features.preclassifier.domain.repository.PreclassifierRepository

internal class PreclassifierRepositoryImpl(
    private val api: CatalogApiService,
    private val database: AppDatabase,
) : PreclassifierRepository {
    override suspend fun fetchRemote(siteId: Long): NetworkResult<List<Preclassifier>> =
        api.getPreclassifiers(siteId).mapSuccess { items -> items.map { it.toDomain() } }

    override fun getAll(siteId: Long): List<Preclassifier> =
        database.catalogsQueries.selectPreclassifiersBySite(siteId).executeAsList().map {
            Preclassifier(it.id, it.code, it.description, it.card_type_id)
        }

    override fun hasData(siteId: Long): Boolean =
        database.catalogsQueries.hasPreclassifiersBySite(siteId).executeAsOne()

    override fun replaceAll(siteId: Long, items: List<Preclassifier>) {
        database.catalogsQueries.deletePreclassifiersBySite(siteId)
        items.forEach {
            database.catalogsQueries.insertPreclassifier(siteId, it.id, it.code, it.description, it.cardTypeId)
        }
    }

    override fun deleteAll() {
        database.catalogsQueries.deleteAllPreclassifiers()
    }
}
