package com.ih.osm.features.cilt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.ih.osm.R
import com.ih.osm.core.feature.viewmodel.GRViewModel
import com.ih.osm.core.network.NetworkResult
import com.ih.osm.features.cilt.domain.model.CiltEvidence
import com.ih.osm.features.cilt.domain.model.CiltExecution
import com.ih.osm.features.cilt.domain.repository.CiltRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.viewmodel.koinViewModel

data class CiltExecutionDetailUiState(
    val isLoading: Boolean = false,
    val execution: CiltExecution? = null,
    val error: Boolean = false,
)

class CiltExecutionDetailViewModel(
    private val repository: CiltRepository,
) : GRViewModel<CiltExecutionDetailUiState, CiltExecutionDetailViewModel.Action, CiltExecutionDetailViewModel.Event>(
    CiltExecutionDetailUiState(),
) {
    sealed interface Action {
        data class Load(val executionId: Long) : Action
    }

    sealed interface Event

    override fun processImpl(action: Action) {
        when (action) {
            is Action.Load -> load(action.executionId)
        }
    }

    private fun load(executionId: Long) {
        setState { copy(isLoading = true, error = false) }
        viewModelScope.launch {
            try {
                when (val result = withContext(Dispatchers.IO) { repository.getExecution(executionId) }) {
                    is NetworkResult.Success -> setState { copy(execution = result.data) }
                    is NetworkResult.Failure -> setState { copy(error = true) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                setState { copy(error = true) }
            } finally {
                setState { copy(isLoading = false) }
            }
        }
    }
}

@Composable
fun CiltExecutionDetailScreenRoute(
    executionId: Long,
    onBack: () -> Unit,
    viewModel: CiltExecutionDetailViewModel = koinViewModel(),
) {
    val state by viewModel.getStateFlow().collectAsStateWithLifecycle()
    LaunchedEffect(executionId) {
        viewModel.process(CiltExecutionDetailViewModel.Action.Load(executionId))
    }
    CiltExecutionDetailScreen(state = state, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CiltExecutionDetailScreen(
    state: CiltExecutionDetailUiState,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cilt_execution_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        val execution = state.execution
        when {
            state.isLoading && execution == null ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                    CircularProgressIndicator(Modifier.padding(top = 48.dp))
                }
            execution == null ->
                Box(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                    Text(stringResource(R.string.cilt_sequence_error), style = MaterialTheme.typography.bodyMedium)
                }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                execution.ciltTypeName.ifBlank { execution.secuenceList },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            DetailRow(stringResource(R.string.cilt_detail_start), execution.secuenceStart ?: "-")
                            DetailRow(stringResource(R.string.cilt_detail_stop), execution.secuenceStop ?: "-")
                            DetailRow(
                                stringResource(R.string.cilt_detail_initial_param),
                                execution.initialParameter?.ifBlank { "-" } ?: "-",
                            )
                            DetailRow(
                                stringResource(R.string.cilt_detail_final_param),
                                execution.finalParameter?.ifBlank { "-" } ?: "-",
                            )
                            DetailRow(
                                stringResource(R.string.cilt_detail_result),
                                if (execution.nok) {
                                    stringResource(R.string.cilt_no)
                                } else {
                                    stringResource(R.string.cilt_yes)
                                },
                            )
                        }
                    }
                }
                item {
                    Text(
                        stringResource(R.string.cilt_detail_evidences),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (execution.evidences.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.cilt_detail_no_evidences),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(execution.evidences, key = { it.id }) { evidence ->
                        EvidenceImage(evidence)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun EvidenceImage(evidence: CiltEvidence) {
    Card(Modifier.fillMaxWidth()) {
        AsyncImage(
            model = evidence.evidenceUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
        )
    }
}
