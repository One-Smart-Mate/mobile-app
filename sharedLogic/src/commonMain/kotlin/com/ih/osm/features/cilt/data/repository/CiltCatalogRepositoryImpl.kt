package com.ih.osm.features.cilt.data.repository

import com.ih.osm.core.network.NetworkResult
import com.ih.osm.database.AppDatabase
import com.ih.osm.features.catalog.data.remote.CatalogApiService
import com.ih.osm.features.catalog.data.remote.mapSuccess
import com.ih.osm.features.cilt.domain.model.CiltMaster
import com.ih.osm.features.cilt.domain.model.CiltSequence
import com.ih.osm.features.cilt.domain.repository.CiltCatalogRepository

/**
 * Offline catalog store for CILT procedures. Masters live in CiltMasterCatalog
 * and their sequences (flattened across masters) in CiltSequenceCatalog.
 */
internal class CiltCatalogRepositoryImpl(
    private val api: CatalogApiService,
    private val database: AppDatabase,
) : CiltCatalogRepository {
    override suspend fun fetchRemote(siteId: Long): NetworkResult<List<CiltMaster>> =
        api.getCilts(siteId).mapSuccess { items -> items.map { it.toDomain() } }

    override fun getAll(siteId: Long): List<CiltMaster> {
        val sequencesByMaster =
            database.catalogsQueries.selectCiltSequencesBySite(siteId).executeAsList()
                .map { s ->
                    CiltSequence(
                        id = s.id.toLong(),
                        siteId = s.site_id,
                        ciltMstrId = s.cilt_mstr_id.toLong(),
                        ciltMstrName = s.cilt_mstr_name,
                        ciltTypeId = s.cilt_type_id.toLong(),
                        ciltTypeName = s.cilt_type_name,
                        secuenceList = s.secuence_list,
                        secuenceColor = s.secuence_color,
                        toolsRequired = s.tools_required,
                        standardTime = s.standard_time,
                        standardOk = s.standard_ok,
                        referencePoint = s.reference_point,
                        order = s.secuence_order.toInt(),
                        status = s.status,
                        executions = emptyList(),
                    )
                }
                .groupBy { it.ciltMstrId }

        return database.catalogsQueries.selectCiltMastersBySite(siteId).executeAsList().map { m ->
            CiltMaster(
                id = m.id.toLong(),
                siteId = m.site_id,
                ciltName = m.cilt_name,
                ciltDescription = m.cilt_description,
                creatorName = m.creator_name,
                reviewerName = m.reviewer_name,
                approvedByName = m.approved_by_name,
                ciltDueDate = m.cilt_due_date,
                standardTime = m.standard_time,
                urlImgLayout = m.url_img_layout,
                status = m.status,
                sequences = sequencesByMaster[m.id.toLong()].orEmpty(),
            )
        }
    }

    override fun count(siteId: Long): Long =
        database.catalogsQueries.countCiltMastersBySite(siteId).executeAsOne()

    override fun replaceAll(siteId: Long, items: List<CiltMaster>) {
        database.catalogsQueries.deleteCiltMastersBySite(siteId)
        database.catalogsQueries.deleteCiltSequencesBySite(siteId)
        items.forEach { master ->
            database.catalogsQueries.insertCiltMaster(
                siteId,
                master.id.toString(),
                master.ciltName,
                master.ciltDescription,
                master.creatorName,
                master.reviewerName,
                master.approvedByName,
                master.ciltDueDate,
                master.standardTime,
                master.urlImgLayout,
                master.status,
            )
            master.sequences.forEach { seq ->
                database.catalogsQueries.insertCiltSequence(
                    siteId,
                    seq.id.toString(),
                    seq.ciltMstrId.toString(),
                    seq.ciltMstrName,
                    seq.ciltTypeId.toString(),
                    seq.ciltTypeName,
                    seq.secuenceList,
                    seq.secuenceColor,
                    seq.toolsRequired,
                    seq.standardTime,
                    seq.standardOk,
                    seq.referencePoint,
                    seq.order.toLong(),
                    seq.status,
                )
            }
        }
    }

    override fun deleteAll() {
        database.catalogsQueries.deleteAllCiltMasters()
        database.catalogsQueries.deleteAllCiltSequences()
    }
}
