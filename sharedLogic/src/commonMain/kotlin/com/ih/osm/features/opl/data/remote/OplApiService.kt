package com.ih.osm.features.opl.data.remote

import com.ih.osm.core.network.NetworkClient
import com.ih.osm.core.network.NetworkResult
import io.ktor.client.request.parameter

internal class OplApiService(
    private val networkClient: NetworkClient,
) {
    suspend fun getBySite(siteId: Long): NetworkResult<List<OplDto>> =
        networkClient.get<OplApiResponse<List<OplDto>>>("/opl-mstr/site/$siteId").unwrap()

    suspend fun search(siteId: Long, query: String): NetworkResult<List<OplDto>> =
        networkClient.get<OplApiResponse<List<OplDto>>>("/opl-mstr/site/$siteId/search") {
            parameter("query", query)
        }.unwrap()

    suspend fun getByLevel(levelId: Long): NetworkResult<List<OplDto>> =
        networkClient.get<OplApiResponse<List<OplDto>>>("/opl-levels/level/$levelId").unwrap()

    suspend fun getById(oplId: Long): NetworkResult<OplDto> =
        networkClient.get<OplApiResponse<OplDto>>("/opl-mstr/$oplId").unwrap()

    private fun <T> NetworkResult<OplApiResponse<T>>.unwrap(): NetworkResult<T> = when (this) {
        is NetworkResult.Success -> NetworkResult.Success(data.data, statusCode)
        is NetworkResult.Failure -> this
    }
}
