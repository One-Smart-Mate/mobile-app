package com.ih.osm.features.carddetail

import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ih.osm.R
import com.ih.osm.designsystem.anatomy.AnatomyCard
import com.ih.osm.designsystem.anatomy.AnatomyCardStyle
import com.ih.osm.designsystem.anatomy.AnatomyImage
import com.ih.osm.designsystem.anatomy.AnatomyImageSource
import com.ih.osm.designsystem.anatomy.AnatomyText
import com.ih.osm.designsystem.anatomy.AnatomyTextProperties
import com.ih.osm.features.card.domain.model.Card
import com.ih.osm.features.card.domain.model.CardEvidence
import com.ih.osm.features.card.domain.model.CardEvidenceMediaType
import com.ih.osm.features.card.domain.model.CardEvidenceStage
import com.ih.osm.features.card.domain.model.CardSyncState
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel

private enum class CardDetailTab { INFORMATION, EVIDENCE }

@Composable
fun CardDetailScreenRoute(
    cardUuid: String,
    siteNames: Map<Long, String>,
    onBack: () -> Unit,
    viewModel: CardDetailViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(cardUuid) { viewModel.load(cardUuid) }
    CardDetailScreen(
        state = state,
        siteNames = siteNames,
        onBack = onBack,
    )
}

@Composable
private fun CardDetailScreen(
    state: CardDetailUiState,
    siteNames: Map<Long, String>,
    onBack: () -> Unit,
) {
    var selectedTab by rememberSaveable { mutableStateOf(CardDetailTab.INFORMATION) }
    var previewImage by remember { mutableStateOf<String?>(null) }
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
                                onClick = { selectedTab = tab },
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
                        CardDetailTab.EVIDENCE -> EvidenceTab(card.evidences, onPreviewImage = { previewImage = it })
                    }
                }
            }
        }
    }

    previewImage?.let { url ->
        ImagePreviewDialog(url = url, onDismiss = { previewImage = null })
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
                DetailRow(null, stringResource(R.string.card_detail_type), card.cardTypeName.orDash())
                card.cardTypeValue?.takeIf(String::isNotBlank)?.let {
                    DetailRow(null, stringResource(R.string.card_detail_classification), it)
                }
                DetailRow(null, stringResource(R.string.card_detail_preclassifier), listOfNotNull(card.preclassifierCode, card.preclassifierDescription).joinToString(" · ").orDash())
                DetailRow(null, stringResource(R.string.card_detail_priority), listOfNotNull(card.priorityCode, card.priorityDescription).joinToString(" · ").orDash())
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
private fun EvidenceTab(evidences: List<CardEvidence>, onPreviewImage: (String) -> Unit) {
    var activeAudioId by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
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
                    activeAudioId = activeAudioId,
                    onAudioToggle = { id -> activeAudioId = if (activeAudioId == id) null else id },
                    onAudioFinished = { activeAudioId = null },
                    onImageClick = onPreviewImage,
                    onVideoClick = { openMedia(context, it, "video/*") },
                )
            }
        }
    }
}

@Composable
private fun EvidenceStageSection(
    stage: CardEvidenceStage,
    evidences: List<CardEvidence>,
    activeAudioId: String?,
    onAudioToggle: (String) -> Unit,
    onAudioFinished: () -> Unit,
    onImageClick: (String) -> Unit,
    onVideoClick: (String) -> Unit,
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
        val images = evidences.filter { it.mediaType == CardEvidenceMediaType.IMAGE }
        val videos = evidences.filter { it.mediaType == CardEvidenceMediaType.VIDEO }
        val audios = evidences.filter { it.mediaType == CardEvidenceMediaType.AUDIO }
        if (images.isNotEmpty()) {
            MediaHeading(Icons.Outlined.Image, stringResource(R.string.card_detail_images), images.size)
            images.chunked(2).forEach { rowItems ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowItems.forEach { evidence ->
                        Surface(
                            onClick = { onImageClick(evidence.url) },
                            modifier = Modifier.weight(1f).aspectRatio(1.35f),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            AnatomyImage(
                                source = AnatomyImageSource.Url(evidence.url),
                                contentDescription = stringResource(R.string.card_detail_image_description),
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        if (videos.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            MediaHeading(Icons.Outlined.Videocam, stringResource(R.string.card_detail_videos), videos.size)
            videos.chunked(2).forEach { rowItems ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowItems.forEach { evidence ->
                        Surface(
                            onClick = { onVideoClick(evidence.url) },
                            modifier = Modifier.weight(1f).aspectRatio(1.5f),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Videocam, contentDescription = null, modifier = Modifier.size(32.dp))
                                Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primary) {
                                    Icon(
                                        Icons.Outlined.PlayArrow,
                                        contentDescription = stringResource(R.string.card_detail_play_video),
                                        modifier = Modifier.padding(8.dp).size(22.dp),
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                    )
                                }
                            }
                        }
                    }
                    if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        if (audios.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            MediaHeading(Icons.Outlined.AudioFile, stringResource(R.string.card_detail_audios), audios.size)
            audios.forEach { evidence ->
                AudioEvidencePlayer(
                    evidence = evidence,
                    isActive = activeAudioId == evidence.id,
                    onToggle = { onAudioToggle(evidence.id) },
                    onFinished = onAudioFinished,
                )
            }
        }
    }
}

@Composable
private fun MediaHeading(icon: ImageVector, title: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
        AnatomyText(
            text = "$title · $count",
            style = MaterialTheme.typography.labelLarge,
            properties = AnatomyTextProperties(fontWeight = FontWeight.SemiBold),
        )
    }
}

@Composable
private fun AudioEvidencePlayer(
    evidence: CardEvidence,
    isActive: Boolean,
    onToggle: () -> Unit,
    onFinished: () -> Unit,
) {
    var prepared by remember(evidence.url) { mutableStateOf(false) }
    var failed by remember(evidence.url) { mutableStateOf(false) }
    var duration by remember(evidence.url) { mutableIntStateOf(0) }
    var position by remember(evidence.url) { mutableIntStateOf(0) }
    val latestActive by rememberUpdatedState(isActive)
    val latestFinished by rememberUpdatedState(onFinished)
    val player = remember(evidence.url) { MediaPlayer() }

    DisposableEffect(player, evidence.url) {
        runCatching {
            player.setDataSource(evidence.url)
            player.setOnPreparedListener {
                prepared = true
                duration = it.duration.coerceAtLeast(0)
                if (latestActive) it.start()
            }
            player.setOnCompletionListener {
                position = 0
                latestFinished()
            }
            player.setOnErrorListener { _, _, _ ->
                failed = true
                latestFinished()
                true
            }
            player.prepareAsync()
        }.onFailure { failed = true }
        onDispose { player.release() }
    }

    LaunchedEffect(isActive, prepared) {
        if (!prepared || failed) return@LaunchedEffect
        if (isActive) player.start() else if (player.isPlaying) player.pause()
    }
    LaunchedEffect(isActive, prepared) {
        while (isActive && prepared && !failed) {
            position = runCatching { player.currentPosition }.getOrDefault(position)
            delay(350)
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onToggle, enabled = prepared && !failed) {
                    Icon(
                        if (isActive) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = stringResource(if (isActive) R.string.card_detail_pause_audio else R.string.card_detail_play_audio),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    AnatomyText(
                        text = evidence.url.substringAfterLast('/').ifBlank { evidence.typeCode },
                        style = MaterialTheme.typography.labelMedium,
                        properties = AnatomyTextProperties(maxLines = 1, overflow = TextOverflow.Ellipsis),
                    )
                    Slider(
                        value = position.toFloat().coerceIn(0f, duration.coerceAtLeast(1).toFloat()),
                        onValueChange = {
                            position = it.toInt()
                            if (prepared) player.seekTo(position)
                        },
                        valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
                        enabled = prepared && !failed,
                    )
                }
                AnatomyText(
                    text = "${position.asDuration()} / ${duration.asDuration()}",
                    style = MaterialTheme.typography.labelSmall,
                    properties = AnatomyTextProperties(color = MaterialTheme.colorScheme.onSurfaceVariant),
                )
            }
            if (failed) {
                AnatomyText(
                    text = stringResource(R.string.card_detail_audio_error),
                    style = MaterialTheme.typography.labelSmall,
                    properties = AnatomyTextProperties(color = MaterialTheme.colorScheme.error),
                )
            }
        }
    }
}

@Composable
private fun ImagePreviewDialog(url: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.card_detail_close))
                    }
                }
                AnatomyImage(
                    source = AnatomyImageSource.Url(url),
                    contentDescription = stringResource(R.string.card_detail_image_description),
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                )
            }
        }
    }
}

private fun openMedia(context: android.content.Context, url: String, mimeType: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(url), mimeType)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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

private fun Int.asDuration(): String {
    val totalSeconds = (this / 1_000).coerceAtLeast(0)
    return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
