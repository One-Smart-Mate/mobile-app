package com.ih.osm.features.createcard

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DatePicker
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import com.ih.osm.BuildConfig
import com.ih.osm.R
import com.ih.osm.designsystem.anatomy.AnatomyBanner
import com.ih.osm.designsystem.anatomy.AnatomyBannerType
import com.ih.osm.designsystem.anatomy.AnatomyButton
import com.ih.osm.designsystem.anatomy.AnatomyButtonStyle
import com.ih.osm.designsystem.anatomy.AnatomyCard
import com.ih.osm.designsystem.anatomy.AnatomyCardStyle
import com.ih.osm.designsystem.anatomy.AnatomyText
import com.ih.osm.designsystem.anatomy.AnatomyTextField
import com.ih.osm.designsystem.anatomy.AnatomyTextProperties
import com.ih.osm.features.auth.domain.model.AuthenticatedUser
import com.ih.osm.features.card.domain.create.CreateCardSelectionItem
import com.ih.osm.features.card.domain.create.CreateCardEvidenceDraft
import com.ih.osm.features.card.domain.create.CreateCardEvidenceErrorReason
import com.ih.osm.features.card.domain.create.CreateCardSheet
import com.ih.osm.features.card.domain.create.CreateCardState
import com.ih.osm.features.card.domain.create.CreateCardStep
import com.ih.osm.features.card.domain.create.CreateCardValidationError
import com.ih.osm.features.card.domain.model.CardEvidenceMediaType
import com.ih.osm.features.createcard.domain.storage.EvidenceStorage
import com.ih.osm.features.createcard.domain.storage.PendingEvidenceCapture
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import androidx.compose.material3.rememberDatePickerState

@Composable
fun CreateCardScreenRoute(
    user: AuthenticatedUser,
    siteId: Long?,
    onFinished: () -> Unit,
    viewModel: CreateCardViewModel = koinViewModel(),
    evidenceStorage: EvidenceStorage = koinInject(),
) {
    val state by viewModel.getStateFlow().collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sourceType by remember { mutableStateOf<CardEvidenceMediaType?>(null) }
    var pendingCapture by remember { mutableStateOf<PendingEvidenceCapture?>(null) }
    var permissionAction by remember { mutableStateOf<EvidenceCaptureAction?>(null) }
    var grantedAction by remember { mutableStateOf<EvidenceCaptureAction?>(null) }
    var showAudioRecorder by remember { mutableStateOf(false) }
    var recordingStartedAt by remember { mutableStateOf<Long?>(null) }
    var recordingSeconds by remember { mutableStateOf(0L) }

    fun processEvidence(block: suspend () -> CreateCardEvidenceDraft) {
        scope.launch {
            viewModel.process(CreateCardViewModel.Action.EvidenceProcessing(true))
            runCatching { block() }
                .onSuccess { viewModel.process(CreateCardViewModel.Action.AddEvidence(it)) }
                .onFailure { viewModel.process(CreateCardViewModel.Action.EvidenceImportFailed) }
        }
    }

    val photoCaptureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val capture = pendingCapture
        pendingCapture = null
        if (success && capture != null) processEvidence { evidenceStorage.finishCapture(capture) }
        else evidenceStorage.discard(capture)
    }
    val videoCaptureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { success ->
        val capture = pendingCapture
        pendingCapture = null
        if (success && capture != null) processEvidence { evidenceStorage.finishCapture(capture) }
        else evidenceStorage.discard(capture)
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { processEvidence { evidenceStorage.import(it, CardEvidenceMediaType.IMAGE) } }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { processEvidence { evidenceStorage.import(it, CardEvidenceMediaType.VIDEO) } }
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { processEvidence { evidenceStorage.import(it, CardEvidenceMediaType.AUDIO) } }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val requested = permissionAction
        permissionAction = null
        if (granted) grantedAction = requested
        else viewModel.process(CreateCardViewModel.Action.EvidenceImportFailed)
    }

    fun request(action: EvidenceCaptureAction) {
        val permission = when (action) {
            EvidenceCaptureAction.TAKE_PHOTO,
            EvidenceCaptureAction.RECORD_VIDEO,
            -> Manifest.permission.CAMERA
            EvidenceCaptureAction.RECORD_AUDIO -> Manifest.permission.RECORD_AUDIO
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
            EvidenceCaptureAction.TAKE_PHOTO -> evidenceStorage.createCapture(CardEvidenceMediaType.IMAGE).also {
                pendingCapture = it
                photoCaptureLauncher.launch(it.uri)
            }
            EvidenceCaptureAction.RECORD_VIDEO -> evidenceStorage.createCapture(CardEvidenceMediaType.VIDEO).also {
                pendingCapture = it
                videoCaptureLauncher.launch(it.uri)
            }
            EvidenceCaptureAction.RECORD_AUDIO -> showAudioRecorder = true
            null -> Unit
        }
        grantedAction = null
    }

    LaunchedEffect(recordingStartedAt) {
        while (recordingStartedAt != null) {
            recordingSeconds = (SystemClock.elapsedRealtime() - recordingStartedAt!!) / 1_000L
            val limit = state.selectedCardType?.audiosDurationCreate ?: 0L
            if (limit > 0L && recordingSeconds >= limit) {
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

    LaunchedEffect(user.id, siteId) {
        viewModel.process(CreateCardViewModel.Action.Initialize(user, siteId, BuildConfig.VERSION_NAME))
    }
    LaunchedEffect(viewModel) {
        viewModel.getEventFlow().collectLatest { event ->
            when (event) {
                CreateCardViewModel.Event.Close,
                is CreateCardViewModel.Event.Created,
                -> onFinished()
            }
        }
    }
    BackHandler { viewModel.process(CreateCardViewModel.Action.Back) }

    CreateCardScreen(
        state = state,
        onAction = viewModel::process,
        onEvidenceAction = { sourceType = it },
        onRemoveEvidence = { viewModel.process(CreateCardViewModel.Action.RemoveEvidence(it)) },
    )

    sourceType?.let { type ->
        EvidenceSourceBottomSheet(
            mediaType = type,
            onDismiss = { sourceType = null },
            onCapture = {
                sourceType = null
                request(
                    when (type) {
                        CardEvidenceMediaType.IMAGE -> EvidenceCaptureAction.TAKE_PHOTO
                        CardEvidenceMediaType.VIDEO -> EvidenceCaptureAction.RECORD_VIDEO
                        CardEvidenceMediaType.AUDIO -> EvidenceCaptureAction.RECORD_AUDIO
                    },
                )
            },
            onSelectFile = {
                sourceType = null
                when (type) {
                    CardEvidenceMediaType.IMAGE -> imagePicker.launch("image/*")
                    CardEvidenceMediaType.VIDEO -> videoPicker.launch("video/*")
                    CardEvidenceMediaType.AUDIO -> audioPicker.launch("audio/*")
                }
            },
        )
    }
    if (showAudioRecorder) {
        AudioRecorderBottomSheet(
            isRecording = recordingStartedAt != null,
            elapsedSeconds = recordingSeconds,
            maxSeconds = state.selectedCardType?.audiosDurationCreate ?: 0L,
            onStart = {
                runCatching {
                    evidenceStorage.startAudioRecording(state.selectedCardType?.audiosDurationCreate ?: 0L)
                }.onSuccess {
                    recordingSeconds = 0
                    recordingStartedAt = SystemClock.elapsedRealtime()
                }.onFailure { viewModel.process(CreateCardViewModel.Action.EvidenceImportFailed) }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateCardScreen(
    state: CreateCardState,
    onAction: (CreateCardViewModel.Action) -> Unit,
    onEvidenceAction: (CardEvidenceMediaType) -> Unit,
    onRemoveEvidence: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    AnatomyText(
                        text = stringResource(R.string.create_card_title),
                        style = MaterialTheme.typography.titleMedium,
                        properties = AnatomyTextProperties(fontWeight = FontWeight.SemiBold),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onAction(CreateCardViewModel.Action.Back) }) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.navigation_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            CreateCardBottomActions(state = state, onAction = onAction)
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { StepIndicator(state.step) }
            state.validationError?.let { error ->
                item {
                    AnatomyBanner(
                        message = stringResource(error.messageResource()),
                        type = AnatomyBannerType.ERROR,
                        onDismiss = { onAction(CreateCardViewModel.Action.DismissError) },
                    )
                }
            }
            state.evidenceError?.let { error ->
                item {
                    AnatomyBanner(
                        message = evidenceErrorMessage(error.reason, error.limit),
                        type = AnatomyBannerType.ERROR,
                        onDismiss = { onAction(CreateCardViewModel.Action.DismissError) },
                    )
                }
            }
            when (state.step) {
                CreateCardStep.CLASSIFICATION -> classificationContent(state, onAction)
                CreateCardStep.LOCATION -> locationContent(state, onAction)
                CreateCardStep.EVIDENCE -> evidenceContent(state, onEvidenceAction, onRemoveEvidence)
                CreateCardStep.DETAILS -> detailsContent(state, onAction)
                CreateCardStep.REVIEW -> reviewContent(state)
            }
        }
    }

    if (state.activeSheet != null) {
        SelectionBottomSheet(state = state, onAction = onAction)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.classificationContent(
    state: CreateCardState,
    onAction: (CreateCardViewModel.Action) -> Unit,
) {
    item {
        SectionHeader(
            title = stringResource(R.string.create_card_classification_title),
            subtitle = stringResource(R.string.create_card_classification_subtitle),
        )
    }
    item {
        SelectionField(
            label = stringResource(R.string.create_card_type_label),
            value = state.selectedCardType?.name,
            placeholder = stringResource(R.string.create_card_type_placeholder),
            icon = Icons.Outlined.Category,
            onClick = { onAction(CreateCardViewModel.Action.OpenSheet(CreateCardSheet.CARD_TYPE)) },
        )
    }
    if (state.requiresCustomDueDate) {
        item {
            SelectionField(
                label = stringResource(R.string.create_card_custom_due_date_label),
                value = state.customDueDate,
                placeholder = stringResource(R.string.create_card_custom_due_date_placeholder),
                icon = Icons.Outlined.Flag,
                supporting = stringResource(R.string.create_card_custom_due_date_support),
                onClick = { onAction(CreateCardViewModel.Action.OpenSheet(CreateCardSheet.CUSTOM_DUE_DATE)) },
            )
        }
    }
    if (state.requiresCardTypeValue) {
        item {
            SelectionField(
                label = stringResource(R.string.create_card_condition_label),
                value = when (state.selectedCardTypeValue) {
                    "safe" -> stringResource(R.string.create_card_condition_safe)
                    "unsafe" -> stringResource(R.string.create_card_condition_unsafe)
                    else -> null
                },
                placeholder = stringResource(R.string.create_card_condition_placeholder),
                icon = Icons.Outlined.Shield,
                onClick = { onAction(CreateCardViewModel.Action.OpenSheet(CreateCardSheet.CARD_TYPE_VALUE)) },
            )
        }
    }
    item {
        SelectionField(
            label = stringResource(R.string.create_card_preclassifier_label),
            value = state.selectedPreclassifier?.let { "${it.code} · ${it.description}" },
            placeholder = stringResource(R.string.create_card_preclassifier_placeholder),
            supporting = state.selectedCardType?.let {
                stringResource(R.string.create_card_preclassifier_support, it.name)
            },
            icon = Icons.Outlined.FolderOpen,
            enabled = state.selectedCardType != null,
            onClick = { onAction(CreateCardViewModel.Action.OpenSheet(CreateCardSheet.PRECLASSIFIER)) },
        )
    }
    item {
        SelectionField(
            label = stringResource(R.string.create_card_priority_label),
            value = state.selectedPriority?.let { "${it.code} · ${it.description}" },
            placeholder = stringResource(R.string.create_card_priority_placeholder),
            icon = Icons.Outlined.Flag,
            enabled = state.selectedPreclassifier != null,
            onClick = { onAction(CreateCardViewModel.Action.OpenSheet(CreateCardSheet.PRIORITY)) },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.locationContent(
    state: CreateCardState,
    onAction: (CreateCardViewModel.Action) -> Unit,
) {
    item {
        SectionHeader(
            title = stringResource(R.string.create_card_location_title),
            subtitle = stringResource(R.string.create_card_location_subtitle),
        )
    }
    item {
        SelectionField(
            label = stringResource(R.string.create_card_level_label),
            value = state.selectedLocation.takeIf(String::isNotBlank),
            placeholder = stringResource(R.string.create_card_level_placeholder),
            supporting = state.responsibleName?.let {
                stringResource(R.string.create_card_responsible, it)
            },
            icon = Icons.Outlined.LocationOn,
            onClick = { onAction(CreateCardViewModel.Action.OpenSheet(CreateCardSheet.LEVEL)) },
        )
    }
    item {
        InfoCard(
            text = stringResource(R.string.create_card_level_helper),
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.evidenceContent(
    state: CreateCardState,
    onEvidenceAction: (CardEvidenceMediaType) -> Unit,
    onRemoveEvidence: (String) -> Unit,
) {
    val cardType = state.selectedCardType
    val imageLimit = cardType?.quantityImagesCreate ?: 0L
    val videoLimit = cardType?.quantityVideosCreate ?: 0L
    val audioLimit = cardType?.quantityAudiosCreate ?: 0L
    item {
        SectionHeader(
            title = stringResource(R.string.create_card_evidence_title),
            subtitle = stringResource(R.string.create_card_evidence_subtitle),
        )
    }
    item {
        InfoCard(
            stringResource(
                R.string.create_card_evidence_limits,
                imageLimit,
                videoLimit,
                cardType?.videosDurationCreate ?: 0,
                audioLimit,
                cardType?.audiosDurationCreate ?: 0,
            ),
        )
    }
    item {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            EvidenceAction(
                Icons.Outlined.CameraAlt,
                stringResource(R.string.create_card_photo),
                "${state.imageEvidenceCount}/$imageLimit",
                enabled = state.imageEvidenceCount < imageLimit,
                onClick = { onEvidenceAction(CardEvidenceMediaType.IMAGE) },
                modifier = Modifier.weight(1f),
            )
            EvidenceAction(
                Icons.Outlined.VideoFile,
                stringResource(R.string.create_card_video),
                "${state.videoEvidenceCount}/$videoLimit",
                enabled = state.videoEvidenceCount < videoLimit,
                onClick = { onEvidenceAction(CardEvidenceMediaType.VIDEO) },
                modifier = Modifier.weight(1f),
            )
            EvidenceAction(
                Icons.Outlined.AudioFile,
                stringResource(R.string.create_card_audio),
                "${state.audioEvidenceCount}/$audioLimit",
                enabled = state.audioEvidenceCount < audioLimit,
                onClick = { onEvidenceAction(CardEvidenceMediaType.AUDIO) },
                modifier = Modifier.weight(1f),
            )
        }
    }
    if (state.isProcessingEvidence) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                AnatomyText(
                    text = stringResource(R.string.create_card_evidence_processing),
                    style = MaterialTheme.typography.bodySmall,
                    properties = AnatomyTextProperties(color = MaterialTheme.colorScheme.onSurfaceVariant),
                )
            }
        }
    }
    if (state.evidences.isEmpty() && !state.isProcessingEvidence) {
        item { InfoCard(stringResource(R.string.create_card_no_evidence)) }
    } else {
        items(state.evidences, key = CreateCardEvidenceDraft::id) { evidence ->
            EvidenceItem(evidence = evidence, onRemove = { onRemoveEvidence(evidence.id) })
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.detailsContent(
    state: CreateCardState,
    onAction: (CreateCardViewModel.Action) -> Unit,
) {
    item {
        SectionHeader(
            title = stringResource(R.string.create_card_details_title),
            subtitle = stringResource(R.string.create_card_details_subtitle),
        )
    }
    item {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AnatomyTextField(
                value = state.description,
                onValueChange = { onAction(CreateCardViewModel.Action.DescriptionChanged(it)) },
                placeholder = stringResource(R.string.create_card_description_placeholder),
                singleLine = false,
                minLines = 4,
                maxLines = 6,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    autoCorrectEnabled = true,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Default,
                ),
            )
            AnatomyText(
                text = stringResource(
                    R.string.create_card_description_count,
                    state.description.length,
                    200,
                ),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                properties = AnatomyTextProperties(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                ),
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.reviewContent(state: CreateCardState) {
    item {
        SectionHeader(
            title = stringResource(R.string.create_card_review_title),
            subtitle = stringResource(R.string.create_card_review_subtitle),
        )
    }
    item {
        AnatomyCard(style = AnatomyCardStyle.OUTLINED) {
            ReviewRow(stringResource(R.string.create_card_type_label), state.selectedCardType?.name.orEmpty())
            ReviewRow(
                stringResource(R.string.create_card_preclassifier_label),
                state.selectedPreclassifier?.description.orEmpty(),
            )
            ReviewRow(
                stringResource(R.string.create_card_priority_label),
                state.selectedPriority?.description.orEmpty(),
            )
            ReviewRow(stringResource(R.string.create_card_level_label), state.selectedLocation)
            ReviewRow(stringResource(R.string.create_card_description_label), state.description)
            ReviewRow(
                stringResource(R.string.create_card_evidence_label),
                if (state.evidences.isEmpty()) {
                    stringResource(R.string.create_card_no_evidence)
                } else {
                    stringResource(R.string.create_card_evidence_count, state.evidences.size)
                },
            )
        }
    }
    item { InfoCard(stringResource(R.string.create_card_offline_notice)) }
}

@Composable
private fun CreateCardBottomActions(
    state: CreateCardState,
    onAction: (CreateCardViewModel.Action) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 35.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.step != CreateCardStep.CLASSIFICATION) {
                AnatomyButton(
                    text = stringResource(R.string.create_card_previous),
                    onClick = { onAction(CreateCardViewModel.Action.Back) },
                    modifier = Modifier.weight(0.42f),
                    style = AnatomyButtonStyle.SECONDARY,
                    enabled = !state.isSaving,
                )
            }
            AnatomyButton(
                text = if (state.step == CreateCardStep.REVIEW) {
                    stringResource(R.string.create_card_save)
                } else {
                    stringResource(R.string.create_card_continue)
                },
                onClick = {
                    onAction(
                        if (state.step == CreateCardStep.REVIEW) {
                            CreateCardViewModel.Action.Save
                        } else {
                            CreateCardViewModel.Action.Continue
                        },
                    )
                },
                modifier = Modifier.weight(1f),
                isLoading = state.isSaving,
                enabled = state.initialized,
            )
        }
    }
}

@Composable
private fun StepIndicator(current: CreateCardStep) {
    val labels = listOf(
        R.string.create_card_step_classify,
        R.string.create_card_step_location,
        R.string.create_card_step_evidence,
        R.string.create_card_step_details,
        R.string.create_card_step_review,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        labels.forEachIndexed { index, label ->
            val completed = index < current.ordinal
            val selected = index == current.ordinal
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Surface(
                    modifier = Modifier.size(30.dp),
                    shape = CircleShape,
                    color = if (completed || selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.dp,
                        if (completed || selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    ),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (completed) {
                            Icon(
                                Icons.Outlined.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            AnatomyText(
                                text = (index + 1).toString(),
                                style = MaterialTheme.typography.labelSmall,
                                properties = AnatomyTextProperties(
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                AnatomyText(
                    text = stringResource(label),
                    style = MaterialTheme.typography.labelSmall,
                    properties = AnatomyTextProperties(
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        AnatomyText(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            properties = AnatomyTextProperties(fontWeight = FontWeight.Bold),
        )
        AnatomyText(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            properties = AnatomyTextProperties(color = MaterialTheme.colorScheme.onSurfaceVariant),
        )
    }
}

@Composable
private fun SelectionField(
    label: String,
    value: String?,
    placeholder: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    enabled: Boolean = true,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AnatomyText(
            text = label,
            modifier = Modifier.padding(start = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            properties = AnatomyTextProperties(
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            ),
        )
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
                .clickable(enabled = enabled, onClick = onClick),
            shape = RoundedCornerShape(14.dp),
            color = if (enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                AnatomyText(
                    text = value ?: placeholder,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    properties = AnatomyTextProperties(
                        color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    ),
                )
                Icon(Icons.Outlined.ChevronRight, contentDescription = null)
            }
        }
        supporting?.let {
            AnatomyText(
                text = it,
                modifier = Modifier.padding(start = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                properties = AnatomyTextProperties(color = MaterialTheme.colorScheme.onSurfaceVariant),
            )
        }
    }
}

@Composable
private fun EvidenceAction(
    icon: ImageVector,
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
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            AnatomyText(label, style = MaterialTheme.typography.labelMedium)
            AnatomyText(
                count,
                style = MaterialTheme.typography.labelSmall,
                properties = AnatomyTextProperties(color = MaterialTheme.colorScheme.onSurfaceVariant),
            )
        }
    }
}

@Composable
private fun EvidenceItem(evidence: CreateCardEvidenceDraft, onRemove: () -> Unit) {
    val icon = when (evidence.mediaType) {
        CardEvidenceMediaType.IMAGE -> Icons.Outlined.CameraAlt
        CardEvidenceMediaType.VIDEO -> Icons.Outlined.VideoFile
        CardEvidenceMediaType.AUDIO -> Icons.Outlined.AudioFile
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                AnatomyText(
                    text = evidence.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    properties = AnatomyTextProperties(
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    ),
                )
                val size = evidence.sizeBytes / 1024L
                val duration = if (evidence.durationMillis > 0) " · ${evidence.durationMillis / 1_000L}s" else ""
                AnatomyText(
                    text = "$size KB$duration",
                    style = MaterialTheme.typography.bodySmall,
                    properties = AnatomyTextProperties(color = MaterialTheme.colorScheme.onSurfaceVariant),
                )
            }
            IconButton(onClick = onRemove) {
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
private fun InfoCard(text: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            AnatomyText(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                properties = AnatomyTextProperties(color = MaterialTheme.colorScheme.onSurfaceVariant),
            )
        }
    }
}

@Composable
private fun ReviewRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AnatomyText(
            text = label,
            modifier = Modifier.width(108.dp),
            style = MaterialTheme.typography.labelMedium,
            properties = AnatomyTextProperties(color = MaterialTheme.colorScheme.onSurfaceVariant),
        )
        AnatomyText(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            properties = AnatomyTextProperties(fontWeight = FontWeight.Medium),
        )
    }
}

private enum class EvidenceCaptureAction {
    TAKE_PHOTO,
    RECORD_VIDEO,
    RECORD_AUDIO,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EvidenceSourceBottomSheet(
    mediaType: CardEvidenceMediaType,
    onDismiss: () -> Unit,
    onCapture: () -> Unit,
    onSelectFile: () -> Unit,
) {
    val title = when (mediaType) {
        CardEvidenceMediaType.IMAGE -> stringResource(R.string.create_card_add_photo)
        CardEvidenceMediaType.VIDEO -> stringResource(R.string.create_card_add_video)
        CardEvidenceMediaType.AUDIO -> stringResource(R.string.create_card_add_audio)
    }
    val captureLabel = when (mediaType) {
        CardEvidenceMediaType.IMAGE -> stringResource(R.string.create_card_take_photo)
        CardEvidenceMediaType.VIDEO -> stringResource(R.string.create_card_record_video)
        CardEvidenceMediaType.AUDIO -> stringResource(R.string.create_card_record_audio)
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AnatomyText(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                properties = AnatomyTextProperties(fontWeight = FontWeight.Bold),
            )
            AnatomyText(
                text = stringResource(R.string.create_card_evidence_source_help),
                style = MaterialTheme.typography.bodyMedium,
                properties = AnatomyTextProperties(color = MaterialTheme.colorScheme.onSurfaceVariant),
            )
            AnatomyButton(text = captureLabel, onClick = onCapture)
            AnatomyButton(
                text = stringResource(R.string.create_card_select_file),
                onClick = onSelectFile,
                style = AnatomyButtonStyle.SECONDARY,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudioRecorderBottomSheet(
    isRecording: Boolean,
    elapsedSeconds: Long,
    maxSeconds: Long,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                Icons.Outlined.AudioFile,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            AnatomyText(
                text = stringResource(R.string.create_card_record_audio),
                style = MaterialTheme.typography.titleLarge,
                properties = AnatomyTextProperties(fontWeight = FontWeight.Bold),
            )
            AnatomyText(
                text = stringResource(
                    R.string.create_card_audio_timer,
                    elapsedSeconds,
                    maxSeconds,
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            AnatomyButton(
                text = stringResource(
                    if (isRecording) R.string.create_card_stop_recording else R.string.create_card_start_recording,
                ),
                onClick = if (isRecording) onStop else onStart,
            )
            AnatomyButton(
                text = stringResource(R.string.create_card_cancel_recording),
                onClick = onDismiss,
                style = AnatomyButtonStyle.SECONDARY,
            )
        }
    }
}

@Composable
private fun evidenceErrorMessage(reason: CreateCardEvidenceErrorReason, limit: Long?): String = when (reason) {
    CreateCardEvidenceErrorReason.IMAGE_LIMIT_REACHED ->
        stringResource(R.string.create_card_error_image_limit, limit ?: 0)
    CreateCardEvidenceErrorReason.VIDEO_LIMIT_REACHED ->
        stringResource(R.string.create_card_error_video_limit, limit ?: 0)
    CreateCardEvidenceErrorReason.AUDIO_LIMIT_REACHED ->
        stringResource(R.string.create_card_error_audio_limit, limit ?: 0)
    CreateCardEvidenceErrorReason.VIDEO_DURATION_EXCEEDED ->
        stringResource(R.string.create_card_error_video_duration, limit ?: 0)
    CreateCardEvidenceErrorReason.AUDIO_DURATION_EXCEEDED ->
        stringResource(R.string.create_card_error_audio_duration, limit ?: 0)
    CreateCardEvidenceErrorReason.FILE_TOO_LARGE ->
        stringResource(R.string.create_card_error_file_size, limit ?: 0)
    CreateCardEvidenceErrorReason.INVALID_MEDIA -> stringResource(R.string.create_card_error_invalid_media)
    CreateCardEvidenceErrorReason.TOTAL_LIMIT_REACHED ->
        stringResource(R.string.create_card_error_total_evidence, limit ?: 0)
    CreateCardEvidenceErrorReason.IMPORT_FAILED -> stringResource(R.string.create_card_error_import_evidence)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionBottomSheet(
    state: CreateCardState,
    onAction: (CreateCardViewModel.Action) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = { onAction(CreateCardViewModel.Action.DismissSheet) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.88f)
                .widthIn(max = 720.dp).align(Alignment.CenterHorizontally)
                .padding(horizontal = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnatomyText(
                    text = stringResource(state.activeSheet.sheetTitleResource()),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    properties = AnatomyTextProperties(fontWeight = FontWeight.Bold),
                )
                IconButton(onClick = { onAction(CreateCardViewModel.Action.DismissSheet) }) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.create_card_close_sheet))
                }
            }
            Spacer(Modifier.height(8.dp))
            if (state.activeSheet == CreateCardSheet.CUSTOM_DUE_DATE) {
                CustomDueDatePicker(state = state, onAction = onAction)
                return@Column
            }
            AnatomyTextField(
                value = state.sheetQuery,
                onValueChange = { onAction(CreateCardViewModel.Action.SearchChanged(it)) },
                placeholder = stringResource(R.string.create_card_search_placeholder),
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            )
            if (state.activeSheet == CreateCardSheet.LEVEL && state.sheetQuery.isBlank()) {
                Spacer(Modifier.height(12.dp))
                LevelBreadcrumbs(state, onAction)
            }
            Spacer(Modifier.height(12.dp))
            if (state.sheetItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AnatomyText(
                        text = stringResource(R.string.create_card_no_results),
                        properties = AnatomyTextProperties(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.sheetItems, key = CreateCardSelectionItem::id) { item ->
                        val localizedItem = if (state.activeSheet == CreateCardSheet.CARD_TYPE_VALUE) {
                            item.copy(
                                title = stringResource(
                                    if (item.id == "safe") {
                                        R.string.create_card_condition_safe
                                    } else {
                                        R.string.create_card_condition_unsafe
                                    },
                                ),
                            )
                        } else {
                            item
                        }
                        SheetItem(
                            item = localizedItem,
                            selected = item.id == state.selectedItemIdForActiveSheet(),
                            onClick = { onAction(CreateCardViewModel.Action.SelectItem(item.id)) },
                        )
                    }
                }
            }
        }
    }
}

private fun CreateCardState.selectedItemIdForActiveSheet(): String? = when (activeSheet) {
    CreateCardSheet.CARD_TYPE -> selectedCardType?.id
    CreateCardSheet.CARD_TYPE_VALUE -> selectedCardTypeValue
    CreateCardSheet.PRECLASSIFIER -> selectedPreclassifier?.id
    CreateCardSheet.PRIORITY -> selectedPriority?.id
    CreateCardSheet.LEVEL -> selectedLevel?.id
    CreateCardSheet.CUSTOM_DUE_DATE,
    null,
    -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomDueDatePicker(
    state: CreateCardState,
    onAction: (CreateCardViewModel.Action) -> Unit,
) {
    val initialMillis = remember(state.customDueDate) {
        state.customDueDate?.let { value ->
            runCatching { LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
        }
    }
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePicker(
        state = pickerState,
        modifier = Modifier.fillMaxWidth(),
        showModeToggle = false,
    )
    Spacer(Modifier.height(8.dp))
    AnatomyButton(
        text = stringResource(R.string.create_card_use_date),
        onClick = {
            pickerState.selectedDateMillis?.let { millis ->
                val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()
                onAction(CreateCardViewModel.Action.CustomDueDateChanged(date))
            }
        },
        enabled = pickerState.selectedDateMillis != null,
        modifier = Modifier.padding(bottom = 20.dp),
    )
}

@Composable
private fun LevelBreadcrumbs(
    state: CreateCardState,
    onAction: (CreateCardViewModel.Action) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = { onAction(CreateCardViewModel.Action.NavigateLevel(null)) },
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(Icons.Outlined.Business, contentDescription = null, modifier = Modifier.size(16.dp))
                AnatomyText(stringResource(R.string.create_card_level_root), style = MaterialTheme.typography.labelMedium)
            }
        }
        state.levelNavigationPath.forEach { level ->
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp))
            Surface(
                onClick = { onAction(CreateCardViewModel.Action.NavigateLevel(level.id)) },
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                AnatomyText(
                    text = level.name,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelMedium,
                    properties = AnatomyTextProperties(maxLines = 1),
                )
            }
        }
    }
}

@Composable
private fun SheetItem(
    item: CreateCardSelectionItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                AnatomyText(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    properties = AnatomyTextProperties(fontWeight = FontWeight.SemiBold),
                )
                item.subtitle?.takeIf(String::isNotBlank)?.let {
                    Spacer(Modifier.height(2.dp))
                    AnatomyText(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        properties = AnatomyTextProperties(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                }
            }
            if (selected) {
                Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            } else if (item.hasChildren) {
                Icon(Icons.Outlined.ChevronRight, contentDescription = null)
            }
        }
    }
}

private fun CreateCardSheet?.sheetTitleResource(): Int = when (this) {
    CreateCardSheet.CARD_TYPE -> R.string.create_card_select_type
    CreateCardSheet.CARD_TYPE_VALUE -> R.string.create_card_select_condition
    CreateCardSheet.PRECLASSIFIER -> R.string.create_card_select_preclassifier
    CreateCardSheet.PRIORITY -> R.string.create_card_select_priority
    CreateCardSheet.LEVEL -> R.string.create_card_select_level
    CreateCardSheet.CUSTOM_DUE_DATE -> R.string.create_card_select_due_date
    null -> R.string.create_card_select_option
}

private fun CreateCardValidationError.messageResource(): Int = when (this) {
    CreateCardValidationError.SITE_REQUIRED -> R.string.create_card_error_site
    CreateCardValidationError.CATALOGS_UNAVAILABLE -> R.string.create_card_error_catalogs
    CreateCardValidationError.CARD_TYPE_REQUIRED -> R.string.create_card_error_type
    CreateCardValidationError.CARD_TYPE_VALUE_REQUIRED -> R.string.create_card_error_condition
    CreateCardValidationError.PRECLASSIFIER_REQUIRED -> R.string.create_card_error_preclassifier
    CreateCardValidationError.PRIORITY_REQUIRED -> R.string.create_card_error_priority
    CreateCardValidationError.LEVEL_REQUIRED -> R.string.create_card_error_level
    CreateCardValidationError.LEVEL_MUST_BE_FINAL -> R.string.create_card_error_final_level
    CreateCardValidationError.DESCRIPTION_REQUIRED -> R.string.create_card_error_description
    CreateCardValidationError.DESCRIPTION_TOO_LONG -> R.string.create_card_error_description_length
    CreateCardValidationError.CUSTOM_DUE_DATE_REQUIRED -> R.string.create_card_error_due_date
    CreateCardValidationError.CUSTOM_DUE_DATE_INVALID -> R.string.create_card_error_due_date_invalid
    CreateCardValidationError.INVALID_CATALOG_SELECTION -> R.string.create_card_error_selection
    CreateCardValidationError.SAVE_FAILED -> R.string.create_card_error_save
}
