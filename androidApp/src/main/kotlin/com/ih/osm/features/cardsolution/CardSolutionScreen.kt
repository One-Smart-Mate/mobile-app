package com.ih.osm.features.cardsolution

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ih.osm.R
import com.ih.osm.designsystem.anatomy.AnatomyBanner
import com.ih.osm.designsystem.anatomy.AnatomyBannerType
import com.ih.osm.designsystem.anatomy.AnatomyButton
import com.ih.osm.designsystem.anatomy.AnatomyText
import com.ih.osm.designsystem.anatomy.AnatomyTextField
import com.ih.osm.designsystem.anatomy.AnatomyTextProperties
import com.ih.osm.features.auth.domain.model.AuthenticatedUser
import com.ih.osm.features.card.domain.solution.CardSolutionState
import com.ih.osm.features.card.domain.solution.CardSolutionType
import com.ih.osm.features.card.domain.solution.CardSolutionValidationError
import com.ih.osm.features.createcard.EvidenceCaptureSection
import com.ih.osm.features.createcard.evidenceErrorMessage
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@Composable
fun CardSolutionScreenRoute(
    user: AuthenticatedUser,
    cardUuid: String,
    type: CardSolutionType,
    onFinished: () -> Unit,
    viewModel: CardSolutionViewModel = koinViewModel(),
) {
    val state by viewModel.getStateFlow().collectAsStateWithLifecycle()
    LaunchedEffect(user.id, cardUuid, type) {
        viewModel.process(CardSolutionViewModel.Action.Initialize(user, cardUuid, type))
    }
    LaunchedEffect(viewModel) {
        viewModel.getEventFlow().collectLatest { onFinished() }
    }
    BackHandler { viewModel.process(CardSolutionViewModel.Action.Back) }
    CardSolutionScreen(state = state, onAction = viewModel::process)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CardSolutionScreen(
    state: CardSolutionState,
    onAction: (CardSolutionViewModel.Action) -> Unit,
) {
    val provisional = state.type == CardSolutionType.PROVISIONAL
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    AnatomyText(
                        text = stringResource(
                            if (provisional) R.string.card_solution_provisional_title
                            else R.string.card_solution_definitive_title,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        properties = AnatomyTextProperties(fontWeight = FontWeight.SemiBold),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onAction(CardSolutionViewModel.Action.Back) }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.navigation_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
                AnatomyButton(
                    text = stringResource(
                        if (provisional) R.string.card_solution_save_provisional
                        else R.string.card_solution_save_definitive,
                    ),
                    onClick = { onAction(CardSolutionViewModel.Action.Save) },
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 30.dp),
                    isLoading = state.isSaving,
                    enabled = state.initialized && state.validationError !in setOf(
                        CardSolutionValidationError.CARD_NOT_FOUND,
                        CardSolutionValidationError.CARD_ALREADY_CLOSED,
                        CardSolutionValidationError.SOLUTION_ALREADY_APPLIED,
                        CardSolutionValidationError.CARD_TYPE_UNAVAILABLE,
                    ) && !state.isProcessingEvidence,
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AnatomyText(
                        text = stringResource(
                            if (provisional) R.string.card_solution_provisional_heading
                            else R.string.card_solution_definitive_heading,
                        ),
                        style = MaterialTheme.typography.headlineSmall,
                        properties = AnatomyTextProperties(fontWeight = FontWeight.Bold),
                    )
                    AnatomyText(
                        text = stringResource(
                            if (provisional) R.string.card_solution_provisional_subtitle
                            else R.string.card_solution_definitive_subtitle,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        properties = AnatomyTextProperties(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                }
            }
            state.validationError?.let { error ->
                item {
                    AnatomyBanner(
                        message = stringResource(error.messageResource()),
                        type = AnatomyBannerType.ERROR,
                        onDismiss = { onAction(CardSolutionViewModel.Action.DismissError) },
                    )
                }
            }
            state.evidenceError?.let { error ->
                item {
                    AnatomyBanner(
                        message = evidenceErrorMessage(error.reason, error.limit),
                        type = AnatomyBannerType.ERROR,
                        onDismiss = { onAction(CardSolutionViewModel.Action.DismissError) },
                    )
                }
            }
            item {
                SolutionCardSummary(state)
            }
            item {
                EmployeeField(
                    value = state.selectedEmployee?.name,
                    onClick = { onAction(CardSolutionViewModel.Action.OpenEmployeeSheet) },
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AnatomyTextField(
                        value = state.comments,
                        onValueChange = { onAction(CardSolutionViewModel.Action.CommentsChanged(it)) },
                        label = stringResource(R.string.card_solution_comments_label),
                        placeholder = stringResource(R.string.card_solution_comments_placeholder),
                        singleLine = false,
                        minLines = 4,
                        maxLines = 7,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            keyboardType = KeyboardType.Text,
                        ),
                    )
                    AnatomyText(
                        text = stringResource(R.string.card_solution_comments_count, state.comments.length, 500),
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall,
                        properties = AnatomyTextProperties(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                        ),
                    )
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AnatomyText(
                        stringResource(R.string.card_solution_evidence_title),
                        style = MaterialTheme.typography.titleLarge,
                        properties = AnatomyTextProperties(fontWeight = FontWeight.Bold),
                    )
                    AnatomyText(
                        stringResource(R.string.card_solution_evidence_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        properties = AnatomyTextProperties(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                }
            }
            item {
                EvidenceCaptureSection(
                    evidences = state.evidences,
                    limits = state.evidenceLimits,
                    isProcessing = state.isProcessingEvidence,
                    onProcessingChanged = { onAction(CardSolutionViewModel.Action.EvidenceProcessing(it)) },
                    onEvidenceAdded = { onAction(CardSolutionViewModel.Action.AddEvidence(it)) },
                    onEvidenceRemoved = { onAction(CardSolutionViewModel.Action.RemoveEvidence(it)) },
                    onImportFailed = { onAction(CardSolutionViewModel.Action.EvidenceImportFailed) },
                )
            }
            item {
                AnatomyBanner(
                    message = stringResource(R.string.card_solution_offline_notice),
                    type = AnatomyBannerType.INFO,
                )
            }
        }
    }
    if (state.showEmployeeSheet) EmployeeSheet(state, onAction)
}

@Composable
private fun SolutionCardSummary(state: CardSolutionState) {
    val card = state.card ?: return
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            AnatomyText(
                text = if (card.siteCardId > 0) "#${card.siteCardId}" else stringResource(R.string.cards_local_folio),
                style = MaterialTheme.typography.labelLarge,
                properties = AnatomyTextProperties(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold),
            )
            AnatomyText(
                text = card.problemDescription,
                style = MaterialTheme.typography.titleMedium,
                properties = AnatomyTextProperties(fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis),
            )
            card.location?.let {
                AnatomyText(it, style = MaterialTheme.typography.bodySmall, properties = AnatomyTextProperties(color = MaterialTheme.colorScheme.onSurfaceVariant))
            }
        }
    }
}

@Composable
private fun EmployeeField(value: String?, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AnatomyText(
            stringResource(R.string.card_solution_employee_label),
            modifier = Modifier.padding(start = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            properties = AnatomyTextProperties(fontWeight = FontWeight.SemiBold),
        )
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onClick),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Outlined.Person, null, tint = MaterialTheme.colorScheme.primary)
                AnatomyText(
                    value ?: stringResource(R.string.card_solution_employee_placeholder),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    properties = AnatomyTextProperties(color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface),
                )
                Icon(Icons.Outlined.ChevronRight, null)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmployeeSheet(
    state: CardSolutionState,
    onAction: (CardSolutionViewModel.Action) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = { onAction(CardSolutionViewModel.Action.DismissEmployeeSheet) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(.88f).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AnatomyText(
                stringResource(R.string.card_solution_employee_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                properties = AnatomyTextProperties(fontWeight = FontWeight.Bold),
            )
            AnatomyTextField(
                value = state.employeeQuery,
                onValueChange = { onAction(CardSolutionViewModel.Action.SearchEmployee(it)) },
                placeholder = stringResource(R.string.card_solution_employee_search),
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.filteredEmployees, key = { it.id }) { employee ->
                    val selected = employee.id == state.selectedEmployee?.id
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable {
                            onAction(CardSolutionViewModel.Action.SelectEmployee(employee.id))
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Surface(modifier = Modifier.size(36.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                                Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Person, null, modifier = Modifier.size(20.dp)) }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                AnatomyText(employee.name, style = MaterialTheme.typography.bodyMedium, properties = AnatomyTextProperties(fontWeight = FontWeight.SemiBold))
                                AnatomyText(employee.email, style = MaterialTheme.typography.bodySmall, properties = AnatomyTextProperties(color = MaterialTheme.colorScheme.onSurfaceVariant))
                            }
                            if (selected) Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                if (state.filteredEmployees.isEmpty()) {
                    item {
                        AnatomyText(
                            stringResource(R.string.card_solution_employee_empty),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp),
                            properties = AnatomyTextProperties(textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant),
                        )
                    }
                }
            }
        }
    }
}

private fun CardSolutionValidationError.messageResource(): Int = when (this) {
    CardSolutionValidationError.CARD_NOT_FOUND -> R.string.card_solution_error_not_found
    CardSolutionValidationError.CARD_ALREADY_CLOSED -> R.string.card_solution_error_closed
    CardSolutionValidationError.SOLUTION_ALREADY_APPLIED -> R.string.card_solution_error_applied
    CardSolutionValidationError.CARD_TYPE_UNAVAILABLE -> R.string.card_solution_error_type
    CardSolutionValidationError.EMPLOYEE_REQUIRED -> R.string.card_solution_error_employee
    CardSolutionValidationError.COMMENTS_REQUIRED -> R.string.card_solution_error_comments
    CardSolutionValidationError.COMMENTS_TOO_LONG -> R.string.card_solution_error_comments_length
    CardSolutionValidationError.EVIDENCE_PROCESSING -> R.string.card_solution_error_evidence_processing
    CardSolutionValidationError.SAVE_FAILED -> R.string.card_solution_error_save
}
