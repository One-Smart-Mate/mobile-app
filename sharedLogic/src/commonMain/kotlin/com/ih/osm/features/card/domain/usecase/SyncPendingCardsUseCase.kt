package com.ih.osm.features.card.domain.usecase

import co.touchlab.kermit.Logger
import com.ih.osm.core.network.NetworkResult
import com.ih.osm.features.card.domain.repository.CardRepository

data class CardSyncProgress(
    val completed: Int,
    val total: Int,
)

sealed interface PendingCardSyncResult {
    data class Success(val synced: Int) : PendingCardSyncResult
    data class Failure(
        val message: String,
        val retryable: Boolean,
        val synced: Int,
        val failed: Int,
    ) : PendingCardSyncResult
}

class SyncPendingCardsUseCase(
    private val repository: CardRepository,
) {
    suspend operator fun invoke(
        onProgress: suspend (CardSyncProgress) -> Unit = {},
    ): PendingCardSyncResult {
        val pending = repository.getPending(MAX_PENDING_SNAPSHOT)
        if (pending.isEmpty()) return PendingCardSyncResult.Success(0)

        var completed = 0
        var synced = 0
        var failed = 0
        var hasRetryableFailure = false
        val failureMessages = linkedSetOf<String>()
        onProgress(CardSyncProgress(completed, pending.size))

        pending.chunked(SERVER_BATCH_SIZE).forEach { batch ->
            batch.forEach { repository.markSyncing(it.uuid) }
            when (val result = repository.sync(batch)) {
                is NetworkResult.Failure -> {
                    batch.forEach { card ->
                        repository.markSyncFailed(card.uuid, result.error.message)
                    }
                    return PendingCardSyncResult.Failure(
                        message = result.error.message,
                        retryable = result.error.statusCode == null || result.error.statusCode >= 500,
                        synced = synced,
                        failed = failed + batch.size,
                    )
                }

                is NetworkResult.Success -> {
                    val outcomes = result.data.associateBy { it.uuid }
                    batch.forEach { localCard ->
                        val outcome = outcomes[localCard.uuid]
                        if (outcome?.success == true && outcome.card != null) {
                            repository.saveSynced(outcome.card)
                            synced += 1
                        } else {
                            val message = outcome?.message
                                ?.takeIf(String::isNotBlank)
                                ?: "The server did not acknowledge the card."
                            repository.markSyncFailed(
                                localCard.uuid,
                                message,
                            )
                            failureMessages += message
                            val statusCode = outcome?.statusCode
                            hasRetryableFailure = hasRetryableFailure ||
                                statusCode == null || statusCode >= 500
                            failed += 1
                        }
                        completed += 1
                        onProgress(CardSyncProgress(completed, pending.size))
                    }
                }
            }
        }

        return if (failed == 0) {
            PendingCardSyncResult.Success(synced)
        } else {
            PendingCardSyncResult.Failure(
                message = failureMessages.joinToString(separator = "\n").take(MAX_ERROR_MESSAGE_LENGTH),
                retryable = hasRetryableFailure,
                synced = synced,
                failed = failed,
            )
        }
    }

    private companion object {
        const val SERVER_BATCH_SIZE = 25
        const val MAX_PENDING_SNAPSHOT = 10_000L
        const val MAX_ERROR_MESSAGE_LENGTH = 500
    }
}
