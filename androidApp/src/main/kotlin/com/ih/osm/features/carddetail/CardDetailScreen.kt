package com.ih.osm.features.carddetail

import android.media.MediaPlayer
import android.net.Uri
import android.view.ViewGroup
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
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Note
import androidx.compose.material.icons.outlined.AddAlert
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Note
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
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
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File
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
        onPrepareEvidence = viewModel::prepareEvidence,
        onRetryEvidence = viewModel::resolveEvidence,
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
    var previewImage by remember { mutableStateOf<String?>(null) }
    var previewVideo by remember { mutableStateOf<String?>(null) }
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
                            onPreviewImage = { previewImage = it },
                            onPreviewVideo = { previewVideo = it },
                        )
                    }
                }
            }
        }
    }

    previewImage?.let { url ->
        ImagePreviewDialog(url = url, onDismiss = { previewImage = null })
    }
    previewVideo?.let { path ->
        VideoPreviewDialog(path = path, onDismiss = { previewVideo = null })
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
    onPreviewImage: (String) -> Unit,
    onPreviewVideo: (String) -> Unit,
) {
    var activeAudioId by remember { mutableStateOf<String?>(null) }
    var pendingAudioId by remember { mutableStateOf<String?>(null) }
    var pendingVideoId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pendingVideoId, evidenceFiles) {
        pendingVideoId?.let { id ->
            evidenceFiles[id]?.let { path ->
                pendingVideoId = null
                onPreviewVideo(path)
            }
        }
    }
    LaunchedEffect(pendingAudioId, evidenceFiles) {
        pendingAudioId?.let { id ->
            if (evidenceFiles[id] != null) {
                pendingAudioId = null
                activeAudioId = id
            }
        }
    }
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
                    onAudioToggle = { evidence ->
                        if (activeAudioId == evidence.id) {
                            activeAudioId = null
                        } else if (evidenceFiles[evidence.id] != null) {
                            activeAudioId = evidence.id
                        } else {
                            pendingAudioId = evidence.id
                            onRetry(evidence)
                        }
                    },
                    onAudioFinished = { activeAudioId = null },
                    evidenceFiles = evidenceFiles,
                    loadingEvidenceIds = loadingEvidenceIds,
                    failedEvidenceIds = failedEvidenceIds,
                    onRetry = onRetry,
                    onImageClick = onPreviewImage,
                    onVideoClick = { evidence ->
                        evidenceFiles[evidence.id]?.let(onPreviewVideo) ?: run {
                            pendingVideoId = evidence.id
                            onRetry(evidence)
                        }
                    },
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
    onAudioToggle: (CardEvidence) -> Unit,
    onAudioFinished: () -> Unit,
    evidenceFiles: Map<String, String>,
    loadingEvidenceIds: Set<String>,
    failedEvidenceIds: Set<String>,
    onRetry: (CardEvidence) -> Unit,
    onImageClick: (String) -> Unit,
    onVideoClick: (CardEvidence) -> Unit,
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
                        val localPath = evidenceFiles[evidence.id]
                        Surface(
                            onClick = {
                                if (localPath != null) onImageClick(localPath) else onRetry(evidence)
                            },
                            modifier = Modifier.weight(1f).aspectRatio(1.35f),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            EvidenceThumbnail(
                                evidence = evidence,
                                localPath = localPath,
                                loading = evidence.id in loadingEvidenceIds,
                                failed = evidence.id in failedEvidenceIds,
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
                        val localPath = evidenceFiles[evidence.id]
                        Surface(
                            onClick = {
                                onVideoClick(evidence)
                            },
                            modifier = Modifier.weight(1f).aspectRatio(1.5f),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Videocam, contentDescription = null, modifier = Modifier.size(32.dp))
                                when {
                                    evidence.id in loadingEvidenceIds -> CircularProgressIndicator(Modifier.size(28.dp))
                                    evidence.id in failedEvidenceIds -> AnatomyText(
                                        stringResource(R.string.card_detail_retry_evidence),
                                        style = MaterialTheme.typography.labelSmall,
                                        properties = AnatomyTextProperties(color = MaterialTheme.colorScheme.error),
                                    )
                                    else -> Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primary) {
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
                    localPath = evidenceFiles[evidence.id],
                    loading = evidence.id in loadingEvidenceIds,
                    failedToLoad = evidence.id in failedEvidenceIds,
                    isActive = activeAudioId == evidence.id,
                    onToggle = { onAudioToggle(evidence) },
                    onFinished = onAudioFinished,
                )
            }
        }
    }
}

@Composable
private fun EvidenceThumbnail(
    evidence: CardEvidence,
    localPath: String?,
    loading: Boolean,
    failed: Boolean,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (localPath != null) {
            AnatomyImage(
                source = AnatomyImageSource.Url(Uri.fromFile(File(localPath)).toString()),
                contentDescription = stringResource(R.string.card_detail_image_description),
                modifier = Modifier.fillMaxSize(),
            )
        }
        when {
            loading -> CircularProgressIndicator(Modifier.size(28.dp))
            failed -> AnatomyText(
                stringResource(R.string.card_detail_retry_evidence),
                style = MaterialTheme.typography.labelSmall,
                properties = AnatomyTextProperties(color = MaterialTheme.colorScheme.error),
            )
            localPath == null -> Icon(Icons.Outlined.Image, contentDescription = null)
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
    localPath: String?,
    loading: Boolean,
    failedToLoad: Boolean,
    isActive: Boolean,
    onToggle: () -> Unit,
    onFinished: () -> Unit,
) {
    var prepared by remember(localPath) { mutableStateOf(false) }
    var failed by remember(localPath) { mutableStateOf(false) }
    var duration by remember(localPath) { mutableIntStateOf(0) }
    var position by remember(localPath) { mutableIntStateOf(0) }
    val latestActive by rememberUpdatedState(isActive)
    val latestFinished by rememberUpdatedState(onFinished)
    val player = remember(localPath) { MediaPlayer() }

    DisposableEffect(player, localPath) {
        if (localPath == null) return@DisposableEffect onDispose { player.release() }
        runCatching {
            player.setDataSource(localPath)
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
                IconButton(onClick = onToggle, enabled = (!loading && localPath == null) || (prepared && !failed)) {
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
            if (loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (failed || failedToLoad) {
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
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black,
        ) {
            Box(Modifier.fillMaxSize()) {
                AnatomyImage(
                    source = AnatomyImageSource.Url(Uri.fromFile(File(url)).toString()),
                    contentDescription = stringResource(R.string.card_detail_image_description),
                    contentScale = ContentScale.Fit,
                    shape = MaterialTheme.shapes.extraSmall,
                    modifier = Modifier.fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                offsetX = if (scale == 1f) 0f else offsetX + pan.x
                                offsetY = if (scale == 1f) 0f else offsetY + pan.y
                            }
                        }
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY,
                        ),
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.card_detail_close),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoPreviewDialog(path: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val player = remember(path) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(Modifier.fillMaxSize()) {
            AndroidView(
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        this.player = player
                        useController = true
                        keepScreenOn = true
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    }
                },
                update = { it.player = player },
                modifier = Modifier.fillMaxSize(),
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.card_detail_close),
                    tint = Color.White,
                )
            }
        }
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
