package com.ih.osm.features.opl

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.ih.osm.BuildConfig
import com.ih.osm.core.feature.viewmodel.GRViewModel
import com.ih.osm.core.network.NetworkErrorKind
import com.ih.osm.core.network.NetworkResult
import com.ih.osm.features.auth.domain.model.AuthenticatedUser
import com.ih.osm.features.auth.domain.model.UserSite
import com.ih.osm.features.level.domain.model.Level
import com.ih.osm.features.level.domain.repository.LevelRepository
import com.ih.osm.features.opl.domain.model.Opl
import com.ih.osm.features.opl.domain.repository.OplRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class OplUiState(
    val initialized: Boolean = false,
    val site: UserSite? = null,
    val query: String = "",
    val selectedLevelPath: List<Level> = emptyList(),
    val isSearching: Boolean = false,
    val isLoadingLevels: Boolean = false,
    val isLevelSheetOpen: Boolean = false,
    val levelSheetQuery: String = "",
    val levelNavigationPath: List<Level> = emptyList(),
    val levelChoices: List<OplLevelChoice> = emptyList(),
    val opls: List<Opl> = emptyList(),
    val hasSearched: Boolean = false,
    val error: OplError? = null,
) {
    val selectedLevel: Level? get() = selectedLevelPath.lastOrNull()
    val selectedLocation: String get() = selectedLevelPath.joinToString(" › ") { it.name }
    val isBusy: Boolean get() = isSearching || isLoadingLevels
    val canSearch: Boolean get() = initialized && site != null && !isBusy &&
        ((selectedLevel != null && query.isBlank()) || (selectedLevel == null && query.isNotBlank()))
}

enum class OplError { SITE_UNAVAILABLE, LEVELS_UNAVAILABLE, QUERY_TOO_LONG, NO_CONNECTION, SEARCH_FAILED, ACCESS_DENIED }

class OplViewModel(
    private val repository: OplRepository,
    private val levelRepository: LevelRepository,
) : GRViewModel<OplUiState, OplViewModel.Action, OplViewModel.Event>(OplUiState()) {
    sealed interface Action {
        data class Initialize(val user: AuthenticatedUser, val siteId: Long?) : Action
        data class QueryChanged(val value: String) : Action
        data object Search : Action
        data object OpenLevelSelector : Action
        data object DismissLevelSelector : Action
        data class LevelQueryChanged(val value: String) : Action
        data class NavigateLevel(val id: String?) : Action
        data class SelectLevel(val id: String) : Action
        data object SelectCurrentLevel : Action
        data object ClearLevel : Action
        data object DismissError : Action
    }

    sealed interface Event

    private var levelSelector = LocalLevelSelector(emptyList())

    override fun processImpl(action: Action) {
        when (action) {
            is Action.Initialize -> initialize(action.user, action.siteId)
            is Action.QueryChanged -> {
                if (getStateValue().isBusy || getStateValue().selectedLevel != null) return
                if (getStateValue().query == action.value) return
                setState { copy(query = action.value, error = null, opls = emptyList(), hasSearched = false) }
            }
            Action.Search -> search()
            Action.OpenLevelSelector -> {
                if (getStateValue().isBusy || getStateValue().query.isNotBlank()) return
                loadLocalLevels(openSheet = true)
            }
            Action.DismissLevelSelector -> setState {
                copy(isLevelSheetOpen = false, levelSheetQuery = "", levelNavigationPath = emptyList())
            }
            is Action.LevelQueryChanged -> {
                if (getStateValue().isBusy || !getStateValue().isLevelSheetOpen) return
                setState { copy(levelSheetQuery = action.value).withLevelChoices() }
            }
            is Action.NavigateLevel -> navigateLevel(action.id)
            is Action.SelectLevel -> selectLevel(action.id)
            Action.SelectCurrentLevel -> getStateValue().levelNavigationPath.lastOrNull()?.let {
                selectLevel(it.id, selectParent = true)
            }
            Action.ClearLevel -> {
                if (getStateValue().isBusy) return
                setState { copy(selectedLevelPath = emptyList(), error = null, opls = emptyList(), hasSearched = false) }
            }
            Action.DismissError -> setState { copy(error = null) }
        }
    }

    private fun initialize(user: AuthenticatedUser, siteId: Long?) {
        if (getStateValue().initialized) return
        val site = if (siteId == null) user.sites.firstOrNull() else user.sites.firstOrNull { it.id == siteId }
        setState { OplUiState(initialized = true, site = site, error = if (site == null) OplError.SITE_UNAVAILABLE else null) }
        if (site != null) loadLocalLevels(openSheet = false)
    }

    private fun loadLocalLevels(openSheet: Boolean) {
        val site = getStateValue().site ?: return
        // Reload on opening: a background catalog sync may have completed since initialization.
        setState { copy(isLoadingLevels = true, error = null) }
        viewModelScope.launch {
            try {
                val levels = withContext(Dispatchers.IO) { levelRepository.getAll(site.id) }
                levelSelector = LocalLevelSelector(levels)
                val choices = levelSelector.choices("", null)
                setState {
                    copy(
                        isLevelSheetOpen = openSheet && choices.isNotEmpty(),
                        levelSheetQuery = "",
                        levelNavigationPath = emptyList(),
                        levelChoices = choices,
                        error = if (openSheet && choices.isEmpty()) OplError.LEVELS_UNAVAILABLE else null,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e(TAG, "Unable to read local LEVEL catalog siteId=${site.id}", error)
                setState { copy(error = OplError.LEVELS_UNAVAILABLE, isLevelSheetOpen = false) }
            } finally {
                setState { copy(isLoadingLevels = false) }
            }
        }
    }

    private fun navigateLevel(id: String?) {
        if (getStateValue().isBusy || !getStateValue().isLevelSheetOpen) return
        if (id != null && levelSelector.level(id) == null) return
        setState {
            copy(levelNavigationPath = id?.let(levelSelector::pathTo).orEmpty(), levelSheetQuery = "")
                .withLevelChoices()
        }
    }

    private fun selectLevel(id: String, selectParent: Boolean = false) {
        val state = getStateValue()
        if (state.isBusy || !state.isLevelSheetOpen || state.query.isNotBlank()) return
        val level = levelSelector.level(id) ?: return
        if (levelSelector.hasChildren(id) && !selectParent) {
            navigateLevel(id)
            return
        }
        setState {
            copy(
                query = "",
                selectedLevelPath = levelSelector.pathTo(level.id),
                isLevelSheetOpen = false,
                levelSheetQuery = "",
                levelNavigationPath = emptyList(),
                error = null,
            )
        }
        search()
    }

    private fun OplUiState.withLevelChoices(): OplUiState = copy(
        levelChoices = levelSelector.choices(levelSheetQuery, levelNavigationPath.lastOrNull()?.id),
    )

    private fun search() {
        val state = getStateValue()
        if (!state.canSearch) return
        val site = state.site ?: return
        val level = state.selectedLevel
        val query = state.query.trim()
        if (level != null && query.isNotEmpty()) return // Never combine the two remote filters.
        if (query.length > 100) {
            setState { copy(error = OplError.QUERY_TOO_LONG) }
            return
        }
        // Set synchronously before launching, so repeated taps/IME events cannot race.
        setState {
            copy(isSearching = true, error = null, isLevelSheetOpen = false, opls = emptyList(), hasSearched = false)
        }
        Log.i(TAG, "Searching OPL siteId=${site.id} mode=${if (level == null) "name" else "level"} levelId=${level?.id}")
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    if (level == null) repository.search(site.id, query)
                    else repository.getByLevel(site.id, level.id)
                }
                when (result) {
                    is NetworkResult.Success -> publishResults(site.id, result.data)
                    is NetworkResult.Failure -> {
                        // The existing by-LEVEL contract returns 404 for no direct assignments.
                        if (level != null && result.error.statusCode == 404) {
                            publishResults(site.id, emptyList())
                        } else {
                            Log.w(TAG, "OPL search failed kind=${result.error.kind} status=${result.error.statusCode}: ${result.error.message}")
                            setState {
                                copy(error = when {
                                    result.error.statusCode == 401 || result.error.statusCode == 403 -> OplError.ACCESS_DENIED
                                    result.error.kind == NetworkErrorKind.CONNECTIVITY -> OplError.NO_CONNECTION
                                    else -> OplError.SEARCH_FAILED
                                })
                            }
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e(TAG, "OPL search failed siteId=${site.id}", error)
                setState { copy(error = OplError.SEARCH_FAILED) }
            } finally {
                setState { copy(isSearching = false) }
            }
        }
    }

    private fun publishResults(siteId: Long, opls: List<Opl>) {
        setState { copy(opls = opls, hasSearched = true, error = null) }
        Log.i(TAG, "OPL results siteId=$siteId count=${opls.size}")
        // Detailed lesson content/URLs are for development logs, never release logs.
        if (BuildConfig.DEBUG) {
            if (opls.isEmpty()) Log.d(TAG, "OPL results: []")
            opls.forEach { opl ->
                opl.toString().chunked(3000).forEachIndexed { index, chunk ->
                    Log.d(TAG, "OPL id=${opl.id} part=${index + 1}: $chunk")
                }
            }
        }
    }

    private companion object { const val TAG = "OplViewModel" }
}
