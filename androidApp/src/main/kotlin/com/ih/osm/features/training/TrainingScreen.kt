package com.ih.osm.features.training

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ih.osm.designsystem.anatomy.AnatomyBanner
import com.ih.osm.designsystem.anatomy.AnatomyBannerType
import com.ih.osm.designsystem.anatomy.AnatomyButton
import com.ih.osm.designsystem.anatomy.AnatomyCard
import com.ih.osm.designsystem.anatomy.AnatomyText
import com.ih.osm.designsystem.anatomy.AnatomyTextProperties
import com.ih.osm.features.training.domain.model.TrainingRecord
import com.ih.osm.features.training.domain.model.TrainingStatus
import org.koin.androidx.compose.koinViewModel

@Composable
fun TrainingScreenRoute(
    onBack: () -> Unit,
    onOpenOpl: (Long, Long) -> Unit,
    viewModel: TrainingViewModel = koinViewModel(),
) {
    val state by viewModel.getStateFlow().collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.process(TrainingViewModel.Action.Initialize)
    }
    TrainingScreen(
        state = state,
        onAction = viewModel::process,
        onBack = onBack,
        onOpenOpl = onOpenOpl,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingScreen(
    state: TrainingUiState,
    onAction: (TrainingViewModel.Action) -> Unit,
    onBack: () -> Unit,
    onOpenOpl: (Long, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        AnatomyText(
                            "Mis capacitaciones",
                            style = MaterialTheme.typography.titleLarge,
                            properties = AnatomyTextProperties(fontWeight = FontWeight.Bold),
                        )
                        AnatomyText(
                            "Cursos (OPL) asignados a tu posición",
                            style = MaterialTheme.typography.bodySmall,
                            properties =
                                AnatomyTextProperties(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Regresar",
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
            )
        },
    ) { innerPadding ->
        Box(
            Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "banners") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        state.error?.let { error ->
                            AnatomyBanner(
                                message =
                                    when (error) {
                                        TrainingError.LOAD_FAILED ->
                                            "No se pudieron cargar tus capacitaciones. Revisa tu conexión."
                                        TrainingError.MARK_FAILED ->
                                            "No se pudo registrar el curso. Intenta de nuevo."
                                        TrainingError.NOT_AUTHENTICATED ->
                                            "Tu sesión expiró. Vuelve a iniciar sesión."
                                    },
                                type = AnatomyBannerType.ERROR,
                                onDismiss = { onAction(TrainingViewModel.Action.DismissError) },
                            )
                        }
                        state.message?.let { message ->
                            AnatomyBanner(
                                message =
                                    when (message) {
                                        TrainingMessage.MARKED_TAKEN ->
                                            "Marcado como tomado. Tu supervisor lo validará."
                                    },
                                type = AnatomyBannerType.SUCCESS,
                                onDismiss = { onAction(TrainingViewModel.Action.DismissMessage) },
                            )
                        }
                        if (state.isLoading) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }

                if (!state.isLoading && state.records.isEmpty()) {
                    item(key = "empty") { TrainingEmpty() }
                } else {
                    items(state.records, key = TrainingRecord::id) { record ->
                        TrainingCard(
                            record = record,
                            isMarking = state.markingOplId == record.oplId,
                            isViewed = state.viewedOplIds.contains(record.oplId),
                            onOpenOpl = {
                                record.siteId?.let { site ->
                                    onAction(TrainingViewModel.Action.MarkViewed(record.oplId))
                                    onOpenOpl(site, record.oplId)
                                }
                            },
                            onMarkTaken = {
                                onAction(TrainingViewModel.Action.MarkTaken(record.oplId))
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrainingCard(
    record: TrainingRecord,
    isMarking: Boolean,
    isViewed: Boolean,
    onOpenOpl: () -> Unit,
    onMarkTaken: () -> Unit,
) {
    AnatomyCard(contentPadding = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AnatomyText(
                text = record.oplTitle,
                style = MaterialTheme.typography.titleMedium,
                properties = AnatomyTextProperties(fontWeight = FontWeight.SemiBold),
            )
            record.oplObjective?.takeIf { it.isNotBlank() }?.let {
                AnatomyText(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    properties =
                        AnatomyTextProperties(color = MaterialTheme.colorScheme.onSurfaceVariant),
                )
            }
            TrainingStatusChip(record)
            record.expiresAt?.takeIf { record.status == TrainingStatus.VALIDATED }?.let {
                AnatomyText(
                    text = "Vence: ${it.take(10)}",
                    style = MaterialTheme.typography.bodySmall,
                    properties =
                        AnatomyTextProperties(color = MaterialTheme.colorScheme.onSurfaceVariant),
                )
            }
            // Always let the user open and read the course content.
            AnatomyButton(
                text = "Ver curso",
                onClick = onOpenOpl,
                leadingIcon = Icons.AutoMirrored.Outlined.MenuBook,
            )
            // "I took it" is only enabled once the user opened the course,
            // and only when it is pending or expired.
            if (record.status == TrainingStatus.PENDING ||
                record.status == TrainingStatus.EXPIRED
            ) {
                AnatomyButton(
                    text = if (isViewed) "Ya lo tomé" else "Primero abre el curso",
                    onClick = onMarkTaken,
                    enabled = isViewed && !isMarking,
                    leadingIcon = Icons.Outlined.CheckCircle,
                )
            }
        }
    }
}

@Composable
private fun TrainingStatusChip(record: TrainingRecord) {
    val (label, color) =
        when (record.status) {
            TrainingStatus.PENDING -> "Pendiente" to Color(0xFF8C8C8C)
            TrainingStatus.SELF_COMPLETED -> "Por validar" to Color(0xFFD48806)
            TrainingStatus.VALIDATED -> "Completado" to Color(0xFF389E0D)
            TrainingStatus.EXPIRED -> "Vencido" to Color(0xFFCF1322)
            TrainingStatus.UNKNOWN -> "—" to Color(0xFF8C8C8C)
        }
    AnatomyText(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        properties = AnatomyTextProperties(color = color, fontWeight = FontWeight.Bold),
    )
}

@Composable
private fun TrainingEmpty() {
    AnatomyCard(contentPadding = PaddingValues(24.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Outlined.School,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            AnatomyText(
                text = "Sin capacitaciones asignadas",
                style = MaterialTheme.typography.titleMedium,
                properties =
                    AnatomyTextProperties(
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    ),
            )
            AnatomyText(
                text = "Cuando se te asigne un curso (OPL) a tu posición, aparecerá aquí.",
                style = MaterialTheme.typography.bodyMedium,
                properties =
                    AnatomyTextProperties(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    ),
            )
        }
    }
}
