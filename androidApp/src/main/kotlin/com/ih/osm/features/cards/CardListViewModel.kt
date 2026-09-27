package com.ih.osm.features.cards

import androidx.lifecycle.viewModelScope
import com.ih.osm.core.feature.viewmodel.GRViewModel
import com.ih.osm.features.card.domain.model.Card
import com.ih.osm.features.card.domain.repository.CardRepository
import com.ih.osm.features.cards.domain.manager.CardSyncManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate

class CardListViewModel(
    private val repository: CardRepository,
    private val cardSyncManager: CardSyncManager,
) : GRViewModel<CardListViewModel.UiState, CardListViewModel.Action, CardListViewModel.Event>(
    initialState = UiState(),
) {
    data class UiState(
        val cards: List<Card> = emptyList(),
        val totalCount: Int = 0,
        val query: String = "",
        val filter: Filter = Filter.OPEN,
        val customFilter: CustomFilter? = null,
        val isInitialLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val errorMessage: String? = null,
        val siteNames: Map<Long, String> = emptyMap(),
    )

    enum class Filter {
        OPEN,
        ASSIGNED,
        OVERDUE,
        CUSTOM,
    }

    enum class CustomFilter {
        ALL_OPEN,
        MY_OPEN,
        MY_ASSIGNED,
        UNASSIGNED,
        DUE,
        CLOSED,
    }

    sealed interface Action {
        data class Initialize(
            val userId: Long,
            val sites: Map<Long, String>,
        ) : Action

        data class SearchChanged(val value: String) : Action
        data class FilterSelected(val value: Filter) : Action
        data class CustomFilterSelected(val value: CustomFilter) : Action
        data object ClearFilters : Action
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
        viewModelScope.launch {
            cardSyncManager.workStatus.collectLatest { workStatus ->
                setState { copy(isRefreshing = workStatus.isActive) }
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
                setState { copy(filter = action.value, customFilter = null) }
                publishCards()
            }
            is Action.CustomFilterSelected -> {
                setState { copy(filter = Filter.CUSTOM, customFilter = action.value) }
                publishCards()
            }
            Action.ClearFilters -> {
                setState { copy(query = "", filter = Filter.OPEN, customFilter = null) }
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
        setState { copy(isInitialLoading = !hasDatabaseSnapshot) }
    }

    private fun refresh() {
        if (getStateValue().isRefreshing || currentSiteIds.isEmpty()) {
            if (currentSiteIds.isEmpty()) setState { copy(isInitialLoading = false) }
            return
        }
        setState { copy(errorMessage = null) }
        cardSyncManager.enqueueRemoteChanges(currentSiteIds)
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
                Filter.CUSTOM -> when (state.customFilter) {
                    CustomFilter.ALL_OPEN -> card.isOpen
                    CustomFilter.MY_OPEN -> card.isOpen && card.creatorId == currentUserId?.toString()
                    CustomFilter.MY_ASSIGNED -> card.isOpen && card.mechanicId == currentUserId?.toString()
                    CustomFilter.UNASSIGNED -> card.isOpen && card.mechanicId.isNullOrBlank()
                    CustomFilter.DUE -> card.isOpen && card.isOverdue()
                    CustomFilter.CLOSED -> card.isClosed
                    null -> card.isOpen
                }
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
                totalCount = filtered.size,
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
