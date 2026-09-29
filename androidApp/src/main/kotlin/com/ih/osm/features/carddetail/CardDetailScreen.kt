package com.ih.osm.features.carddetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Note
import androidx.compose.material.icons.outlined.AddAlert
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Note
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ih.osm.R
import com.ih.osm.designsystem.anatomy.AnatomyCard
import com.ih.osm.designsystem.anatomy.AnatomyCardStyle
import com.ih.osm.designsystem.anatomy.AnatomyText
import com.ih.osm.designsystem.anatomy.AnatomyTextProperties
import com.ih.osm.features.card.domain.model.Card
import com.ih.osm.features.card.domain.model.CardEvidence
import com.ih.osm.features.card.domain.model.CardEvidenceStage
import com.ih.osm.features.card.domain.model.CardSyncState
import com.ih.osm.features.evidence.EvidenceMediaGallery
import com.ih.osm.features.evidence.EvidenceMediaUiItem
import org.koin.compose.viewmodel.koinViewModel

private enum class CardDetailTab { INFORMATION, EVIDENCE }

@Composable
fun CardDetailScreenRoute(
    cardUuid: String,
    siteNames: Map<Long, String>,
    onBack: () -> Unit,
    viewModel: CardDetailViewModel = koinViewModel(),
) {
    val state by viewModel.getStateFlow().collectAsStateWithLifecycle()
    LaunchedEffect(cardUuid) {
        viewModel.process(CardDetailViewModel.Action.Load(cardUuid))
    }
    CardDetailScreen(
        state = state,
        siteNames = siteNames,
        onBack = onBack,
        onPrepareEvidence = { viewModel.process(CardDetailViewModel.Action.PrepareEvidence) },
        onRetryEvidence = { viewModel.process(CardDetailViewModel.Action.ResolveEvidence(it)) },
    )
}

@Composable
private fun CardDetailScreen(
    state: CardDetailUiState,
    siteNames: Map<Long, String>,
    onBack: () -> Unit,
    onPrepareEvidence: () -> Unit,
    onRetryEvidence: (CardEvidence) -> Unit,
) {
    var selectedTab by rememberSaveable { mutableStateOf(CardDetailTab.INFORMATION) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { AnatomyText(stringResource(R.string.card_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.card_detail_back))
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.card == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                AnatomyText(stringResource(R.string.card_detail_not_found))
            }
            else -> {
                val card = state.card
                Column(Modifier.fillMaxSize().padding(padding)) {
                    CardIdentityHeader(card)
                    PrimaryTabRow(selectedTabIndex = selectedTab.ordinal) {
                        CardDetailTab.entries.forEach { tab ->
                            Tab(
                                selected = selectedTab == tab,
                                onClick = {
                                    selectedTab = tab
                                    if (tab == CardDetailTab.EVIDENCE) onPrepareEvidence()
                                },
                                text = {
                                    Text(
                                        if (tab == CardDetailTab.INFORMATION) {
                                            stringResource(R.string.card_detail_information_tab)
                                        } else {
                                            stringResource(R.string.card_detail_evidence_tab)
                                        },
                                    )
                                },
                            )
                        }
                    }
                    when (selectedTab) {
                        CardDetailTab.INFORMATION -> InformationTab(card, siteNames[card.siteId])
                        CardDetailTab.EVIDENCE -> EvidenceTab(
                            evidences = card.evidences,
                            evidenceFiles = state.evidenceFiles,
                            loadingEvidenceIds = state.loadingEvidenceIds,
                            failedEvidenceIds = state.failedEvidenceIds,
                            onRetry = onRetryEvidence,
                        )
                    }
                }
            }
        }
    }

}

@Composable
private fun CardIdentityHeader(card: Card) {
    val folio = if (card.siteCardId > 0) "#${card.siteCardId}" else stringResource(R.string.cards_local_folio)
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            DetailPill(
                label = if (card.isClosed) stringResource(R.string.cards_status_closed) else stringResource(R.string.cards_status_open),
                background = if (card.isClosed) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer,
                foreground = if (card.isClosed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer,
            )
            card.priorityDescription?.takeIf(String::isNotBlank)?.let {
                DetailPill(it, Color(0xFFFFF1D2), Color(0xFF865900))
            }
            if (card.isLocal) {
                DetailPill(
                    stringResource(R.string.cards_pending_sync),
                    MaterialTheme.colorScheme.errorContainer,
                    MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
        AnatomyText(
            text = stringResource(R.string.card_detail_folio, folio),
            style = MaterialTheme.typography.headlineSmall,
            properties = AnatomyTextProperties(fontWeight = FontWeight.Bold),
        )
        AnatomyText(
            text = listOfNotNull(card.cardTypeName, card.nodeName).filter(String::isNotBlank).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            properties = AnatomyTextProperties(color = MaterialTheme.colorScheme.onSurfaceVariant),
        )
    }
}

@Composable
private fun DetailPill(label: String, background: Color, foreground: Color) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = background) {
        AnatomyText(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            properties = AnatomyTextProperties(color = foreground, fontWeight = FontWeight.SemiBold),
        )
    }
}

@Composable
private fun InformationTab(card: Card, siteName: String?) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ReadOnlySection(stringResource(R.string.card_detail_general_information)) {
                DetailRow(Icons.Outlined.LocationOn, stringResource(R.string.card_detail_site), siteName ?: card.siteCode.orDash())
                DetailRow(Icons.Outlined.LocationOn, stringResource(R.string.card_detail_location), card.location.orDash())
                DetailRow(Icons.AutoMirrored.Outlined.Note, stringResource(R.string.card_detail_type), card.cardTypeName.orDash())
                card.cardTypeValue?.takeIf(String::isNotBlank)?.let {
                    DetailRow(null, stringResource(R.string.card_detail_classification), it)
                }
                DetailRow(Icons.Outlined.Archive, stringResource(R.string.card_detail_preclassifier), listOfNotNull(card.preclassifierCode, card.preclassifierDescription).joinToString(" · ").orDash())
                DetailRow(Icons.Outlined.AddAlert, stringResource(R.string.card_detail_priority), listOfNotNull(card.priorityCode, card.priorityDescription).joinToString(" · ").orDash())
                DetailRow(Icons.Outlined.CalendarMonth, stringResource(R.string.card_detail_created), card.creationDate.displayDate())
                DetailRow(Icons.Outlined.CalendarMonth, stringResource(R.string.card_detail_due), card.dueDate.displayDate())
                DetailRow(Icons.Outlined.Person, stringResource(R.string.card_detail_created_by), card.creatorName.orDash())
                DetailRow(Icons.Outlined.Person, stringResource(R.string.card_detail_responsible), card.responsibleName.orDash())
                DetailRow(Icons.Outlined.Person, stringResource(R.string.card_detail_mechanic), card.mechanicName.orDash())
            }
        }
        item {
            ReadOnlySection(stringResource(R.string.card_detail_problem_description)) {
                AnatomyText(
                    text = card.comments.orDash(),
                    style = MaterialTheme.typography.bodyMedium,
                    properties = AnatomyTextProperties(color = MaterialTheme.colorScheme.onSurfaceVariant),
                )
            }
        }
        item {
            SolutionSection(
                title = stringResource(R.string.card_detail_provisional_solution),
                date = card.provisionalSolutionDate,
                user = card.provisionalSolutionUserName,
                comments = card.provisionalSolutionComments,
            )
        }
        item {
            SolutionSection(
                title = stringResource(R.string.card_detail_definitive_solution),
                date = card.definitiveSolutionDate,
                user = card.definitiveSolutionUserName,
                comments = card.definitiveSolutionComments,
            )
        }
        if (!card.managerName.isNullOrBlank() || !card.managerComments.isNullOrBlank()) {
            item {
                SolutionSection(
                    title = stringResource(R.string.card_detail_manager_close),
                    date = card.managerCloseDate,
                    user = card.managerName,
                    comments = card.managerComments,
                )
            }
        }
        item {
            ReadOnlySection(stringResource(R.string.card_detail_device_information)) {
                DetailRow(
                    if (card.syncState == CardSyncState.SYNCED) Icons.Outlined.CloudDone else Icons.Outlined.CloudOff,
                    stringResource(R.string.card_detail_sync_status),
                    card.syncState.displayName(),
                )
                DetailRow(null, stringResource(R.string.card_detail_app), listOfNotNull(card.appSo, card.appVersion).joinToString(" · ").orDash())
                card.syncError?.takeIf(String::isNotBlank)?.let {
                    DetailRow(null, stringResource(R.string.card_detail_sync_error), it)
                }
            }
        }
    }
}

@Composable
private fun SolutionSection(title: String, date: String?, user: String?, comments: String?) {
    ReadOnlySection(title) {
        if (date.isNullOrBlank() && user.isNullOrBlank() && comments.isNullOrBlank()) {
            AnatomyText(
                text = stringResource(R.string.card_detail_no_solution),
                style = MaterialTheme.typography.bodyMedium,
                properties = AnatomyTextProperties(color = MaterialTheme.colorScheme.onSurfaceVariant),
            )
        } else {
            DetailRow(Icons.Outlined.CalendarMonth, stringResource(R.string.card_detail_date), date.displayDate())
            DetailRow(Icons.Outlined.Person, stringResource(R.string.card_detail_user), user.orDash())
            DetailRow(null, stringResource(R.string.card_detail_comments), comments.orDash())
        }
    }
}

@Composable
private fun ReadOnlySection(title: String, content: @Composable ColumnScope.() -> Unit) {
    AnatomyCard(
        style = AnatomyCardStyle.OUTLINED,
        contentPadding = PaddingValues(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        AnatomyText(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            properties = AnatomyTextProperties(fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun DetailRow(icon: ImageVector?, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        } else {
            Spacer(Modifier.size(18.dp))
        }
        AnatomyText(
            text = label,
            modifier = Modifier.weight(0.38f),
            style = MaterialTheme.typography.bodySmall,
            properties = AnatomyTextProperties(color = MaterialTheme.colorScheme.onSurfaceVariant),
        )
        AnatomyText(
            text = value,
            modifier = Modifier.weight(0.62f),
            style = MaterialTheme.typography.bodySmall,
            properties = AnatomyTextProperties(fontWeight = FontWeight.Medium),
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
}

@Composable
private fun EvidenceTab(
    evidences: List<CardEvidence>,
    evidenceFiles: Map<String, String>,
    loadingEvidenceIds: Set<String>,
    failedEvidenceIds: Set<String>,
    onRetry: (CardEvidence) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CardEvidenceStage.entries.forEach { stage ->
            val stageEvidences = evidences.filter { it.stage == stage }
            item(key = stage.name) {
                EvidenceStageSection(
                    stage = stage,
                    evidences = stageEvidences,
                    evidenceFiles = evidenceFiles,
                    loadingEvidenceIds = loadingEvidenceIds,
                    failedEvidenceIds = failedEvidenceIds,
                    onRetry = onRetry,
                )
            }
        }
    }
}

@Composable
private fun EvidenceStageSection(
    stage: CardEvidenceStage,
    evidences: List<CardEvidence>,
    evidenceFiles: Map<String, String>,
    loadingEvidenceIds: Set<String>,
    failedEvidenceIds: Set<String>,
    onRetry: (CardEvidence) -> Unit,
) {
    ReadOnlySection(stage.title()) {
        if (evidences.isEmpty()) {
            AnatomyText(
                text = stringResource(R.string.card_detail_no_evidence),
                style = MaterialTheme.typography.bodyMedium,
                properties = AnatomyTextProperties(color = MaterialTheme.colorScheme.onSurfaceVariant),
            )
            return@ReadOnlySection
        }
        EvidenceMediaGallery(
            evidences = evidences.map { evidence ->
                EvidenceMediaUiItem(
                    id = evidence.id,
                    source = evidenceFiles[evidence.id],
                    displayName = evidence.url.substringAfterLast('/').ifBlank { evidence.typeCode },
                    mediaType = evidence.mediaType,
                    loading = evidence.id in loadingEvidenceIds,
                    failed = evidence.id in failedEvidenceIds,
                )
            },
            isOnlyRead = true,
            onRequestSource = { item ->
                evidences.firstOrNull { it.id == item.id }?.let(onRetry)
            },
        )
    }
}

@Composable
private fun CardEvidenceStage.title(): String = when (this) {
    CardEvidenceStage.CREATION -> stringResource(R.string.card_detail_creation_evidence)
    CardEvidenceStage.PROVISIONAL_SOLUTION -> stringResource(R.string.card_detail_provisional_evidence)
    CardEvidenceStage.DEFINITIVE_SOLUTION -> stringResource(R.string.card_detail_definitive_evidence)
}

@Composable
private fun CardSyncState.displayName(): String = when (this) {
    CardSyncState.PENDING -> stringResource(R.string.card_detail_sync_pending)
    CardSyncState.SYNCING -> stringResource(R.string.card_detail_syncing)
    CardSyncState.FAILED -> stringResource(R.string.card_detail_sync_failed)
    CardSyncState.SYNCED -> stringResource(R.string.card_detail_synced)
}

private fun String?.orDash(): String = this?.takeIf(String::isNotBlank) ?: "—"

private fun String?.displayDate(): String = this?.takeIf(String::isNotBlank)
    ?.replace('T', ' ')
    ?.removeSuffix("Z")
    ?.take(16)
    ?: "—"
