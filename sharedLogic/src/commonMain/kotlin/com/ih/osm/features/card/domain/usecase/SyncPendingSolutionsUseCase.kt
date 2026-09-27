package com.ih.osm.features.card.domain.usecase

import com.ih.osm.core.network.NetworkResult
import com.ih.osm.features.card.domain.repository.CardRepository
import com.ih.osm.features.card.domain.solution.CardSolutionType

class SyncPendingSolutionsUseCase(
    private val repository: CardRepository,
) {
    suspend operator fun invoke(
        onProgress: suspend (CardSyncProgress) -> Unit = {},
    ): PendingCardSyncResult {
        val cards = repository.getPendingSolutions(MAX_PENDING_SNAPSHOT)
        val total = cards.sumOf { card ->
            (if (card.provisionalSolutionPending) 1 else 0) +
                (if (card.definitiveSolutionPending) 1 else 0)
        }
        if (total == 0) return PendingCardSyncResult.Success(0)

        var completed = 0
        var synced = 0
        onProgress(CardSyncProgress(0, total))
        for (snapshot in cards) {
            var current = repository.getCard(snapshot.uuid) ?: continue
            val types = buildList {
                if (current.provisionalSolutionPending) add(CardSolutionType.PROVISIONAL)
                if (current.definitiveSolutionPending) add(CardSolutionType.DEFINITIVE)
            }
            for (type in types) {
                current = repository.getCard(snapshot.uuid) ?: current
                when (val result = repository.syncSolution(current, type)) {
                    is NetworkResult.Failure -> {
                        repository.markSyncFailed(current.uuid, result.error.message)
                        return PendingCardSyncResult.Failure(
                            message = result.error.message,
                            retryable = result.error.statusCode == null || result.error.statusCode >= 500,
                            synced = synced,
                            failed = total - completed,
                        )
                    }
                    is NetworkResult.Success -> {
                        repository.saveSolutionSynced(current, result.data, type)
                        synced += 1
                        completed += 1
                        onProgress(CardSyncProgress(completed, total))
                    }
                }
            }
        }
        return PendingCardSyncResult.Success(synced)
    }

    private companion object {
        const val MAX_PENDING_SNAPSHOT = 10_000L
    }
}
