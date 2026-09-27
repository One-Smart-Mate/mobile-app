package com.ih.osm.features.card.data.remote

import com.ih.osm.core.network.NetworkClient
import com.ih.osm.core.network.NetworkResult
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.parameter
import io.ktor.client.request.headers
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders

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

    suspend fun updateProvisionalSolution(
        request: UpdateProvisionalSolutionRequestDto,
    ): NetworkResult<CardDto> = when (
        val result = networkClient.put<UpdateProvisionalSolutionRequestDto, CardMutationApiResponse>(
            endpoint = "/card/update/provisional-solution",
            body = request,
        )
    ) {
        is NetworkResult.Success -> NetworkResult.Success(result.data.data, result.statusCode)
        is NetworkResult.Failure -> result
    }

    suspend fun updateDefinitiveSolution(
        request: UpdateDefinitiveSolutionRequestDto,
    ): NetworkResult<CardDto> = when (
        val result = networkClient.put<UpdateDefinitiveSolutionRequestDto, CardMutationApiResponse>(
            endpoint = "/card/update/definitive-solution",
            body = request,
        )
    ) {
        is NetworkResult.Success -> NetworkResult.Success(result.data.data, result.statusCode)
        is NetworkResult.Failure -> result
    }

    suspend fun uploadEvidence(
        siteId: Long,
        cardUuid: String,
        evidenceId: String,
        evidenceType: String,
        fileName: String,
        contentType: String,
        bytes: ByteArray,
    ): NetworkResult<String> = when (
        val result = networkClient.postContent<CardEvidenceUploadApiResponse>(
            endpoint = "/card/evidence/$siteId/upload",
            body = MultiPartFormDataContent(
                formData {
                    append("cardUUID", cardUuid)
                    append("evidenceId", evidenceId)
                    append("evidenceType", evidenceType)
                    append(
                        key = "file",
                        value = bytes,
                        headers = Headers.build {
                            append(HttpHeaders.ContentType, contentType)
                            append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                        },
                    )
                },
            ),
        ) {
            headers.remove(HttpHeaders.ContentType)
        }
    ) {
        is NetworkResult.Success -> NetworkResult.Success(result.data.data.url, result.statusCode)
        is NetworkResult.Failure -> result
    }

    suspend fun downloadEvidence(
        siteId: Long,
        reference: String,
    ): NetworkResult<ByteArray> = if (reference.startsWith("/card/evidence/")) {
        networkClient.get(reference)
    } else {
        networkClient.get("/card/evidence/$siteId/legacy") {
            parameter("reference", reference)
        }
    }
}
