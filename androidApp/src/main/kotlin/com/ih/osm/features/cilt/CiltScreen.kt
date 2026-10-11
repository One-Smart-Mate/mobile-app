package com.ih.osm.features.cilt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ih.osm.R
import com.ih.osm.features.cilt.domain.model.CiltExecution
import com.ih.osm.features.cilt.domain.model.CiltMaster
import com.ih.osm.features.cilt.domain.model.CiltSequence
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CiltScreenRoute(
    onBack: () -> Unit,
    onRunSequence: (sequenceId: Long, executionId: Long) -> Unit,
    onViewExecution: (executionId: Long) -> Unit,
    viewModel: CiltViewModel = koinViewModel(),
) {
    val state by viewModel.getStateFlow().collectAsStateWithLifecycle()
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        viewModel.process(CiltViewModel.Action.LoadRoutine)
        onPauseOrDispose { }
    }
    CiltScreen(
        state = state,
        onBack = onBack,
        onReload = { viewModel.process(CiltViewModel.Action.LoadRoutine) },
        onRunSequence = onRunSequence,
        onViewExecution = onViewExecution,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CiltScreen(
    state: CiltUiState,
    onBack: () -> Unit,
    onReload: () -> Unit,
    onRunSequence: (Long, Long) -> Unit,
    onViewExecution: (Long) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cilt_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            when {
                state.isLoadingRoutine && state.positions.isEmpty() ->
                    CircularProgressIndicator(Modifier.padding(top = 48.dp))
                state.error != null && state.positions.isEmpty() ->
                    CiltMessage(stringResource(R.string.cilt_routine_error), onReload)
                state.positions.isEmpty() ->
                    CiltMessage(stringResource(R.string.cilt_routine_empty), onReload)
                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    state.positions.forEach { position ->
                        item(key = "pos-${position.id}") {
                            Text(
                                text = position.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        items(position.ciltMasters, key = { "m-${it.id}" }) { master ->
                            CiltMasterCard(master = master, onRunSequence = onRunSequence, onViewExecution = onViewExecution)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CiltMasterCard(master: CiltMaster, onRunSequence: (Long, Long) -> Unit, onViewExecution: (Long) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(master.ciltName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (master.ciltDescription.isNotBlank()) {
                Text(
                    master.ciltDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            master.sequences.forEach { sequence ->
                sequence.executions.forEach { execution ->
                    SequenceExecutionRow(sequence, execution, onRunSequence, onViewExecution)
                }
            }
        }
    }
}

@Composable
private fun SequenceExecutionRow(
    sequence: CiltSequence,
    execution: CiltExecution,
    onRunSequence: (Long, Long) -> Unit,
    onViewExecution: (Long) -> Unit,
) {
    Card(
        modifier = if (execution.isCompleted) {
            Modifier.fillMaxWidth().clickable { onViewExecution(execution.id) }
        } else {
            Modifier.fillMaxWidth()
        },
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    sequence.ciltTypeName.ifBlank { sequence.secuenceList },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (execution.isCompleted) {
                        stringResource(R.string.cilt_status_completed)
                    } else {
                        stringResource(R.string.cilt_status_pending)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (!execution.isCompleted) {
                FilledTonalButton(onClick = { onRunSequence(sequence.id, execution.id) }) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Text(stringResource(R.string.cilt_run))
                }
            } else {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CiltMessage(message: String, onReload: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
        FilledTonalButton(onClick = onReload) { Text(stringResource(R.string.cilt_reload)) }
    }
}
