package com.ih.osm.features.carddetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ih.osm.features.card.domain.model.Card
import com.ih.osm.features.card.domain.model.CardEvidence
import com.ih.osm.features.card.domain.model.CardEvidenceMediaType
import com.ih.osm.features.card.domain.repository.CardRepository
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val evidenceCache: EvidenceFileCache,
) : ViewModel() {
    private val mutableState = MutableStateFlow(CardDetailUiState())
    val state: StateFlow<CardDetailUiState> = mutableState.asStateFlow()
    private var observedUuid: String? = null
    private var observation: Job? = null

    fun load(uuid: String) {
        if (observedUuid == uuid) return
        observedUuid = uuid
        observation?.cancel()
        mutableState.value = CardDetailUiState()
        observation = viewModelScope.launch {
            repository.observeCard(uuid).collectLatest { card ->
                mutableState.value = mutableState.value.copy(
                    isLoading = false,
                    card = card,
                    evidenceFiles = mutableState.value.evidenceFiles.filterValues { File(it).isFile },
                )
            }
        }
    }

    fun prepareEvidence() {
        mutableState.value.card?.evidences
            ?.filter { it.mediaType == CardEvidenceMediaType.IMAGE }
            ?.forEach(::resolveEvidence)
    }

    fun resolveEvidence(evidence: CardEvidence) {
        val state = mutableState.value
        if (evidence.id in state.evidenceFiles || evidence.id in state.loadingEvidenceIds) return
        mutableState.value = state.copy(
            loadingEvidenceIds = state.loadingEvidenceIds + evidence.id,
            failedEvidenceIds = state.failedEvidenceIds - evidence.id,
        )
        viewModelScope.launch {
            evidenceCache.resolve(evidence)
                .onSuccess { file ->
                    mutableState.value = mutableState.value.copy(
                        evidenceFiles = mutableState.value.evidenceFiles + (evidence.id to file.absolutePath),
                        loadingEvidenceIds = mutableState.value.loadingEvidenceIds - evidence.id,
                        failedEvidenceIds = mutableState.value.failedEvidenceIds - evidence.id,
                    )
                }
                .onFailure {
                    mutableState.value = mutableState.value.copy(
                        loadingEvidenceIds = mutableState.value.loadingEvidenceIds - evidence.id,
                        failedEvidenceIds = mutableState.value.failedEvidenceIds + evidence.id,
                    )
                }
        }
    }
}
