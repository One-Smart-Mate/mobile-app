package com.ih.osm.features.preclassifier.domain.repository

import com.ih.osm.core.network.NetworkResult
import com.ih.osm.features.preclassifier.domain.model.Preclassifier

interface PreclassifierRepository {
    suspend fun fetchRemote(siteId: Long): NetworkResult<List<Preclassifier>>
    fun getAll(siteId: Long): List<Preclassifier>
    fun hasData(siteId: Long): Boolean
    fun replaceAll(siteId: Long, items: List<Preclassifier>)
    fun deleteAll()
}
