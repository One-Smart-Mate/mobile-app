package com.ih.osm.features.card

import com.ih.osm.core.network.NetworkResult
import com.ih.osm.features.card.domain.model.Card
import com.ih.osm.features.card.domain.repository.CardRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class IosCardSite(
    val siteId: Long,
    val name: String,
)

enum class IosCardListFilter {
    OPEN,
    ASSIGNED,
    OVERDUE,
    CUSTOM,
}

enum class IosCardListCustomFilter {
    ALL_OPEN,
    MY_OPEN,
    MY_ASSIGNED,
    UNASSIGNED,
    DUE,
    CLOSED,
}

data class IosCardListState(
    val cards: List<Card> = emptyList(),
    val totalCount: Int = 0,
    val query: String = "",
    val filter: IosCardListFilter = IosCardListFilter.OPEN,
    val customFilter: IosCardListCustomFilter? = null,
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * iOS-facing adapter for the shared cards feature.
 *
 * The database remains the source of truth. Search and filter rules live here so the
 * SwiftUI view model only forwards user intent and renders already processed state.
 */
class IosCardListController(
    private val repository: CardRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observationJob: Job? = null
    private var refreshJob: Job? = null
    private var observer: ((IosCardListState) -> Unit)? = null
    private var state = IosCardListState()
    private var databaseCards: List<Card> = emptyList()
    private var currentUserId: Long? = null
    private var currentSiteIds: Set<Long> = emptySet()
    private var siteNames: Map<Long, String> = emptyMap()
    private var todayIso: String = ""
    private var hasDatabaseSnapshot = false
    private var initializedScope: Pair<Long, Set<Long>>? = null

    fun start(
        userId: Long,
        sites: List<IosCardSite>,
        todayIso: String,
        onStateChanged: (IosCardListState) -> Unit,
    ) {
        observer = onStateChanged
        currentUserId = userId
        currentSiteIds = sites.mapTo(linkedSetOf(), IosCardSite::siteId)
        siteNames = sites.associate { it.siteId to it.name }
        this.todayIso = todayIso

        if (observationJob == null) {
            observationJob = scope.launch {
                repository.observeCards().collectLatest { cards ->
                    databaseCards = cards
                    hasDatabaseSnapshot = true
                    publish()
                }
            }
        }

        val nextScope = userId to currentSiteIds
        val shouldRefresh = initializedScope != nextScope
        initializedScope = nextScope
        publish()
        if (shouldRefresh) requestRefresh()
    }

    fun setQuery(value: String) {
        state = state.copy(query = value)
        publish()
    }

    fun selectFilter(filter: IosCardListFilter) {
        state = state.copy(filter = filter, customFilter = null)
        publish()
    }

    fun selectCustomFilter(filter: IosCardListCustomFilter) {
        state = state.copy(filter = IosCardListFilter.CUSTOM, customFilter = filter)
        publish()
    }

    fun clearFilters() {
        state = state.copy(
            query = "",
            filter = IosCardListFilter.OPEN,
            customFilter = null,
        )
        publish()
    }

    fun dismissError() {
        state = state.copy(errorMessage = null)
        emit()
    }

    fun requestRefresh() {
        if (refreshJob?.isActive == true || currentSiteIds.isEmpty()) {
            if (currentSiteIds.isEmpty()) {
                state = state.copy(isInitialLoading = false)
                emit()
            }
            return
        }
        refreshJob = scope.launch { refresh() }
    }

    suspend fun refresh() {
        if (state.isRefreshing || currentSiteIds.isEmpty()) return
        state = state.copy(isRefreshing = true, errorMessage = null)
        emit()
        when (val result = repository.refresh(currentSiteIds.toList())) {
            is NetworkResult.Success -> state = state.copy(
                isRefreshing = false,
                isInitialLoading = false,
            )
            is NetworkResult.Failure -> state = state.copy(
                isRefreshing = false,
                isInitialLoading = false,
                errorMessage = result.error.message,
            )
        }
        emit()
    }

    fun stop() {
        observationJob?.cancel()
        observationJob = null
        refreshJob?.cancel()
        refreshJob = null
        observer = null
    }

    private fun publish() {
        val scopedCards = if (currentSiteIds.isEmpty()) {
            emptyList()
        } else {
            databaseCards.filter { it.siteId in currentSiteIds }
        }
        val normalizedQuery = state.query.trim().lowercase()
        val filtered = scopedCards.filter { card ->
            matchesSelectedFilter(card) && (
                normalizedQuery.isEmpty() || listOfNotNull(
                    card.siteCardId.takeIf { it > 0 }?.toString(),
                    card.siteCode,
                    card.cardTypeName,
                    card.preclassifierCode,
                    card.preclassifierDescription,
                    card.comments,
                    card.location,
                    card.mechanicName,
                    siteNames[card.siteId],
                ).any { value -> value.lowercase().contains(normalizedQuery) }
                )
        }
        state = state.copy(
            cards = filtered,
            totalCount = filtered.size,
            isInitialLoading = !hasDatabaseSnapshot,
        )
        emit()
    }

    private fun matchesSelectedFilter(card: Card): Boolean = when (state.filter) {
        IosCardListFilter.OPEN -> card.isOpen
        IosCardListFilter.ASSIGNED -> card.isOpen && (
            card.mechanicId == currentUserId?.toString() ||
                card.responsibleId == currentUserId?.toString()
            )
        IosCardListFilter.OVERDUE -> card.isOpen && card.isOverdue()
        IosCardListFilter.CUSTOM -> when (state.customFilter) {
            IosCardListCustomFilter.ALL_OPEN -> card.isOpen
            IosCardListCustomFilter.MY_OPEN -> card.isOpen && card.creatorId == currentUserId?.toString()
            IosCardListCustomFilter.MY_ASSIGNED -> card.isOpen && card.mechanicId == currentUserId?.toString()
            IosCardListCustomFilter.UNASSIGNED -> card.isOpen && card.mechanicId.isNullOrBlank()
            IosCardListCustomFilter.DUE -> card.isOpen && card.isOverdue()
            IosCardListCustomFilter.CLOSED -> card.isClosed
            null -> card.isOpen
        }
    }

    private fun Card.isOverdue(): Boolean {
        val date = dueDate?.take(10)?.takeIf(String::isNotBlank) ?: return false
        return todayIso.isNotBlank() && date < todayIso
    }

    private fun emit() {
        observer?.invoke(state)
    }
}
