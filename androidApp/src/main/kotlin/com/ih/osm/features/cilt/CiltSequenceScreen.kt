package com.ih.osm.features.cilt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.io.ByteArrayOutputStream
import android.graphics.Bitmap
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ih.osm.R
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CiltSequenceScreenRoute(
    sequenceId: Long,
    executionId: Long,
    onBack: () -> Unit,
    onFinished: () -> Unit,
    viewModel: CiltViewModel = koinViewModel(),
) {
    val state by viewModel.getStateFlow().collectAsStateWithLifecycle()
    LaunchedEffect(sequenceId, executionId) {
        viewModel.process(CiltViewModel.Action.LoadSequence(sequenceId, executionId))
    }
    LaunchedEffect(state.completed) {
        if (state.completed) {
            viewModel.process(CiltViewModel.Action.ResetRun)
            onFinished()
        }
    }
    CiltSequenceScreen(state = state, onBack = onBack, onAction = viewModel::process)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CiltSequenceScreen(
    state: CiltUiState,
    onBack: () -> Unit,
    onAction: (CiltViewModel.Action) -> Unit,
) {
    val sequence = state.sequence
    val context = LocalContext.current
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview(),
    ) { bitmap ->
        if (bitmap != null) {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            onAction(
                CiltViewModel.Action.UploadEvidence(
                    bytes = stream.toByteArray(),
                    fileName = "evidence_${System.currentTimeMillis()}.jpg",
                    contentType = "image/jpeg",
                    type = if (state.started) "FINAL" else "INITIAL",
                ),
            )
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) cameraLauncher.launch(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(sequence?.ciltTypeName?.ifBlank { stringResource(R.string.cilt_sequence_title) } ?: stringResource(R.string.cilt_sequence_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoadingSequence && sequence == null) {
            Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(Modifier.padding(top = 48.dp))
            }
            return@Scaffold
        }
        if (sequence == null) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text(stringResource(R.string.cilt_sequence_error), style = MaterialTheme.typography.bodyMedium)
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(sequence.secuenceList, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (sequence.standardOk.isNotBlank()) {
                        Text(
                            stringResource(R.string.cilt_standard_ok, sequence.standardOk),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (sequence.toolsRequired.isNotBlank()) {
                        Text(
                            stringResource(R.string.cilt_tools, sequence.toolsRequired),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (!state.started) {
                Button(
                    onClick = { onAction(CiltViewModel.Action.StartExecution) },
                    enabled = !state.isStarting,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.cilt_start)) }
            } else {
                OutlinedTextField(
                    value = state.initialParameter,
                    onValueChange = { onAction(CiltViewModel.Action.InitialParameterChanged(it)) },
                    label = { Text(stringResource(R.string.cilt_initial_parameter)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.finalParameter,
                    onValueChange = { onAction(CiltViewModel.Action.FinalParameterChanged(it)) },
                    label = { Text(stringResource(R.string.cilt_final_parameter)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Text(stringResource(R.string.cilt_parameter_ok_question), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ParameterOkOption(stringResource(R.string.cilt_yes), state.parameterOk) {
                        onAction(CiltViewModel.Action.ParameterOkChanged(true))
                    }
                    ParameterOkOption(stringResource(R.string.cilt_no), !state.parameterOk) {
                        onAction(CiltViewModel.Action.ParameterOkChanged(false))
                    }
                }
                OutlinedButton(
                    onClick = {
                        val hasPermission = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.CAMERA,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasPermission) cameraLauncher.launch(null)
                        else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    enabled = !state.isUploadingEvidence,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (state.evidenceCount > 0) {
                            stringResource(R.string.cilt_add_evidence_count, state.evidenceCount)
                        } else {
                            stringResource(R.string.cilt_add_evidence)
                        },
                    )
                }
                Button(
                    onClick = { onAction(CiltViewModel.Action.CompleteExecution) },
                    enabled = !state.isCompleting,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.cilt_finish)) }
            }

            if (state.error != null) {
                val errorMessage = when (state.error) {
                    CiltError.START_FAILED -> stringResource(R.string.cilt_start_error)
                    CiltError.COMPLETE_FAILED -> stringResource(R.string.cilt_complete_error)
                    CiltError.EVIDENCE_FAILED -> stringResource(R.string.cilt_evidence_error)
                    else -> stringResource(R.string.cilt_sequence_error)
                }
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = { onAction(CiltViewModel.Action.DismissError) }) {
                    Text(stringResource(R.string.cilt_dismiss))
                }
            }
        }
    }
}

@Composable
private fun ParameterOkOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier.selectable(selected = selected, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label)
    }
}
