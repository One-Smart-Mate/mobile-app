package com.ih.osm.features.createcard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DatePicker
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ih.osm.BuildConfig
import com.ih.osm.R
import com.ih.osm.designsystem.anatomy.AnatomyBanner
import com.ih.osm.designsystem.anatomy.AnatomyBannerType
import com.ih.osm.designsystem.anatomy.AnatomyButton
import com.ih.osm.designsystem.anatomy.AnatomyButtonStyle
import com.ih.osm.designsystem.anatomy.AnatomyCard
import com.ih.osm.designsystem.anatomy.AnatomyCardStyle
import com.ih.osm.designsystem.anatomy.AnatomyText
import com.ih.osm.designsystem.anatomy.AnatomyLevelBreadcrumbs
import com.ih.osm.designsystem.anatomy.AnatomySelectionBottomSheet
import com.ih.osm.designsystem.anatomy.AnatomySelectionField
import com.ih.osm.designsystem.anatomy.AnatomySelectionItem
import com.ih.osm.designsystem.anatomy.AnatomyTextField
import com.ih.osm.designsystem.anatomy.AnatomyTextProperties
import com.ih.osm.features.auth.domain.model.AuthenticatedUser
import com.ih.osm.features.card.domain.create.CreateCardEvidenceErrorReason
import com.ih.osm.features.card.domain.create.CreateCardSheet
import com.ih.osm.features.card.domain.create.CreateCardState
import com.ih.osm.features.card.domain.create.CreateCardStep
import com.ih.osm.features.card.domain.create.CreateCardValidationError
import com.ih.osm.features.card.domain.evidence.CardEvidenceLimits
import com.ih.osm.features.evidence.EvidenceMediaGallery
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel
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
) {
    val state by viewModel.getStateFlow().collectAsStateWithLifecycle()

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
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateCardScreen(
    state: CreateCardState,
    onAction: (CreateCardViewModel.Action) -> Unit,
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
                CreateCardStep.EVIDENCE -> evidenceContent(state, onAction)
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
        AnatomySelectionField(
            label = stringResource(R.string.create_card_type_label),
            value = state.selectedCardType?.name,
            placeholder = stringResource(R.string.create_card_type_placeholder),
            icon = Icons.Outlined.Category,
            onClick = { onAction(CreateCardViewModel.Action.OpenSheet(CreateCardSheet.CARD_TYPE)) },
        )
    }
    if (state.requiresCustomDueDate) {
        item {
            AnatomySelectionField(
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
            AnatomySelectionField(
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
        AnatomySelectionField(
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
        AnatomySelectionField(
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
        AnatomySelectionField(
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
    onAction: (CreateCardViewModel.Action) -> Unit,
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
        EvidenceCaptureSection(
            evidences = state.evidences,
            limits = CardEvidenceLimits(
                images = imageLimit,
                videos = videoLimit,
                audios = audioLimit,
                videoDurationSeconds = cardType?.videosDurationCreate ?: 0L,
                audioDurationSeconds = cardType?.audiosDurationCreate ?: 0L,
            ),
            isProcessing = state.isProcessingEvidence,
            onProcessingChanged = { onAction(CreateCardViewModel.Action.EvidenceProcessing(it)) },
            onEvidenceAdded = { onAction(CreateCardViewModel.Action.AddEvidence(it)) },
            onEvidenceRemoved = { onAction(CreateCardViewModel.Action.RemoveEvidence(it)) },
            onImportFailed = { onAction(CreateCardViewModel.Action.EvidenceImportFailed) },
        )
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
    if (state.evidences.isNotEmpty()) {
        item {
            EvidenceMediaGallery(
                evidences = state.evidences.map { it.toEvidenceMediaUiItem() },
                isOnlyRead = true,
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

@Composable
internal fun evidenceErrorMessage(reason: CreateCardEvidenceErrorReason, limit: Long?): String = when (reason) {
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

@Composable
private fun SelectionBottomSheet(
    state: CreateCardState,
    onAction: (CreateCardViewModel.Action) -> Unit,
) {
    AnatomySelectionBottomSheet(
        title = stringResource(state.activeSheet.sheetTitleResource()),
        query = state.sheetQuery,
        searchPlaceholder = stringResource(R.string.create_card_search_placeholder),
        items = state.sheetItems.map { item ->
            AnatomySelectionItem(
                id = item.id,
                title = if (state.activeSheet == CreateCardSheet.CARD_TYPE_VALUE) {
                    stringResource(
                        if (item.id == "safe") R.string.create_card_condition_safe
                        else R.string.create_card_condition_unsafe,
                    )
                } else item.title,
                subtitle = item.subtitle,
                hasChildren = item.hasChildren,
            )
        },
        selectedItemId = state.selectedItemIdForActiveSheet(),
        onQueryChanged = { onAction(CreateCardViewModel.Action.SearchChanged(it)) },
        onSelectItem = { onAction(CreateCardViewModel.Action.SelectItem(it)) },
        onDismiss = { onAction(CreateCardViewModel.Action.DismissSheet) },
        breadcrumbs = if (state.activeSheet == CreateCardSheet.LEVEL) {
            {
                AnatomyLevelBreadcrumbs(
                    path = state.levelNavigationPath.map { AnatomySelectionItem(it.id, it.name) },
                    onNavigate = { onAction(CreateCardViewModel.Action.NavigateLevel(it)) },
                )
            }
        } else null,
        customContent = if (state.activeSheet == CreateCardSheet.CUSTOM_DUE_DATE) {
            { CustomDueDatePicker(state, onAction) }
        } else null,
    )
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
