package com.ih.osm.features.permissions

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ih.osm.R
import com.ih.osm.designsystem.anatomy.AnatomyButton
import com.ih.osm.designsystem.anatomy.AnatomyButtonStyle
import com.ih.osm.designsystem.anatomy.AnatomyText
import com.ih.osm.designsystem.anatomy.AnatomyTextProperties
import com.ih.osm.features.permissions.domain.model.AppPermissionItem
import com.ih.osm.features.permissions.domain.model.AppPermissionKind
import com.ih.osm.features.permissions.domain.model.AppPermissionStatus
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@Composable
fun PermissionsBottomSheetHost(
    autoPrompt: Boolean = true,
    showRequestKey: Int = 0,
    onPermissionsChanged: () -> Unit = {},
    viewModel: PermissionsViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.getStateFlow().collectAsStateWithLifecycle()
    val runtimePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        viewModel.process(PermissionsViewModel.Action.RuntimeRequestFinished)
        onPermissionsChanged()
    }
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.process(PermissionsViewModel.Action.RuntimeRequestFinished)
        onPermissionsChanged()
    }

    LaunchedEffect(autoPrompt) {
        if (autoPrompt) viewModel.process(PermissionsViewModel.Action.Initialize)
    }
    LaunchedEffect(showRequestKey) {
        if (showRequestKey > 0) viewModel.process(PermissionsViewModel.Action.Show)
    }
    LaunchedEffect(viewModel) {
        viewModel.getEventFlow().collectLatest { event ->
            when (event) {
                is PermissionsViewModel.Event.RequestRuntimePermissions ->
                    runtimePermissionLauncher.launch(event.permissions.toTypedArray())
                PermissionsViewModel.Event.OpenAppSettings ->
                    settingsLauncher.launch(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )
            }
        }
    }

    if (state.showSheet) {
        PermissionsBottomSheet(
            state = state,
            onContinue = { viewModel.process(PermissionsViewModel.Action.Continue) },
            onNotNow = { viewModel.process(PermissionsViewModel.Action.NotNow) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PermissionsBottomSheet(
    state: PermissionsViewModel.UiState,
    onContinue: () -> Unit,
    onNotNow: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )

    ModalBottomSheet(
        modifier = Modifier.fillMaxSize(),
        sheetState = sheetState,
        onDismissRequest = onNotNow,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                modifier = Modifier.size(54.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            AnatomyText(
                text = stringResource(R.string.permissions_title),
                style = MaterialTheme.typography.titleLarge,
                properties = AnatomyTextProperties(fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(5.dp))
            AnatomyText(
                text = stringResource(R.string.permissions_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                properties = AnatomyTextProperties(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                ),
            )
            Spacer(Modifier.height(18.dp))

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                items(state.items, key = { it.kind.name }) { item ->
                    PermissionRow(item)
                }
            }

            AnatomyButton(
                text = stringResource(
                    if (state.hasRequestedOnce) R.string.permissions_open_settings else R.string.permissions_continue,
                ),
                onClick = onContinue,
                isLoading = state.isRequesting,
            )
            Spacer(Modifier.height(8.dp))
            AnatomyButton(
                text = stringResource(R.string.permissions_not_now),
                onClick = onNotNow,
                style = AnatomyButtonStyle.SECONDARY,
                enabled = !state.isRequesting,
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun PermissionRow(item: AppPermissionItem) {
    val visual = item.kind.visual()
    val statusColor = when (item.status) {
        AppPermissionStatus.GRANTED,
        AppPermissionStatus.SYSTEM_MANAGED,
        -> MaterialTheme.colorScheme.primary
        AppPermissionStatus.PARTIAL -> Color(0xFFE19A00)
        AppPermissionStatus.MISSING -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        visual.icon,
                        contentDescription = null,
                        modifier = Modifier.size(21.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                AnatomyText(
                    text = stringResource(visual.title),
                    style = MaterialTheme.typography.labelLarge,
                    properties = AnatomyTextProperties(fontWeight = FontWeight.SemiBold),
                )
                Spacer(Modifier.height(2.dp))
                AnatomyText(
                    text = stringResource(visual.description),
                    style = MaterialTheme.typography.bodySmall,
                    properties = AnatomyTextProperties(color = MaterialTheme.colorScheme.onSurfaceVariant),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = when (item.status) {
                        AppPermissionStatus.GRANTED,
                        AppPermissionStatus.SYSTEM_MANAGED,
                        -> Icons.Outlined.Check
                        AppPermissionStatus.PARTIAL -> Icons.Outlined.WarningAmber
                        AppPermissionStatus.MISSING -> Icons.Outlined.Shield
                    },
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = statusColor,
                )
                AnatomyText(
                    text = stringResource(item.status.labelResource()),
                    style = MaterialTheme.typography.labelSmall,
                    properties = AnatomyTextProperties(color = statusColor),
                )
            }
        }
    }
}

private data class PermissionVisual(
    val icon: ImageVector,
    val title: Int,
    val description: Int,
)

private fun AppPermissionKind.visual(): PermissionVisual = when (this) {
    AppPermissionKind.NOTIFICATIONS -> PermissionVisual(
        Icons.Outlined.Notifications,
        R.string.permissions_notifications_title,
        R.string.permissions_notifications_description,
    )
    AppPermissionKind.CAMERA -> PermissionVisual(
        Icons.Outlined.CameraAlt,
        R.string.permissions_camera_title,
        R.string.permissions_camera_description,
    )
    AppPermissionKind.MICROPHONE -> PermissionVisual(
        Icons.Outlined.Mic,
        R.string.permissions_microphone_title,
        R.string.permissions_microphone_description,
    )
    AppPermissionKind.GALLERY -> PermissionVisual(
        Icons.Outlined.Collections,
        R.string.permissions_gallery_title,
        R.string.permissions_gallery_description,
    )
    AppPermissionKind.BACKGROUND_TASKS -> PermissionVisual(
        Icons.Outlined.CloudSync,
        R.string.permissions_background_title,
        R.string.permissions_background_description,
    )
}

private fun AppPermissionStatus.labelResource(): Int = when (this) {
    AppPermissionStatus.GRANTED -> R.string.permissions_status_granted
    AppPermissionStatus.PARTIAL -> R.string.permissions_status_partial
    AppPermissionStatus.MISSING -> R.string.permissions_status_pending
    AppPermissionStatus.SYSTEM_MANAGED -> R.string.permissions_status_ready
}
