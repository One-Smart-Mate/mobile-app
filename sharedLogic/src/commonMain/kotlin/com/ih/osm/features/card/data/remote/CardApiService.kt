package com.ih.osm.features.card.data.remote

import com.ih.osm.core.network.NetworkClient
import com.ih.osm.core.network.NetworkResult
import io.ktor.client.request.parameter

internal class CardApiService(
    private val networkClient: NetworkClient,
) {
    suspend fun getCards(
        siteId: Long,
        page: Int,
        limit: Int,
    ): NetworkResult<CardPageDto> =
        when (
            val result = networkClient.get<CardApiResponse>("/card/all/$siteId") {
                parameter("page", page)
                parameter("limit", limit)
            }
        ) {
            is NetworkResult.Success -> NetworkResult.Success(result.data.data, result.statusCode)
            is NetworkResult.Failure -> result
        }

    suspend fun syncCards(cards: List<CreateCardRequestDto>): NetworkResult<CardSyncResponseDto> =
        when (
            val result = networkClient.post<SyncCardsRequestDto, CardSyncApiResponse>(
                endpoint = "/card/sync",
                body = SyncCardsRequestDto(cards),
            )
        ) {
            is NetworkResult.Success -> NetworkResult.Success(result.data.data, result.statusCode)
            is NetworkResult.Failure -> result
        }
}
