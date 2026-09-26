package com.ih.osm.features.priority.domain.repository

import com.ih.osm.core.network.NetworkResult
import com.ih.osm.features.priority.domain.model.Priority

interface PriorityRepository {
    suspend fun fetchRemote(siteId: Long): NetworkResult<List<Priority>>
    fun getAll(siteId: Long): List<Priority>
    fun count(siteId: Long): Long
    fun replaceAll(siteId: Long, items: List<Priority>)
    fun deleteAll()
}
