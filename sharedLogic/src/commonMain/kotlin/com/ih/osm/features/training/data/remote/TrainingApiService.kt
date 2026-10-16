package com.ih.osm.features.training.data.remote

import com.ih.osm.core.network.NetworkClient
import com.ih.osm.core.network.NetworkResult

internal class TrainingApiService(
    private val networkClient: NetworkClient,
) {
    suspend fun getMyRecords(): NetworkResult<List<TrainingRecordDto>> =
        networkClient
            .get<TrainingApiResponse<List<TrainingRecordDto>>>("/opl-training/my-records")
            .unwrap()

    suspend fun selfComplete(oplId: Long): NetworkResult<Unit> =
        when (
            val result =
                networkClient.post<SelfCompleteRequest, TrainingApiResponse<Unit?>>(
                    "/opl-training/self-complete",
                    SelfCompleteRequest(oplId),
                )
        ) {
            is NetworkResult.Success -> NetworkResult.Success(Unit, result.statusCode)
            is NetworkResult.Failure -> result
        }

    private fun <T> NetworkResult<TrainingApiResponse<T>>.unwrap(): NetworkResult<T> =
        when (this) {
            is NetworkResult.Success -> NetworkResult.Success(data.data, statusCode)
            is NetworkResult.Failure -> this
        }
}
