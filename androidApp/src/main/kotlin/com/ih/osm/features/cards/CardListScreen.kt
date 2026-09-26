package com.ih.osm.features.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ih.osm.R
import com.ih.osm.designsystem.anatomy.AnatomyBanner
import com.ih.osm.designsystem.anatomy.AnatomyBannerType
import com.ih.osm.designsystem.anatomy.AnatomyText
import com.ih.osm.designsystem.anatomy.AnatomyTextProperties
import com.ih.osm.designsystem.preview.PreviewScreen
import com.ih.osm.designsystem.theme.OneSmartMateTheme
import com.ih.osm.features.auth.domain.model.AuthenticatedUser
import com.ih.osm.features.card.domain.model.Card
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CardListScreenRoute(
    user: AuthenticatedUser,
    onCreateCard: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CardListViewModel = koinViewModel(),
) {
    val uiState by viewModel.getStateFlow().collectAsStateWithLifecycle()

    LaunchedEffect(user.id, user.sites) {
        viewModel.process(
            CardListViewModel.Action.Initialize(
                userId = user.id,
                sites = user.sites.associate { it.id to it.name },
            ),
        )
    }

    CardListScreen(
        uiState = uiState,
        onAction = viewModel::process,
        onCreateCard = onCreateCard,
        modifier = modifier,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CardListScreen(
    uiState: CardListViewModel.UiState,
    onAction: (CardListViewModel.Action) -> Unit,
    onCreateCard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateCard,
                modifier = Modifier.size(54.dp),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.cards_create))
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 88.dp),
        ) {
            stickyHeader {
                CardListHeader(
                    uiState = uiState,
                    onAction = onAction,
                )
            }

            uiState.errorMessage?.let { message ->
                item(key = "error") {
                    AnatomyBanner(
                        message = message,
                        type = AnatomyBannerType.ERROR,
                        actionLabel = stringResource(R.string.cards_retry),
                        onAction = { onAction(CardListViewModel.Action.Refresh) },
                        onDismiss = { onAction(CardListViewModel.Action.DismissError) },
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }

            if (uiState.isInitialLoading && uiState.cards.isEmpty()) {
                item(key = "loading") { LoadingState() }
            } else if (uiState.cards.isEmpty()) {
                item(key = "empty") { EmptyState(uiState.query.isNotBlank()) }
            } else {
                items(uiState.cards, key = Card::uuid) { card ->
                    CardListItem(
                        card = card,
                        siteName = uiState.siteNames[card.siteId],
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CardListHeader(
    uiState: CardListViewModel.UiState,
    onAction: (CardListViewModel.Action) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    AnatomyText(
                        text = stringResource(R.string.cards_title),
                        style = MaterialTheme.typography.headlineMedium,
                        properties = AnatomyTextProperties(fontWeight = FontWeight.Bold),
                    )
                    AnatomyText(
                        text = stringResource(R.string.cards_count, uiState.totalCount),
                        style = MaterialTheme.typography.bodyMedium,
                        properties = AnatomyTextProperties(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                }
                if (uiState.isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                IconButton(
                    onClick = { onAction(CardListViewModel.Action.Refresh) },
                    enabled = !uiState.isRefreshing,
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.cards_refresh))
                }
            }

            CardSearchField(
                value = uiState.query,
                onValueChange = { onAction(CardListViewModel.Action.SearchChanged(it)) },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    text = stringResource(R.string.cards_filter_open),
                    selected = uiState.filter == CardListViewModel.Filter.OPEN,
                    onClick = { onAction(CardListViewModel.Action.FilterSelected(CardListViewModel.Filter.OPEN)) },
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    text = stringResource(R.string.cards_filter_assigned),
                    selected = uiState.filter == CardListViewModel.Filter.ASSIGNED,
                    onClick = { onAction(CardListViewModel.Action.FilterSelected(CardListViewModel.Filter.ASSIGNED)) },
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    text = stringResource(R.string.cards_filter_overdue),
                    selected = uiState.filter == CardListViewModel.Filter.OVERDUE,
                    onClick = { onAction(CardListViewModel.Action.FilterSelected(CardListViewModel.Filter.OVERDUE)) },
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.Tune,
                            contentDescription = stringResource(R.string.cards_filters),
                            modifier = Modifier.size(19.dp),
                        )
                        Box(
                            modifier = Modifier.align(Alignment.TopEnd).size(9.dp)
                                .clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CardSearchField(value: String, onValueChange: (String) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            AnatomyText(
                                text = stringResource(R.string.cards_search_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                properties = AnatomyTextProperties(color = MaterialTheme.colorScheme.onSurfaceVariant),
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }
    }
}

@Composable
private fun FilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            AnatomyText(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                properties = AnatomyTextProperties(
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                ),
            )
        }
    }
}

@Composable
private fun CardListItem(
    card: Card,
    siteName: String?,
    modifier: Modifier = Modifier,
) {
    val overdue = card.isOpen && card.isOverdue()
    val folio = if (card.siteCardId > 0) "#${card.siteCardId}" else stringResource(R.string.cards_local_folio)
    Surface(
        modifier = modifier.fillMaxWidth().widthIn(max = 720.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnatomyText(
                    text = buildString {
                        append(folio)
                        (siteName ?: card.siteCode)?.takeIf(String::isNotBlank)?.let { append(" · $it") }
                    },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    properties = AnatomyTextProperties(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    ),
                )
                StatusPill(card = card, overdue = overdue)
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier.size(10.dp).clip(CircleShape).background(card.typeColor()),
                )
                AnatomyText(
                    text = card.cardTypeName.orEmpty().ifBlank { stringResource(R.string.cards_unknown_type) },
                    style = MaterialTheme.typography.bodySmall,
                    properties = AnatomyTextProperties(color = MaterialTheme.colorScheme.onSurfaceVariant),
                )
            }

            AnatomyText(
                text = card.problemDescription.ifBlank { stringResource(R.string.cards_no_description) },
                style = MaterialTheme.typography.titleMedium,
                properties = AnatomyTextProperties(
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                ),
            )

            card.location?.takeIf(String::isNotBlank)?.let { location ->
                IconTextRow(Icons.Outlined.LocationOn, location)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PriorityPill(card)
                AnatomyText(
                    text = stringResource(R.string.cards_created, card.creationDate.compactDate()),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    properties = AnatomyTextProperties(color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1),
                )
                val endDate = when {
                    card.isClosed -> stringResource(
                        R.string.cards_closed_date,
                        (card.definitiveSolutionDate ?: card.updatedAt ?: card.dueDate).compactDate(),
                    )
                    overdue -> stringResource(R.string.cards_overdue_date, card.dueDate.compactDate())
                    else -> stringResource(R.string.cards_due_date, card.dueDate.compactDate())
                }
                AnatomyText(
                    text = endDate,
                    style = MaterialTheme.typography.labelMedium,
                    properties = AnatomyTextProperties(
                        color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (overdue) FontWeight.SemiBold else null,
                        maxLines = 1,
                    ),
                )
            }

            if (card.isLocal || card.hasLocalSolutions) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (card.isLocal) LocalBadge(stringResource(R.string.cards_pending_sync), Icons.Outlined.Sync)
                    if (card.hasLocalSolutions) LocalBadge(
                        stringResource(R.string.cards_local_solution),
                        Icons.AutoMirrored.Outlined.Assignment,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(30.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Person, contentDescription = null, modifier = Modifier.size(17.dp))
                }
                Spacer(Modifier.width(9.dp))
                AnatomyText(
                    text = card.mechanicName?.takeIf(String::isNotBlank)
                        ?: card.responsibleName?.takeIf(String::isNotBlank)
                        ?: stringResource(R.string.cards_unassigned),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    properties = AnatomyTextProperties(maxLines = 1, overflow = TextOverflow.Ellipsis),
                )
                EvidenceCount(Icons.Outlined.CameraAlt, card.evidenceImageCreation)
                EvidenceCount(Icons.Outlined.MicNone, card.evidenceAudioCreation)
                EvidenceCount(Icons.Outlined.Videocam, card.evidenceVideoCreation)
            }
        }
    }
}

@Composable
private fun StatusPill(card: Card, overdue: Boolean) {
    val (label, background, foreground) = when {
        overdue -> Triple(
            stringResource(R.string.cards_status_overdue),
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.error,
        )
        card.isClosed -> Triple(
            stringResource(R.string.cards_status_closed),
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> Triple(
            stringResource(R.string.cards_status_open),
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.primary,
        )
    }
    Surface(shape = RoundedCornerShape(50), color = background) {
        AnatomyText(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            properties = AnatomyTextProperties(color = foreground, fontWeight = FontWeight.Bold),
        )
    }
}

@Composable
private fun PriorityPill(card: Card) {
    val code = card.priorityCode?.takeIf(String::isNotBlank) ?: return
    val description = card.priorityDescription?.takeIf(String::isNotBlank)
    val critical = code.equals("P1", true) || description?.contains("alta", true) == true ||
        description?.contains("crítica", true) == true
    val background = if (critical) MaterialTheme.colorScheme.errorContainer else Color(0xFFFFF1D2)
    val foreground = if (critical) MaterialTheme.colorScheme.error else Color(0xFF865900)
    Surface(shape = RoundedCornerShape(7.dp), color = background) {
        AnatomyText(
            text = listOfNotNull(code, description).joinToString(" · "),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            properties = AnatomyTextProperties(color = foreground, fontWeight = FontWeight.SemiBold, maxLines = 1),
        )
    }
}

@Composable
private fun LocalBadge(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(
        shape = RoundedCornerShape(7.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, Color(0xFFD77A00)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFFD77A00))
            AnatomyText(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                properties = AnatomyTextProperties(color = Color(0xFFD77A00), fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

@Composable
private fun IconTextRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        AnatomyText(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            properties = AnatomyTextProperties(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            ),
        )
    }
}

@Composable
private fun EvidenceCount(icon: androidx.compose.ui.graphics.vector.ImageVector, count: Long) {
    Row(
        modifier = Modifier.padding(start = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        AnatomyText(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            properties = AnatomyTextProperties(color = MaterialTheme.colorScheme.onSurfaceVariant),
        )
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxWidth().height(280.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun EmptyState(hasQuery: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.AutoMirrored.Outlined.Assignment,
            contentDescription = null,
            modifier = Modifier.size(42.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AnatomyText(
            text = stringResource(if (hasQuery) R.string.cards_empty_search else R.string.cards_empty_filter),
            style = MaterialTheme.typography.titleMedium,
            properties = AnatomyTextProperties(fontWeight = FontWeight.SemiBold),
        )
        AnatomyText(
            text = stringResource(R.string.cards_empty_support),
            style = MaterialTheme.typography.bodyMedium,
            properties = AnatomyTextProperties(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            ),
        )
    }
}

private fun Card.typeColor(): Color {
    val raw = cardTypeColor?.takeIf(String::isNotBlank) ?: return Color(0xFF3B8D2F)
    return runCatching {
        Color(android.graphics.Color.parseColor(if (raw.startsWith("#")) raw else "#$raw"))
    }.getOrDefault(Color(0xFF3B8D2F))
}

private fun String?.compactDate(): String {
    val raw = this?.take(10)?.takeIf(String::isNotBlank) ?: return "—"
    return runCatching {
        val spanishMexico = Locale.Builder().setLanguage("es").setRegion("MX").build()
        LocalDate.parse(raw).format(DateTimeFormatter.ofPattern("dd MMM", spanishMexico))
    }.getOrDefault(raw)
}

@PreviewScreen
@Composable
private fun CardListScreenPreview() {
    OneSmartMateTheme {
        CardListScreen(
            uiState = CardListViewModel.UiState(
                cards = previewCards,
                totalCount = 24,
                siteNames = mapOf(1L to "Planta Norte"),
                isInitialLoading = false,
            ),
            onAction = {},
            onCreateCard = {},
        )
    }
}

private val previewCards = listOf(
    Card(
        uuid = "1", serverId = "1842", siteCardId = 1842, siteId = 1, siteCode = "NTE",
        cardTypeColor = "3B8D2F", status = "A", creationDate = "2026-09-16", dueDate = "2026-09-18",
        priorityId = "1", priorityCode = "P1", priorityDescription = "Alta", nodeId = "10",
        nodeName = "Bomba 1", cardTypeId = "1", cardTypeName = "Mantenimiento", cardTypeValue = null,
        preclassifierId = "1", preclassifierCode = "FUG",
        preclassifierDescription = "Fuga", creatorId = "1", creatorName = "Diego", responsibleId = "2",
        responsibleName = "Juan Méndez", mechanicId = "2", mechanicName = "Juan Méndez",
        comments = "Fuga de aceite en bomba hidráulica", evidenceAudioCreation = 1,
        evidenceVideoCreation = 0, evidenceImageCreation = 3, location = "Producción / Línea 2 / Bomba 1",
        definitiveSolutionDate = null, isLocal = false, hasLocalSolutions = false, updatedAt = null,
    ),
    Card(
        uuid = "2", serverId = "1839", siteCardId = 1839, siteId = 1, siteCode = "NTE",
        cardTypeColor = "E53935", status = "A", creationDate = "2026-09-12", dueDate = "2026-09-15",
        priorityId = "1", priorityCode = "P1", priorityDescription = "Crítica", nodeId = "11",
        nodeName = "Transportador 4", cardTypeId = "2", cardTypeName = "Seguridad", cardTypeValue = "unsafe",
        preclassifierId = "2", preclassifierCode = "SEG",
        preclassifierDescription = "Protección", creatorId = "1", creatorName = "Diego", responsibleId = null,
        responsibleName = null, mechanicId = null, mechanicName = null, comments = "Guarda de protección dañada",
        evidenceAudioCreation = 0, evidenceVideoCreation = 1, evidenceImageCreation = 2,
        location = "Envasado / Transportador 4", definitiveSolutionDate = null, isLocal = true,
        hasLocalSolutions = false, updatedAt = null,
    ),
)
