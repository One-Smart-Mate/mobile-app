package com.ih.osm.features.level.domain.repository

import com.ih.osm.core.network.NetworkResult
import com.ih.osm.features.level.domain.model.Level

interface LevelRepository {
    suspend fun fetchRemote(siteId: Long): NetworkResult<List<Level>>
    fun getAll(siteId: Long): List<Level>
    fun count(siteId: Long): Long
    fun replaceAll(siteId: Long, items: List<Level>)
    fun deleteAll()
}
