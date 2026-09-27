package com.ih.osm.features.card

import com.ih.osm.core.network.NetworkResult
import com.ih.osm.features.card.domain.model.Card
import com.ih.osm.features.card.domain.model.CardEvidence
import com.ih.osm.features.card.domain.repository.CardRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class IosEvidenceResolveOutcome(
    val path: String?,
    val errorMessage: String?,
)

class IosCardDetailController(
    private val repository: CardRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observationJob: Job? = null

    fun observe(uuid: String, onChanged: (Card?) -> Unit) {
        observationJob?.cancel()
        observationJob = scope.launch {
            repository.observeCard(uuid).collectLatest(onChanged)
        }
    }

    suspend fun resolveEvidence(evidence: CardEvidence): IosEvidenceResolveOutcome {
        IosEvidenceFileCache.resolveExisting(evidence)?.let {
            return IosEvidenceResolveOutcome(it, null)
        }
        return when (val result = repository.downloadEvidence(evidence.siteId, evidence.url)) {
            is NetworkResult.Failure -> IosEvidenceResolveOutcome(null, result.error.message)
            is NetworkResult.Success -> runCatching {
                IosEvidenceFileCache.store(evidence, result.data)
            }.fold(
                onSuccess = { IosEvidenceResolveOutcome(it, null) },
                onFailure = { IosEvidenceResolveOutcome(null, it.message ?: "Unable to cache evidence.") },
            )
        }
    }

    fun stop() {
        observationJob?.cancel()
        observationJob = null
    }
}
