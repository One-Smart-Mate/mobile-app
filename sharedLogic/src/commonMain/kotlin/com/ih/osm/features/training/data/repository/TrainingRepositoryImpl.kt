package com.ih.osm.features.training.data.repository

import com.ih.osm.core.network.NetworkError
import com.ih.osm.core.network.NetworkErrorKind
import com.ih.osm.core.network.NetworkResult
import com.ih.osm.features.auth.domain.session.SessionRepository
import com.ih.osm.features.training.data.remote.TrainingApiService
import com.ih.osm.features.training.data.remote.TrainingRecordDto
import com.ih.osm.features.training.domain.model.TrainingRecord
import com.ih.osm.features.training.domain.repository.TrainingRepository
import kotlinx.serialization.SerializationException
import kotlin.coroutines.cancellation.CancellationException

internal class TrainingRepositoryImpl(
    private val api: TrainingApiService,
    private val sessionRepository: SessionRepository,
) : TrainingRepository {
    override suspend fun getMyRecords(): NetworkResult<List<TrainingRecord>> =
        authenticated {
            api.getMyRecords().mapData { list -> list.map(TrainingRecordDto::toDomain) }
        }

    override suspend fun selfComplete(oplId: Long): NetworkResult<Unit> =
        authenticated {
            if (oplId <= 0) {
                NetworkResult.Failure(NetworkError(NetworkErrorKind.CLIENT, "Invalid OPL identifier.", 400))
            } else {
                api.selfComplete(oplId)
            }
        }

    private suspend fun <T> authenticated(read: suspend () -> NetworkResult<T>): NetworkResult<T> =
        try {
            val user = sessionRepository.currentUser()
            if (user == null) {
                NetworkResult.Failure(NetworkError(NetworkErrorKind.CLIENT, "User not authenticated.", 401))
            } else {
                read()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: SerializationException) {
            NetworkResult.Failure(
                NetworkError(NetworkErrorKind.SERIALIZATION, error.message ?: "Invalid training data."),
            )
        } catch (error: Exception) {
            NetworkResult.Failure(
                NetworkError(NetworkErrorKind.UNKNOWN, error.message ?: "Training request failed."),
            )
        }

    private inline fun <T, R> NetworkResult<T>.mapData(mapper: (T) -> R): NetworkResult<R> =
        when (this) {
            is NetworkResult.Failure -> this
            is NetworkResult.Success ->
                try {
                    NetworkResult.Success(mapper(data), statusCode)
                } catch (error: SerializationException) {
                    NetworkResult.Failure(
                        NetworkError(
                            NetworkErrorKind.SERIALIZATION,
                            error.message ?: "Invalid training data.",
                            statusCode,
                        ),
                    )
                }
        }
}
