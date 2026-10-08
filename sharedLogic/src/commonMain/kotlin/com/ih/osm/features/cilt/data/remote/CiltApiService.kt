package com.ih.osm.features.cilt.data.remote

import com.ih.osm.core.network.NetworkClient
import com.ih.osm.core.network.NetworkResult

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
