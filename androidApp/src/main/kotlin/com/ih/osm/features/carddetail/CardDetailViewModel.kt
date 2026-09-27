package com.ih.osm.features.carddetail

import androidx.lifecycle.viewModelScope
import com.ih.osm.core.feature.viewmodel.GRViewModel
import com.ih.osm.features.card.domain.model.Card
import com.ih.osm.features.card.domain.model.CardEvidence
import com.ih.osm.features.card.domain.model.CardEvidenceMediaType
import com.ih.osm.features.card.domain.repository.CardRepository
import com.ih.osm.features.carddetail.domain.cache.EvidenceCache
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class CardDetailUiState(
    val isLoading: Boolean = true,
    val card: Card? = null,
    val evidenceFiles: Map<String, String> = emptyMap(),
    val loadingEvidenceIds: Set<String> = emptySet(),
    val failedEvidenceIds: Set<String> = emptySet(),
)

class CardDetailViewModel(
    private val repository: CardRepository,
    private val evidenceCache: EvidenceCache,
) : GRViewModel<CardDetailUiState, CardDetailViewModel.Action, CardDetailViewModel.Event>(
    CardDetailUiState(),
) {
    sealed interface Action {
        data class Load(val uuid: String) : Action
        data object PrepareEvidence : Action
        data class ResolveEvidence(val evidence: CardEvidence) : Action
    }

    sealed interface Event

    private var observedUuid: String? = null
    private var observation: Job? = null

    override fun processImpl(action: Action) {
        when (action) {
            is Action.Load -> load(action.uuid)
            Action.PrepareEvidence -> prepareEvidence()
            is Action.ResolveEvidence -> resolveEvidence(action.evidence)
        }
    }

    private fun load(uuid: String) {
        if (observedUuid == uuid) return
        observedUuid = uuid
        observation?.cancel()
        setState { CardDetailUiState() }
        observation = viewModelScope.launch {
            repository.observeCard(uuid).collectLatest { card ->
                setState {
                    copy(
                        isLoading = false,
                        card = card,
                        evidenceFiles = evidenceFiles.filterValues { File(it).isFile },
                    )
                }
            }
        }
    }

    private fun prepareEvidence() {
        getStateValue().card?.evidences
            ?.filter { it.mediaType == CardEvidenceMediaType.IMAGE }
            ?.forEach(::resolveEvidence)
    }

    private fun resolveEvidence(evidence: CardEvidence) {
        val state = getStateValue()
        if (evidence.id in state.evidenceFiles || evidence.id in state.loadingEvidenceIds) return
        setState {
            copy(
                loadingEvidenceIds = loadingEvidenceIds + evidence.id,
                failedEvidenceIds = failedEvidenceIds - evidence.id,
            )
        }
        viewModelScope.launch {
            evidenceCache.resolve(evidence)
                .onSuccess { file ->
                    setState {
                        copy(
                            evidenceFiles = evidenceFiles + (evidence.id to file.absolutePath),
                            loadingEvidenceIds = loadingEvidenceIds - evidence.id,
                            failedEvidenceIds = failedEvidenceIds - evidence.id,
                        )
                    }
                }
                .onFailure {
                    setState {
                        copy(
                            loadingEvidenceIds = loadingEvidenceIds - evidence.id,
                            failedEvidenceIds = failedEvidenceIds + evidence.id,
                        )
                    }
                }
        }
    }
}
