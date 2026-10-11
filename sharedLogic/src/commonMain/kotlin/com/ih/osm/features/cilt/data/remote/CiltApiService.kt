package com.ih.osm.features.cilt.data.remote

import com.ih.osm.core.network.NetworkClient
import com.ih.osm.core.network.NetworkResult
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders

internal class CiltApiService(
    private val networkClient: NetworkClient,
) {
    suspend fun getRoutineOfDay(): NetworkResult<CiltRoutineDto> =
        networkClient.get<CiltApiResponse<CiltRoutineDto>>(
            "/cilt-sequences-executions/of-day",
        ).unwrap()

    suspend fun getSequence(sequenceId: Long): NetworkResult<CiltSequenceDto> =
        networkClient.get<CiltApiResponse<CiltSequenceDto>>(
            "/cilt-sequences/$sequenceId",
        ).unwrap()

    suspend fun getExecution(executionId: Long): NetworkResult<CiltExecutionDto> =
        networkClient.get<CiltApiResponse<CiltExecutionDto>>(
            "/cilt-sequences-executions/$executionId",
        ).unwrap()

    suspend fun uploadExecutionEvidence(
        siteId: Long,
        executionId: Long,
        type: String,
        fileName: String,
        contentType: String,
        bytes: ByteArray,
    ): NetworkResult<Unit> = when (
        val result = networkClient.postContent<CiltApiResponse<kotlinx.serialization.json.JsonElement>>(
            endpoint = "/cilt-sequences-executions/evidence/$siteId/upload",
            body = MultiPartFormDataContent(
                formData {
                    append("executionId", executionId.toString())
                    append("type", type)
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
        is NetworkResult.Success -> NetworkResult.Success(Unit, result.statusCode)
        is NetworkResult.Failure -> result
    }

    suspend fun startExecution(
        request: StartSequenceExecutionRequest,
    ): NetworkResult<CiltExecutionDto> =
        networkClient.put<StartSequenceExecutionRequest, CiltApiResponse<CiltExecutionDto>>(
            endpoint = "/cilt-sequences-executions/start",
            body = request,
        ).unwrap()

    suspend fun stopExecution(
        request: StopSequenceExecutionRequest,
    ): NetworkResult<CiltExecutionDto> =
        networkClient.put<StopSequenceExecutionRequest, CiltApiResponse<CiltExecutionDto>>(
            endpoint = "/cilt-sequences-executions/stop",
            body = request,
        ).unwrap()

    private fun <T> NetworkResult<CiltApiResponse<T>>.unwrap(): NetworkResult<T> = when (this) {
        is NetworkResult.Success -> NetworkResult.Success(data.data, statusCode)
        is NetworkResult.Failure -> this
    }
}
