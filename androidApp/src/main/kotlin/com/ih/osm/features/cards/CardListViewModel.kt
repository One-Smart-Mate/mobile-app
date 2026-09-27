package com.ih.osm.features.cards

import androidx.lifecycle.viewModelScope
import com.ih.osm.core.feature.viewmodel.GRViewModel
import com.ih.osm.core.network.NetworkResult
import com.ih.osm.features.card.domain.model.Card
import com.ih.osm.features.card.domain.repository.CardRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate

class CardListViewModel(
    private val repository: CardRepository,
) : GRViewModel<CardListViewModel.UiState, CardListViewModel.Action, CardListViewModel.Event>(
    initialState = UiState(),
) {
    data class UiState(
        val cards: List<Card> = emptyList(),
        val totalCount: Int = 0,
        val query: String = "",
        val filter: Filter = Filter.OPEN,
        val isInitialLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val errorMessage: String? = null,
        val siteNames: Map<Long, String> = emptyMap(),
    )

    enum class Filter {
        OPEN,
        ASSIGNED,
        OVERDUE,
    }

    sealed interface Action {
        data class Initialize(
            val userId: Long,
            val sites: Map<Long, String>,
        ) : Action

        data class SearchChanged(val value: String) : Action
        data class FilterSelected(val value: Filter) : Action
        data object Refresh : Action
        data object DismissError : Action
    }

    sealed interface Event

    private var databaseCards: List<Card> = emptyList()
    private var currentUserId: Long? = null
    private var currentSiteIds: Set<Long> = emptySet()
    private var initializedScope: Pair<Long, Set<Long>>? = null
    private var hasDatabaseSnapshot: Boolean = false

    init {
        viewModelScope.launch {
            repository.observeCards().collectLatest { cards ->
                databaseCards = cards
                hasDatabaseSnapshot = true
                publishCards()
            }
        }
    }

    override fun processImpl(action: Action) {
        when (action) {
            is Action.Initialize -> initialize(action.userId, action.sites)
            is Action.SearchChanged -> {
                setState { copy(query = action.value) }
                publishCards()
            }
            is Action.FilterSelected -> {
                setState { copy(filter = action.value) }
                publishCards()
            }
            Action.Refresh -> refresh()
            Action.DismissError -> setState { copy(errorMessage = null) }
        }
    }

    private fun initialize(userId: Long, sites: Map<Long, String>) {
        currentUserId = userId
        currentSiteIds = sites.keys
        val scope = userId to sites.keys
        val scopeChanged = initializedScope != scope
        initializedScope = scope

        setState { copy(siteNames = sites) }
        publishCards()

        if (!scopeChanged) return
        refresh()
    }

    private fun refresh() {
        if (getStateValue().isRefreshing || currentSiteIds.isEmpty()) {
            if (currentSiteIds.isEmpty()) setState { copy(isInitialLoading = false) }
            return
        }
        viewModelScope.launch {
            setState {
                copy(
                    isRefreshing = true,
                    errorMessage = null,
                )
            }
            when (val result = repository.refresh(currentSiteIds.toList())) {
                is NetworkResult.Success -> setState {
                    copy(isRefreshing = false, isInitialLoading = false)
                }
                is NetworkResult.Failure -> setState {
                    copy(
                        isRefreshing = false,
                        isInitialLoading = false,
                        errorMessage = result.error.message,
                    )
                }
            }
        }
    }

    private fun publishCards() {
        val state = getStateValue()
        val scopedCards = if (initializedScope == null || currentSiteIds.isEmpty()) {
            emptyList()
        } else {
            databaseCards.filter { it.siteId in currentSiteIds }
        }
        val normalizedQuery = state.query.trim().lowercase()
        val filtered = scopedCards.filter { card ->
            val matchesFilter = when (state.filter) {
                Filter.OPEN -> card.isOpen
                Filter.ASSIGNED -> card.isOpen && (
                    card.mechanicId == currentUserId?.toString() ||
                        card.responsibleId == currentUserId?.toString()
                    )
                Filter.OVERDUE -> card.isOpen && card.isOverdue()
            }
            val matchesQuery = normalizedQuery.isEmpty() || listOfNotNull(
                card.siteCardId.takeIf { it > 0 }?.toString(),
                card.siteCode,
                card.cardTypeName,
                card.preclassifierCode,
                card.preclassifierDescription,
                card.comments,
                card.location,
                card.mechanicName,
                state.siteNames[card.siteId],
            ).any { it.lowercase().contains(normalizedQuery) }
            matchesFilter && matchesQuery
        }
        setState {
            copy(
                cards = filtered,
                totalCount = scopedCards.size,
                // The local database is the source of truth. An empty snapshot is still a
                // completed load and must not keep the full-screen loader visible while the
                // independent network refresh runs in the background.
                isInitialLoading = !hasDatabaseSnapshot,
            )
        }
    }
}

internal fun Card.isOverdue(today: LocalDate = LocalDate.now()): Boolean {
    val rawDate = dueDate?.take(10)?.takeIf(String::isNotBlank) ?: return false
    return runCatching { LocalDate.parse(rawDate).isBefore(today) }.getOrDefault(false)
}
