package com.ih.osm.features.cardtype.domain.repository

import com.ih.osm.core.network.NetworkResult
import com.ih.osm.features.cardtype.domain.model.CardType

interface CardTypeRepository {
    suspend fun fetchRemote(siteId: Long): NetworkResult<List<CardType>>
    fun getAll(siteId: Long): List<CardType>
    fun count(siteId: Long): Long
    fun replaceAll(siteId: Long, items: List<CardType>)
    fun deleteAll()
}
