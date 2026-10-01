package com.ih.osm.features.opl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ih.osm.R
import com.ih.osm.designsystem.anatomy.AnatomyBanner
import com.ih.osm.designsystem.anatomy.AnatomyBannerType
import com.ih.osm.designsystem.anatomy.AnatomyButton
import com.ih.osm.designsystem.anatomy.AnatomyCard
import com.ih.osm.designsystem.anatomy.AnatomyLevelBreadcrumbs
import com.ih.osm.designsystem.anatomy.AnatomySearch
import com.ih.osm.designsystem.anatomy.AnatomySelectionBottomSheet
import com.ih.osm.designsystem.anatomy.AnatomySelectionField
import com.ih.osm.designsystem.anatomy.AnatomySelectionItem
import com.ih.osm.designsystem.anatomy.AnatomyText
import com.ih.osm.designsystem.anatomy.AnatomyTextProperties
import com.ih.osm.features.auth.domain.model.AuthenticatedUser
import com.ih.osm.features.opl.components.OplCard
import com.ih.osm.features.opl.domain.model.Opl
import org.koin.androidx.compose.koinViewModel

@Composable
fun OplScreenRoute(
    user: AuthenticatedUser,
    siteId: Long,
    onBack: () -> Unit,
    onOpenOpl: (Long) -> Unit,
    viewModel: OplViewModel = koinViewModel(),
) {
    val state by viewModel.getStateFlow().collectAsStateWithLifecycle()
    LaunchedEffect(user.id, siteId) {
        viewModel.process(OplViewModel.Action.Initialize(user, siteId))
    }
    OplScreen(state = state, onAction = viewModel::process, onBack = onBack, onOpenOpl = onOpenOpl)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OplScreen(
    state: OplUiState,
    onAction: (OplViewModel.Action) -> Unit,
    onBack: () -> Unit,
    onOpenOpl: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(state.isSearching, state.isLevelSheetOpen) {
        if (state.isSearching || state.isLevelSheetOpen) {
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        AnatomyText(stringResource(R.string.opl_title), style = MaterialTheme.typography.titleLarge,
                            properties = AnatomyTextProperties(fontWeight = FontWeight.Bold))
                        AnatomyText(stringResource(R.string.opl_subtitle), style = MaterialTheme.typography.bodySmall,
                            properties = AnatomyTextProperties(color = MaterialTheme.colorScheme.onSurfaceVariant))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.navigation_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 720.dp).fillMaxSize().imePadding(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "search_controls") {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        state.error?.let { error ->
                            AnatomyBanner(
                                message = stringResource(error.messageResource()),
                                type = AnatomyBannerType.ERROR,
                                onDismiss = { onAction(OplViewModel.Action.DismissError) },
                            )
                        }
                        AnatomySearch(
                            value = state.query,
                            onValueChange = { onAction(OplViewModel.Action.QueryChanged(it)) },
                            placeholder = stringResource(R.string.opl_search_placeholder),
                            enabled = state.initialized && state.site != null && !state.isBusy && state.selectedLevel == null,
                            onSearch = { onAction(OplViewModel.Action.Search) },
                        )
                        AnatomySelectionField(
                            label = stringResource(R.string.create_card_level_label),
                            value = state.selectedLocation.takeIf(String::isNotBlank),
                            placeholder = stringResource(R.string.create_card_level_placeholder),
                            icon = Icons.Outlined.LocationOn,
                            enabled = state.initialized && state.site != null && !state.isBusy && state.query.isBlank(),
                            onClick = { onAction(OplViewModel.Action.OpenLevelSelector) },
                            onClear = { onAction(OplViewModel.Action.ClearLevel) },
                        )
                        AnatomyText(
                            text = stringResource(when {
                                state.selectedLevel != null -> R.string.opl_clear_level_to_search
                                state.query.isNotBlank() -> R.string.opl_clear_query_to_select
                                else -> R.string.opl_search_mode_helper
                            }),
                            style = MaterialTheme.typography.bodySmall,
                            properties = AnatomyTextProperties(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        )
                        AnatomyButton(
                            text = stringResource(R.string.opl_search_action),
                            onClick = { onAction(OplViewModel.Action.Search) },
                            enabled = state.canSearch,
                            leadingIcon = Icons.Outlined.Search,
                        )
                        if (state.isBusy) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            AnatomyText(
                                stringResource(if (state.isSearching) R.string.opl_searching else R.string.opl_loading_levels),
                                style = MaterialTheme.typography.bodyMedium,
                                properties = AnatomyTextProperties(color = MaterialTheme.colorScheme.onSurfaceVariant),
                            )
                        }
                    }
                }
                if (state.hasSearched && !state.isSearching) {
                    item(key = "result_count") {
                        AnatomyText(
                            text = pluralStringResource(R.plurals.opl_result_count, state.opls.size, state.opls.size),
                            style = MaterialTheme.typography.titleMedium,
                            properties = AnatomyTextProperties(fontWeight = FontWeight.SemiBold),
                        )
                    }
                    if (state.opls.isEmpty()) {
                        item(key = "empty_results") { OplEmptyResults() }
                    } else {
                        items(state.opls, key = Opl::id) { opl ->
                            OplCard(opl = opl, onClick = { onOpenOpl(opl.id) })
                        }
                    }
                }
            }
        }
    }
    if (state.isLevelSheetOpen && !state.isBusy) {
        AnatomySelectionBottomSheet(
            title = stringResource(R.string.create_card_select_level),
            query = state.levelSheetQuery,
            searchPlaceholder = stringResource(R.string.create_card_search_placeholder),
            items = state.levelChoices.map { AnatomySelectionItem(it.id, it.title, it.subtitle, it.hasChildren) },
            selectedItemId = state.selectedLevel?.id,
            onQueryChanged = { onAction(OplViewModel.Action.LevelQueryChanged(it)) },
            onSelectItem = { onAction(OplViewModel.Action.SelectLevel(it)) },
            onDismiss = { onAction(OplViewModel.Action.DismissLevelSelector) },
            breadcrumbs = {
                AnatomyLevelBreadcrumbs(
                    path = state.levelNavigationPath.map { AnatomySelectionItem(it.id, it.name) },
                    onNavigate = { onAction(OplViewModel.Action.NavigateLevel(it)) },
                )
            },
            footer = if (state.levelNavigationPath.isNotEmpty() && state.levelSheetQuery.isBlank()) {
                {
                    AnatomyButton(
                        text = stringResource(R.string.opl_select_current_level, state.levelNavigationPath.last().name),
                        onClick = { onAction(OplViewModel.Action.SelectCurrentLevel) },
                    )
                }
            } else null,
        )
    }
}

@Composable
private fun OplEmptyResults() {
    AnatomyCard(contentPadding = PaddingValues(24.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Outlined.SearchOff,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            AnatomyText(
                text = stringResource(R.string.opl_empty_title),
                style = MaterialTheme.typography.titleMedium,
                properties = AnatomyTextProperties(fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
            )
            AnatomyText(
                text = stringResource(R.string.opl_empty_description),
                style = MaterialTheme.typography.bodyMedium,
                properties = AnatomyTextProperties(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }
}

private fun OplError.messageResource(): Int = when (this) {
    OplError.SITE_UNAVAILABLE -> R.string.opl_site_unavailable
    OplError.LEVELS_UNAVAILABLE -> R.string.opl_levels_unavailable
    OplError.QUERY_TOO_LONG -> R.string.opl_query_too_long
    OplError.NO_CONNECTION -> R.string.opl_no_connection
    OplError.SEARCH_FAILED -> R.string.opl_search_failed
    OplError.ACCESS_DENIED -> R.string.opl_access_denied
}
