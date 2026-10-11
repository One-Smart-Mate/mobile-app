package com.ih.osm.features.cilt.domain.repository

import com.ih.osm.core.network.NetworkResult
import com.ih.osm.features.cilt.domain.model.CiltMaster

/**
 * Local, offline-first catalog of CILT procedures (masters + their sequences)
 * for a site. Mirrors the other catalog repositories (CardType, Level, ...) so
 * the sync flow can persist procedures and work without connectivity.
 */
interface CiltCatalogRepository {
    /** Fetch the site's CILTs with their sequences from the backend. */
    suspend fun fetchRemote(siteId: Long): NetworkResult<List<CiltMaster>>

    /** Read the locally stored CILTs (with sequences) for a site. */
    fun getAll(siteId: Long): List<CiltMaster>

    /** Number of locally stored CILT masters for a site. */
    fun count(siteId: Long): Long

    /** Replace all locally stored CILTs (and sequences) for a site. */
    fun replaceAll(siteId: Long, items: List<CiltMaster>)

    /** Remove every locally stored CILT and sequence (all sites). */
    fun deleteAll()
}
