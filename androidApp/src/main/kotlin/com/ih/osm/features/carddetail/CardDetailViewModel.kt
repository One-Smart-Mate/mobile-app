package com.ih.osm.features.carddetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ih.osm.features.card.domain.model.Card
import com.ih.osm.features.card.domain.repository.CardRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class CardDetailUiState(
    val isLoading: Boolean = true,
    val card: Card? = null,
)

class CardDetailViewModel(
    private val repository: CardRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(CardDetailUiState())
    val state: StateFlow<CardDetailUiState> = mutableState.asStateFlow()
    private var observedUuid: String? = null
    private var observation: Job? = null

    fun load(uuid: String) {
        if (observedUuid == uuid) return
        observedUuid = uuid
        observation?.cancel()
        observation = viewModelScope.launch {
            repository.observeCard(uuid).collectLatest { card ->
                mutableState.value = CardDetailUiState(isLoading = false, card = card)
            }
        }
    }
}
