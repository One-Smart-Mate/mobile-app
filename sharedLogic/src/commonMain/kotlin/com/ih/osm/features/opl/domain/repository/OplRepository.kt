package com.ih.osm.features.opl.domain.repository

import com.ih.osm.core.network.NetworkResult
import com.ih.osm.features.opl.domain.model.Opl
import com.ih.osm.features.opl.domain.model.OplDownloadProgress
import kotlinx.coroutines.flow.Flow

interface OplRepository {
    /** Remote reads require a site belonging to the authenticated user's session. */
    suspend fun getBySite(siteId: Long): NetworkResult<List<Opl>>

    /** Searches the OPL title or the assigned LEVEL/machine name. Blank input lists the site. */
    suspend fun search(siteId: Long, query: String): NetworkResult<List<Opl>>

    /** Uses a LEVEL from the site's synchronized catalog; this endpoint returns direct assignments. */
    suspend fun getByLevel(siteId: Long, levelId: String): NetworkResult<List<Opl>>

    /** Complete local lesson first (no HTTP call); otherwise the authorized remote lesson. */
    suspend fun getById(siteId: Long, oplId: Long): NetworkResult<Opl>

    /** Only complete downloads are visible. Reads never reach the network. */
    suspend fun getDownloaded(siteId: Long): NetworkResult<List<Opl>>
    fun observeDownloadedIds(siteId: Long): Flow<Set<Long>>

    /** Explicit refresh from the server, followed by atomic replacement of the local lesson/media. */
    suspend fun download(
        siteId: Long,
        oplId: Long,
        onProgress: (OplDownloadProgress) -> Unit = {},
    ): NetworkResult<Opl>

    suspend fun deleteDownloaded(siteId: Long, oplId: Long): NetworkResult<Unit>

    /** Streams SQLite chunks when downloaded; otherwise reads the authorized lesson's remote media. */
    suspend fun readMedia(
        siteId: Long,
        oplId: Long,
        contentId: Long,
        onChunk: (ByteArray) -> Unit,
    ): NetworkResult<Unit>
}
