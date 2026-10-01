package com.ih.osm.features.opl.detail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.ih.osm.R
import com.ih.osm.designsystem.anatomy.AnatomyBanner
import com.ih.osm.designsystem.anatomy.AnatomyBannerType
import com.ih.osm.designsystem.anatomy.AnatomyButton
import com.ih.osm.designsystem.anatomy.AnatomyCard
import com.ih.osm.designsystem.anatomy.AnatomyText
import com.ih.osm.designsystem.anatomy.AnatomyTextProperties
import com.ih.osm.features.card.domain.model.CardEvidenceMediaType
import com.ih.osm.features.evidence.EvidenceMediaGallery
import com.ih.osm.features.opl.detail.components.OplPdfBottomSheet
import com.ih.osm.features.opl.detail.data.fileName
import com.ih.osm.features.opl.domain.model.Opl
import com.ih.osm.features.opl.domain.model.OplContent
import com.ih.osm.features.opl.domain.model.OplContentType
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun OplDetailScreenRoute(
    siteId: Long,
    oplId: Long,
    siteName: String,
    onBack: () -> Unit,
    viewModel: OplDetailViewModel = koinViewModel(),
) {
    val state by viewModel.getStateFlow().collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val downloadedMessage = stringResource(R.string.opl_detail_download_complete)
    val lifecycleOwner = LocalLifecycleOwner.current
    var choosingDestination by rememberSaveable { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        choosingDestination = false
        uri?.let { viewModel.process(OplDetailViewModel.Action.Export(it, siteName)) }
    }
    LaunchedEffect(siteId, oplId) {
        viewModel.process(OplDetailViewModel.Action.Load(siteId, oplId))
    }
    LaunchedEffect(viewModel, lifecycleOwner, downloadedMessage) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.getEventFlow().collect { event ->
                if (event is OplDetailViewModel.Event.ExportFinished) snackbar.showSnackbar(downloadedMessage)
            }
        }
    }
    OplDetailScreen(
        state = state,
        siteName = siteName,
        onAction = viewModel::process,
        onBack = onBack,
        snackbarHostState = snackbar,
        downloadEnabled = !choosingDestination,
        onDownload = {
            state.opl?.let {
                choosingDestination = true
                val name = it.title.replace(Regex("[^\\p{L}\\p{N}._ -]"), "_").take(80).ifBlank { "OPL-${it.id}" }
                exportLauncher.launch("$name.zip")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OplDetailScreen(
    state: OplDetailUiState,
    siteName: String,
    onAction: (OplDetailViewModel.Action) -> Unit,
    onBack: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
    downloadEnabled: Boolean = true,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { AnatomyText(stringResource(R.string.opl_detail_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.navigation_back))
                    }
                },
                actions = {
                    if (state.isExporting) CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp))
                    else IconButton(onClick = onDownload, enabled = state.opl != null && downloadEnabled) {
                        Icon(Icons.Outlined.Download, stringResource(R.string.opl_detail_download))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.error != null -> Column(Modifier.padding(20.dp)) {
                    AnatomyBanner(
                        message = stringResource(when (state.error) {
                            OplDetailError.NO_CONNECTION -> R.string.opl_no_connection
                            OplDetailError.ACCESS_DENIED -> R.string.opl_access_denied
                            OplDetailError.NOT_FOUND -> R.string.opl_detail_not_found
                            else -> R.string.opl_detail_load_failed
                        }),
                        type = AnatomyBannerType.ERROR,
                        actionLabel = stringResource(R.string.cards_retry),
                        onAction = { onAction(OplDetailViewModel.Action.Retry) },
                    )
                }
                state.opl != null -> {
                    val opl = state.opl
                    LazyColumn(
                        modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
                        contentPadding = PaddingValues(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        if (state.exportFailed) item("export_error") {
                            AnatomyBanner(
                                message = stringResource(R.string.opl_detail_download_failed),
                                type = AnatomyBannerType.ERROR,
                                onDismiss = { onAction(OplDetailViewModel.Action.DismissExportError) },
                            )
                        }
                        if (state.isExporting) item("export_progress") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                                AnatomyText(stringResource(R.string.opl_detail_downloading))
                            }
                        }
                        item("summary") { OplOverview(opl, siteName) }
                        item("locations") {
                            DetailSection(stringResource(R.string.opl_detail_locations)) {
                                if (opl.levels.isEmpty()) AnatomyText(stringResource(R.string.opl_card_no_locations))
                                opl.levels.distinctBy { it.id }.forEach { level ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Icon(Icons.Outlined.LocationOn, null, tint = MaterialTheme.colorScheme.primary)
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            AnatomyText(level.name, properties = AnatomyTextProperties(fontWeight = FontWeight.SemiBold))
                                            level.machineId?.takeIf(String::isNotBlank)?.let {
                                                AnatomyText(stringResource(R.string.opl_detail_machine_id, it), style = MaterialTheme.typography.bodySmall)
                                            }
                                            level.description?.takeIf(String::isNotBlank)?.let {
                                                AnatomyText(it, style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        item("content_title") {
                            AnatomyText(stringResource(R.string.opl_detail_content), style = MaterialTheme.typography.titleLarge,
                                properties = AnatomyTextProperties(fontWeight = FontWeight.Bold))
                        }
                        if (opl.content.isEmpty()) item("no_content") {
                            AnatomyBanner(stringResource(R.string.opl_card_no_content), AnatomyBannerType.INFO)
                        }
                        items(opl.content.sortedWith(compareBy<OplContent> { it.order }.thenBy { it.id }), key = { "content-${it.id}" }) { content ->
                            OplContentCard(content, state, onAction)
                        }
                        item("document_info") { OplDocumentInfo(opl) }
                    }
                }
            }
        }
    }
    val pdf = state.opl?.content?.firstOrNull { it.id == state.selectedPdfId }
    if (pdf != null) OplPdfBottomSheet(
        title = fileName(pdf),
        path = state.files[pdf.id],
        failed = pdf.id in state.failedFiles,
        onRetry = { onAction(OplDetailViewModel.Action.ResolveMedia(pdf.id)) },
        onDismiss = { onAction(OplDetailViewModel.Action.ClosePdf) },
    )
}

@Composable
private fun OplOverview(opl: Opl, siteName: String) {
    DetailSection(siteName) {
        AnatomyText(opl.title, style = MaterialTheme.typography.headlineSmall,
            properties = AnatomyTextProperties(fontWeight = FontWeight.Bold))
        opl.typeName?.takeIf(String::isNotBlank)?.let {
            AnatomyText(it, style = MaterialTheme.typography.labelLarge,
                properties = AnatomyTextProperties(color = MaterialTheme.colorScheme.primary))
        }
        HorizontalDivider()
        AnatomyText(stringResource(R.string.opl_detail_objective), style = MaterialTheme.typography.titleSmall,
            properties = AnatomyTextProperties(fontWeight = FontWeight.SemiBold))
        SelectionContainer { AnatomyText(opl.objective?.takeIf(String::isNotBlank) ?: stringResource(R.string.opl_detail_no_objective)) }
    }
}

@Composable
private fun OplDocumentInfo(opl: Opl) {
    DetailSection(stringResource(R.string.opl_detail_document_info)) {
        DetailValue(stringResource(R.string.opl_detail_identifier), opl.id.toString())
        DetailValue(stringResource(R.string.opl_detail_creator), opl.creatorName)
        DetailValue(stringResource(R.string.opl_detail_reviewer), opl.reviewerName)
        DetailValue(stringResource(R.string.opl_detail_created), opl.createdAt.displayTimestamp())
        DetailValue(stringResource(R.string.opl_detail_updated), opl.updatedAt.displayTimestamp())
        opl.ciltUsageCount?.let { DetailValue(stringResource(R.string.opl_detail_cilt_usage), it.toString()) }
        opl.directUsageCount?.let { DetailValue(stringResource(R.string.opl_detail_direct_usage), it.toString()) }
        opl.lastUsedAt?.let { DetailValue(stringResource(R.string.opl_detail_last_used), it.displayTimestamp()) }
    }
}

@Composable
private fun DetailValue(label: String, value: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        AnatomyText(label, style = MaterialTheme.typography.labelMedium,
            properties = AnatomyTextProperties(color = MaterialTheme.colorScheme.onSurfaceVariant))
        AnatomyText(value?.takeIf(String::isNotBlank) ?: stringResource(R.string.opl_detail_not_assigned))
    }
}

@Composable
private fun OplContentCard(content: OplContent, state: OplDetailUiState, onAction: (OplDetailViewModel.Action) -> Unit) {
    val mediaType = content.galleryType()
    LaunchedEffect(content.id) {
        if (mediaType == CardEvidenceMediaType.IMAGE && content.id !in state.failedFiles) {
            onAction(OplDetailViewModel.Action.ResolveMedia(content.id))
        }
    }
    AnatomyCard(
        removeBorder = true
    ) {
        if (content.type == OplContentType.TEXT) {
            AnatomyText(stringResource(R.string.opl_detail_text), style = MaterialTheme.typography.titleSmall)
            SelectionContainer {
                AnatomyText(content.text?.takeIf(String::isNotBlank) ?: stringResource(R.string.opl_detail_empty_text))
            }
        } else {
            content.text?.takeIf(String::isNotBlank)?.let { SelectionContainer { AnatomyText(it) } }
            when {
                mediaType != null -> EvidenceMediaGallery(
                    modifier = Modifier.fillMaxWidth(),
                    evidences = listOf(content.galleryItem(state)),
                    isOnlyRead = true,
                    onRequestSource = { onAction(OplDetailViewModel.Action.ResolveMedia(content.id)) },
                )
                content.type == OplContentType.PDF -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.PictureAsPdf, null, tint = MaterialTheme.colorScheme.primary)
                        AnatomyText(fileName(content), modifier = Modifier.weight(1f),
                            properties = AnatomyTextProperties(maxLines = 2, overflow = TextOverflow.Ellipsis))
                    }
                    AnatomyButton(
                        text = stringResource(R.string.opl_detail_open_pdf),
                        onClick = { onAction(OplDetailViewModel.Action.OpenPdf(content.id)) },
                        leadingIcon = Icons.Outlined.PictureAsPdf,
                    )
                }
                else -> AnatomyBanner(
                    stringResource(R.string.opl_detail_unsupported_content, content.typeCode),
                    AnatomyBannerType.INFO,
                )
            }
        }
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    AnatomyCard(
        removeBorder = true
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            AnatomyText(title, style = MaterialTheme.typography.titleMedium,
                properties = AnatomyTextProperties(fontWeight = FontWeight.SemiBold))
            content()
        }
    }
}

private fun String?.displayTimestamp(): String? {
    val raw = this?.takeIf(String::isNotBlank) ?: return null
    val dateTimeFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(Locale.getDefault())
    return runCatching { OffsetDateTime.parse(raw).format(dateTimeFormat) }.getOrNull()
        ?: runCatching { LocalDateTime.parse(raw.replace(' ', 'T')).format(dateTimeFormat) }.getOrNull()
        ?: runCatching {
            LocalDate.parse(raw).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()))
        }.getOrNull()
        ?: raw
}
