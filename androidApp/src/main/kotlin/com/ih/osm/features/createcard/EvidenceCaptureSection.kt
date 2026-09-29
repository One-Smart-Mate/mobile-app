package com.ih.osm.features.createcard

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ih.osm.R
import com.ih.osm.designsystem.anatomy.AnatomyButton
import com.ih.osm.designsystem.anatomy.AnatomyButtonStyle
import com.ih.osm.designsystem.anatomy.AnatomyText
import com.ih.osm.designsystem.anatomy.AnatomyTextProperties
import com.ih.osm.features.card.domain.create.CreateCardEvidenceDraft
import com.ih.osm.features.card.domain.evidence.CardEvidenceLimits
import com.ih.osm.features.card.domain.model.CardEvidenceMediaType
import com.ih.osm.features.createcard.domain.storage.EvidenceStorage
import com.ih.osm.features.createcard.domain.storage.PendingEvidenceCapture
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Shared Android evidence capture UI used by card creation and card solutions.
 * File acquisition stays native while all limits are supplied by shared business logic.
 */
@Composable
fun EvidenceCaptureSection(
    evidences: List<CreateCardEvidenceDraft>,
    limits: CardEvidenceLimits,
    isProcessing: Boolean,
    onProcessingChanged: (Boolean) -> Unit,
    onEvidenceAdded: (CreateCardEvidenceDraft) -> Unit,
    onEvidenceRemoved: (String) -> Unit,
    onImportFailed: () -> Unit,
    modifier: Modifier = Modifier,
    evidenceStorage: EvidenceStorage = koinInject(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingCapture by remember { mutableStateOf<PendingEvidenceCapture?>(null) }
    var permissionAction by remember { mutableStateOf<SharedEvidenceCaptureAction?>(null) }
    var grantedAction by remember { mutableStateOf<SharedEvidenceCaptureAction?>(null) }
    var showAudioRecorder by remember { mutableStateOf(false) }
    var recordingStartedAt by remember { mutableStateOf<Long?>(null) }
    var recordingSeconds by remember { mutableStateOf(0L) }

    fun processEvidence(block: suspend () -> CreateCardEvidenceDraft) {
        scope.launch {
            onProcessingChanged(true)
            runCatching { block() }
                .onSuccess(onEvidenceAdded)
                .onFailure { onImportFailed() }
        }
    }

    val photoCapture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val capture = pendingCapture
        pendingCapture = null
        if (success && capture != null) processEvidence { evidenceStorage.finishCapture(capture) }
        else evidenceStorage.discard(capture)
    }
    val videoCapture = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { success ->
        val capture = pendingCapture
        pendingCapture = null
        if (success && capture != null) processEvidence { evidenceStorage.finishCapture(capture) }
        else evidenceStorage.discard(capture)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = permissionAction
        permissionAction = null
        if (granted) grantedAction = action else onImportFailed()
    }

    fun request(action: SharedEvidenceCaptureAction) {
        val permission = when (action) {
            SharedEvidenceCaptureAction.TAKE_PHOTO, SharedEvidenceCaptureAction.RECORD_VIDEO -> Manifest.permission.CAMERA
            SharedEvidenceCaptureAction.RECORD_AUDIO -> Manifest.permission.RECORD_AUDIO
        }
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            grantedAction = action
        } else {
            permissionAction = action
            permissionLauncher.launch(permission)
        }
    }

    LaunchedEffect(grantedAction) {
        when (grantedAction) {
            SharedEvidenceCaptureAction.TAKE_PHOTO -> evidenceStorage.createCapture(CardEvidenceMediaType.IMAGE).also {
                pendingCapture = it
                photoCapture.launch(it.uri)
            }
            SharedEvidenceCaptureAction.RECORD_VIDEO -> evidenceStorage.createCapture(CardEvidenceMediaType.VIDEO).also {
                pendingCapture = it
                videoCapture.launch(it.uri)
            }
            SharedEvidenceCaptureAction.RECORD_AUDIO -> showAudioRecorder = true
            null -> Unit
        }
        grantedAction = null
    }

    LaunchedEffect(recordingStartedAt) {
        while (recordingStartedAt != null) {
            recordingSeconds = (SystemClock.elapsedRealtime() - recordingStartedAt!!) / 1_000L
            if (limits.audioDurationSeconds > 0L && recordingSeconds >= limits.audioDurationSeconds) {
                recordingStartedAt = null
                showAudioRecorder = false
                processEvidence { evidenceStorage.stopAudioRecording() }
                break
            }
            delay(250)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            evidenceStorage.discard(pendingCapture)
            evidenceStorage.cancelAudioRecording()
        }
    }

    val images = evidences.count { it.mediaType == CardEvidenceMediaType.IMAGE }.toLong()
    val videos = evidences.count { it.mediaType == CardEvidenceMediaType.VIDEO }.toLong()
    val audios = evidences.count { it.mediaType == CardEvidenceMediaType.AUDIO }.toLong()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        EvidenceLimitsInfo(limits)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EvidenceActionCard(
                icon = Icons.Outlined.CameraAlt,
                label = stringResource(R.string.create_card_photo),
                count = "$images/${limits.images}",
                enabled = images < limits.images && !isProcessing,
                onClick = { request(SharedEvidenceCaptureAction.TAKE_PHOTO) },
                modifier = Modifier.weight(1f),
            )
            EvidenceActionCard(
                icon = Icons.Outlined.VideoFile,
                label = stringResource(R.string.create_card_video),
                count = "$videos/${limits.videos}",
                enabled = videos < limits.videos && !isProcessing,
                onClick = { request(SharedEvidenceCaptureAction.RECORD_VIDEO) },
                modifier = Modifier.weight(1f),
            )
            EvidenceActionCard(
                icon = Icons.Outlined.AudioFile,
                label = stringResource(R.string.create_card_audio),
                count = "$audios/${limits.audios}",
                enabled = audios < limits.audios && !isProcessing,
                onClick = { request(SharedEvidenceCaptureAction.RECORD_AUDIO) },
                modifier = Modifier.weight(1f),
            )
        }
        if (isProcessing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            AnatomyText(
                text = stringResource(R.string.create_card_evidence_processing),
                style = MaterialTheme.typography.bodySmall,
                properties = AnatomyTextProperties(color = MaterialTheme.colorScheme.onSurfaceVariant),
            )
        }
        if (evidences.isEmpty() && !isProcessing) {
            EvidenceInfoCard(stringResource(R.string.create_card_no_evidence))
        } else {
            evidences.forEach { evidence ->
                EvidenceDraftItem(evidence) { onEvidenceRemoved(evidence.id) }
            }
        }
    }

    if (showAudioRecorder) {
        EvidenceAudioRecorderSheet(
            isRecording = recordingStartedAt != null,
            elapsedSeconds = recordingSeconds,
            maxSeconds = limits.audioDurationSeconds,
            onStart = {
                runCatching { evidenceStorage.startAudioRecording(limits.audioDurationSeconds) }
                    .onSuccess {
                        recordingSeconds = 0
                        recordingStartedAt = SystemClock.elapsedRealtime()
                    }
                    .onFailure { onImportFailed() }
            },
            onStop = {
                recordingStartedAt = null
                showAudioRecorder = false
                processEvidence { evidenceStorage.stopAudioRecording() }
            },
            onDismiss = {
                recordingStartedAt = null
                showAudioRecorder = false
                evidenceStorage.cancelAudioRecording()
            },
        )
    }
}

private enum class SharedEvidenceCaptureAction { TAKE_PHOTO, RECORD_VIDEO, RECORD_AUDIO }

@Composable
private fun EvidenceLimitsInfo(limits: CardEvidenceLimits) {
    EvidenceInfoCard(
        stringResource(
            R.string.create_card_evidence_limits,
            limits.images,
            limits.videos,
            limits.videoDurationSeconds,
            limits.audios,
            limits.audioDurationSeconds,
        ),
    )
}

@Composable
private fun EvidenceInfoCard(text: String) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .45f)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Outlined.Info, null, modifier = Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
            AnatomyText(
                text,
                style = MaterialTheme.typography.bodySmall,
                properties = AnatomyTextProperties(color = MaterialTheme.colorScheme.onSurfaceVariant),
            )
        }
    }
}

@Composable
private fun EvidenceActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    count: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(108.dp).clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            AnatomyText(label, style = MaterialTheme.typography.labelMedium)
            AnatomyText(count, style = MaterialTheme.typography.labelSmall, properties = AnatomyTextProperties(color = MaterialTheme.colorScheme.onSurfaceVariant))
        }
    }
}

@Composable
private fun EvidenceDraftItem(evidence: CreateCardEvidenceDraft, onRemove: () -> Unit) {
    val icon = when (evidence.mediaType) {
        CardEvidenceMediaType.IMAGE -> Icons.Outlined.CameraAlt
        CardEvidenceMediaType.VIDEO -> Icons.Outlined.VideoFile
        CardEvidenceMediaType.AUDIO -> Icons.Outlined.AudioFile
    }
    Surface(shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                AnatomyText(
                    evidence.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    properties = AnatomyTextProperties(fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis),
                )
                val duration = if (evidence.durationMillis > 0) " · ${evidence.durationMillis / 1_000L}s" else ""
                AnatomyText(
                    "${evidence.sizeBytes / 1024L} KB$duration",
                    style = MaterialTheme.typography.bodySmall,
                    properties = AnatomyTextProperties(color = MaterialTheme.colorScheme.onSurfaceVariant),
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.create_card_remove_evidence), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EvidenceAudioRecorderSheet(
    isRecording: Boolean,
    elapsedSeconds: Long,
    maxSeconds: Long,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(Icons.Outlined.AudioFile, null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
            AnatomyText(stringResource(R.string.create_card_record_audio), style = MaterialTheme.typography.titleLarge, properties = AnatomyTextProperties(fontWeight = FontWeight.Bold))
            AnatomyText(stringResource(R.string.create_card_audio_timer, elapsedSeconds, maxSeconds), style = MaterialTheme.typography.titleMedium)
            AnatomyButton(stringResource(if (isRecording) R.string.create_card_stop_recording else R.string.create_card_start_recording), onClick = if (isRecording) onStop else onStart)
            AnatomyButton(stringResource(R.string.create_card_cancel_recording), onClick = onDismiss, style = AnatomyButtonStyle.SECONDARY)
        }
    }
}
