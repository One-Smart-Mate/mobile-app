package com.ih.osm.features.evidence

import android.media.MediaPlayer
import android.net.Uri
import android.view.ViewGroup
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.ih.osm.R
import com.ih.osm.designsystem.anatomy.AnatomyText
import com.ih.osm.designsystem.anatomy.AnatomyTextProperties
import com.ih.osm.features.card.domain.model.CardEvidenceMediaType
import java.io.File
import kotlinx.coroutines.delay

data class EvidenceMediaUiItem(
    val id: String,
    val source: String?,
    val displayName: String,
    val mediaType: CardEvidenceMediaType,
    val sizeBytes: Long = 0,
    val durationMillis: Long = 0,
    val loading: Boolean = false,
    val failed: Boolean = false,
)

/**
 * Shared evidence renderer for Create Card and Card Detail.
 * Read-only mode only hides deletion; preview and playback remain enabled.
 */
@Composable
fun EvidenceMediaGallery(
    evidences: List<EvidenceMediaUiItem>,
    isOnlyRead: Boolean,
    modifier: Modifier = Modifier,
    onDelete: (EvidenceMediaUiItem) -> Unit = {},
    onRequestSource: (EvidenceMediaUiItem) -> Unit = {},
) {
    var previewImage by remember { mutableStateOf<String?>(null) }
    var previewVideo by remember { mutableStateOf<String?>(null) }
    var pendingPreviewId by remember { mutableStateOf<String?>(null) }
    var activeAudioId by remember { mutableStateOf<String?>(null) }
    var pendingAudioId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(evidences, pendingPreviewId) {
        val item = evidences.firstOrNull { it.id == pendingPreviewId }
        val source = item?.source
        if (source != null) {
            pendingPreviewId = null
            when (item.mediaType) {
                CardEvidenceMediaType.IMAGE -> previewImage = source
                CardEvidenceMediaType.VIDEO -> previewVideo = source
                CardEvidenceMediaType.AUDIO -> Unit
            }
        }
    }
    LaunchedEffect(evidences, pendingAudioId) {
        val item = evidences.firstOrNull { it.id == pendingAudioId }
        if (item?.source != null) {
            pendingAudioId = null
            activeAudioId = item.id
        }
    }
    LaunchedEffect(evidences) {
        if (activeAudioId != null && evidences.none { it.id == activeAudioId }) activeAudioId = null
    }

    val images = evidences.filter { it.mediaType == CardEvidenceMediaType.IMAGE }
    val videos = evidences.filter { it.mediaType == CardEvidenceMediaType.VIDEO }
    val audios = evidences.filter { it.mediaType == CardEvidenceMediaType.AUDIO }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (images.isNotEmpty()) {
            MediaHeading(Icons.Outlined.Image, stringResource(R.string.card_detail_images), images.size)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(images, key = EvidenceMediaUiItem::id) { evidence ->
                    ImageEvidenceItem(
                        evidence = evidence,
                        isOnlyRead = isOnlyRead,
                        onOpen = {
                            evidence.source?.let { previewImage = it } ?: run {
                                pendingPreviewId = evidence.id
                                onRequestSource(evidence)
                            }
                        },
                        onDelete = { onDelete(evidence) },
                    )
                }
            }
        }

        if (videos.isNotEmpty()) {
            MediaHeading(Icons.Outlined.Videocam, stringResource(R.string.card_detail_videos), videos.size)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(videos, key = EvidenceMediaUiItem::id) { evidence ->
                    VideoEvidenceItem(
                        evidence = evidence,
                        isOnlyRead = isOnlyRead,
                        onOpen = {
                            evidence.source?.let { previewVideo = it } ?: run {
                                pendingPreviewId = evidence.id
                                onRequestSource(evidence)
                            }
                        },
                        onDelete = { onDelete(evidence) },
                    )
                }
            }
        }

        if (audios.isNotEmpty()) {
            MediaHeading(Icons.Outlined.AudioFile, stringResource(R.string.card_detail_audios), audios.size)
            audios.forEach { evidence ->
                AudioEvidenceItem(
                    evidence = evidence,
                    isOnlyRead = isOnlyRead,
                    isActive = activeAudioId == evidence.id,
                    onToggle = {
                        if (activeAudioId == evidence.id) {
                            activeAudioId = null
                        } else if (evidence.source != null) {
                            activeAudioId = evidence.id
                        } else {
                            pendingAudioId = evidence.id
                            onRequestSource(evidence)
                        }
                    },
                    onFinished = { activeAudioId = null },
                    onDelete = { onDelete(evidence) },
                )
            }
        }
    }

    previewImage?.let { ImagePreviewDialog(it) { previewImage = null } }
    previewVideo?.let { VideoPreviewDialog(it) { previewVideo = null } }
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
private fun ImageEvidenceItem(
    evidence: EvidenceMediaUiItem,
    isOnlyRead: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.width(190.dp).aspectRatio(1.35f).clickable(onClick = onOpen),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            evidence.source?.let {
                AsyncImage(
                    model = Uri.fromFile(File(it)),
                    contentDescription = stringResource(R.string.card_detail_image_description),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            EvidenceStatus(evidence, Icons.Outlined.Image)
            DeleteButton(isOnlyRead, onDelete)
        }
    }
}

@Composable
private fun VideoEvidenceItem(
    evidence: EvidenceMediaUiItem,
    isOnlyRead: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.width(190.dp).aspectRatio(1.5f),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            evidence.source?.let { VideoThumbnail(it) }
            Box(Modifier.fillMaxSize().clickable(onClick = onOpen))
            if (!evidence.loading && !evidence.failed) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)) {
                    Icon(
                        Icons.Outlined.PlayArrow,
                        contentDescription = stringResource(R.string.card_detail_play_video),
                        modifier = Modifier.padding(8.dp).size(22.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
            EvidenceStatus(evidence, Icons.Outlined.Videocam)
            DeleteButton(isOnlyRead, onDelete)
        }
    }
}

@Composable
private fun EvidenceStatus(evidence: EvidenceMediaUiItem, placeholder: ImageVector) {
    when {
        evidence.loading -> CircularProgressIndicator(Modifier.size(28.dp))
        evidence.failed -> AnatomyText(
            text = stringResource(R.string.card_detail_retry_evidence),
            style = MaterialTheme.typography.labelSmall,
            properties = AnatomyTextProperties(color = MaterialTheme.colorScheme.error),
        )
        evidence.source == null -> Icon(placeholder, contentDescription = null, modifier = Modifier.size(30.dp))
    }
}

@Composable
private fun DeleteButton(isOnlyRead: Boolean, onDelete: () -> Unit) {
    if (!isOnlyRead) {
        Box(Modifier.fillMaxSize()) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), CircleShape),
            ) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = stringResource(R.string.create_card_remove_evidence),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun VideoThumbnail(path: String) {
    val context = LocalContext.current
    val player = remember(path) {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
            prepare()
            playWhenReady = false
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                this.player = player
                useController = false
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
        },
        update = { it.player = player },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun AudioEvidenceItem(
    evidence: EvidenceMediaUiItem,
    isOnlyRead: Boolean,
    isActive: Boolean,
    onToggle: () -> Unit,
    onFinished: () -> Unit,
    onDelete: () -> Unit,
) {
    var prepared by remember(evidence.source) { mutableStateOf(false) }
    var failed by remember(evidence.source) { mutableStateOf(false) }
    var duration by remember(evidence.source) { mutableIntStateOf(0) }
    var position by remember(evidence.source) { mutableIntStateOf(0) }
    val latestActive by rememberUpdatedState(isActive)
    val latestFinished by rememberUpdatedState(onFinished)
    val player = remember(evidence.source) { MediaPlayer() }

    DisposableEffect(player, evidence.source) {
        val source = evidence.source
        if (source == null) return@DisposableEffect onDispose { player.release() }
        runCatching {
            player.setDataSource(source)
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
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = onToggle,
                    enabled = (!evidence.loading && evidence.source == null) || (prepared && !failed),
                ) {
                    Icon(
                        if (isActive) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = stringResource(
                            if (isActive) R.string.card_detail_pause_audio else R.string.card_detail_play_audio,
                        ),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    AnatomyText(
                        text = evidence.displayName,
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
                if (!isOnlyRead) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Outlined.DeleteOutline,
                            contentDescription = stringResource(R.string.create_card_remove_evidence),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            if (evidence.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (failed || evidence.failed) {
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
private fun ImagePreviewDialog(path: String, onDismiss: () -> Unit) {
    var scale by remember(path) { mutableFloatStateOf(1f) }
    var offsetX by remember(path) { mutableFloatStateOf(0f) }
    var offsetY by remember(path) { mutableFloatStateOf(0f) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = Uri.fromFile(File(path)),
                    contentDescription = stringResource(R.string.card_detail_image_description),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().pointerInput(path) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offsetX = if (scale == 1f) 0f else offsetX + pan.x
                            offsetY = if (scale == 1f) 0f else offsetY + pan.y
                        }
                    }.graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY,
                    ),
                )
                CloseButton(onDismiss)
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
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        setBackgroundColor(android.graphics.Color.BLACK)
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
            CloseButton(onDismiss)
        }
    }
}

@Composable
private fun CloseButton(onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                .background(Color.Black.copy(alpha = 0.6f), CircleShape),
        ) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = stringResource(R.string.card_detail_close),
                tint = Color.White,
            )
        }
    }
}

private fun Int.asDuration(): String {
    val totalSeconds = (this / 1_000).coerceAtLeast(0)
    return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
