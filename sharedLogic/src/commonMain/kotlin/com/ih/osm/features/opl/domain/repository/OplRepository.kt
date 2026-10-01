package com.ih.osm.features.opl.domain.repository

import com.ih.osm.core.network.NetworkResult
import com.ih.osm.features.opl.domain.model.Opl

interface OplRepository {
    /** Remote reads require a site belonging to the authenticated user's session. */
    suspend fun getBySite(siteId: Long): NetworkResult<List<Opl>>

    /** Searches the OPL title or the assigned LEVEL/machine name. Blank input lists the site. */
    suspend fun search(siteId: Long, query: String): NetworkResult<List<Opl>>

    /** Uses a LEVEL from the site's synchronized catalog; this endpoint returns direct assignments. */
    suspend fun getByLevel(siteId: Long, levelId: String): NetworkResult<List<Opl>>

    /** Loads the complete lesson, including all assigned LEVELS and ordered content. */
    suspend fun getById(siteId: Long, oplId: Long): NetworkResult<Opl>
}
