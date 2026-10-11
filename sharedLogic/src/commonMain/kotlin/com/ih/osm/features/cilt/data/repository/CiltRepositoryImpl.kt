package com.ih.osm.features.cilt.data.repository

import com.ih.osm.core.network.NetworkError
import com.ih.osm.core.network.NetworkErrorKind
import com.ih.osm.core.network.NetworkResult
import com.ih.osm.features.auth.domain.session.SessionRepository
import com.ih.osm.features.cilt.data.remote.CiltApiService
import com.ih.osm.features.cilt.data.remote.StartSequenceExecutionRequest
import com.ih.osm.features.cilt.data.remote.StopSequenceExecutionRequest
import com.ih.osm.features.cilt.domain.model.CiltExecution
import com.ih.osm.features.cilt.domain.model.CiltRoutine
import com.ih.osm.features.cilt.domain.model.CiltSequence
import com.ih.osm.features.cilt.domain.repository.CiltRepository
import kotlinx.serialization.SerializationException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

internal class CiltRepositoryImpl(
    private val api: CiltApiService,
    private val sessionRepository: SessionRepository,
) : CiltRepository {

    override suspend fun getRoutineOfDay(): NetworkResult<CiltRoutine> = authenticated {
        api.getRoutineOfDay().mapData { it.toDomain() }
    }

    override suspend fun getSequence(sequenceId: Long): NetworkResult<CiltSequence> = authenticated {
        if (sequenceId <= 0) return@authenticated clientFailure("Invalid sequence identifier.", 400)
        api.getSequence(sequenceId).mapData { it.toDomain() }
    }

    override suspend fun startExecution(executionId: Long): NetworkResult<CiltExecution> = authenticated {
        if (executionId <= 0) return@authenticated clientFailure("Invalid execution identifier.", 400)
        api.startExecution(
            StartSequenceExecutionRequest(id = executionId, startDate = nowUtc()),
        ).mapData { it.toDomain() }
    }

    override suspend fun stopExecution(
        executionId: Long,
        initialParameter: String,
        finalParameter: String,
        parameterOk: Boolean,
    ): NetworkResult<CiltExecution> = authenticated {
        if (executionId <= 0) return@authenticated clientFailure("Invalid execution identifier.", 400)
        api.stopExecution(
            StopSequenceExecutionRequest(
                id = executionId,
                stopDate = nowUtc(),
                initialParameter = initialParameter,
                finalParameter = finalParameter,
                // Backend convention: nok = true means the parameter was NOT ok.
                nok = !parameterOk,
            ),
        ).mapData { it.toDomain() }
    }

    override suspend fun uploadEvidence(
        siteId: Long,
        executionId: Long,
        type: String,
        fileName: String,
        contentType: String,
        bytes: ByteArray,
    ): NetworkResult<Unit> = authenticated {
        if (executionId <= 0) return@authenticated clientFailure("Invalid execution identifier.", 400)
        api.uploadExecutionEvidence(siteId, executionId, type, fileName, contentType, bytes)
    }

    override suspend fun getExecution(executionId: Long): NetworkResult<CiltExecution> = authenticated {
        if (executionId <= 0) return@authenticated clientFailure("Invalid execution identifier.", 400)
        api.getExecution(executionId).mapData { it.toDomain() }
    }

    private fun nowUtc(): String = Clock.System.now().toString()

    private suspend fun <T> authenticated(read: suspend () -> NetworkResult<T>): NetworkResult<T> {
        return try {
            sessionRepository.currentUser() ?: return clientFailure("User not authenticated.", 401)
            read()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: SerializationException) {
            NetworkResult.Failure(NetworkError(NetworkErrorKind.SERIALIZATION, error.message ?: "Invalid CILT data."))
        } catch (error: Exception) {
            NetworkResult.Failure(NetworkError(NetworkErrorKind.UNKNOWN, error.message ?: "CILT request failed."))
        }
    }

    private inline fun <T, R> NetworkResult<T>.mapData(mapper: (T) -> R): NetworkResult<R> = when (this) {
        is NetworkResult.Failure -> this
        is NetworkResult.Success -> try {
            NetworkResult.Success(mapper(data), statusCode)
        } catch (error: SerializationException) {
            NetworkResult.Failure(
                NetworkError(NetworkErrorKind.SERIALIZATION, error.message ?: "Invalid CILT data.", statusCode),
            )
        }
    }

    private fun clientFailure(message: String, statusCode: Int) =
        NetworkResult.Failure(NetworkError(NetworkErrorKind.CLIENT, message, statusCode))
}
