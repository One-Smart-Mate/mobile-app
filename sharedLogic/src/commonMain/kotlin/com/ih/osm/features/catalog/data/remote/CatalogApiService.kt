package com.ih.osm.features.catalog.data.remote

import com.ih.osm.core.network.NetworkClient
import com.ih.osm.core.network.NetworkResult
import io.ktor.client.request.parameter

internal class CatalogApiService(
    private val networkClient: NetworkClient,
) {
    suspend fun getCardTypes(siteId: Long): NetworkResult<List<CardTypeDto>> =
        networkClient.get<CatalogApiResponse<List<CardTypeDto>>>("/card-types/all/$siteId").unwrap()

    suspend fun getPreclassifiers(siteId: Long): NetworkResult<List<PreclassifierDto>> =
        networkClient.get<CatalogApiResponse<List<PreclassifierDto>>>("/preclassifier/site/$siteId").unwrap()

    suspend fun getPriorities(siteId: Long): NetworkResult<List<PriorityDto>> =
        networkClient.get<CatalogApiResponse<List<PriorityDto>>>("/priority/all/$siteId").unwrap()

    suspend fun getEmployees(siteId: Long): NetworkResult<List<EmployeeDto>> =
        networkClient.get<CatalogApiResponse<List<EmployeeDto>>>("/users/all/$siteId").unwrap()

    suspend fun getLevels(
        siteId: Long,
        page: Int,
        limit: Int,
    ): NetworkResult<LevelPageDto> =
        networkClient.get<CatalogApiResponse<LevelPageDto>>("/level/all/$siteId") {
            parameter("page", page)
            parameter("limit", limit)
        }.unwrap()

    private fun <T> NetworkResult<CatalogApiResponse<T>>.unwrap(): NetworkResult<T> = when (this) {
        is NetworkResult.Success -> NetworkResult.Success(data.data, statusCode)
        is NetworkResult.Failure -> this
    }
}

internal inline fun <T, R> NetworkResult<T>.mapSuccess(
    transform: (T) -> R,
): NetworkResult<R> = when (this) {
    is NetworkResult.Success -> NetworkResult.Success(transform(data), statusCode)
    is NetworkResult.Failure -> this
}
