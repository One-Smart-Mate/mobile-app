package com.ih.osm.features.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ih.osm.BuildConfig
import com.ih.osm.R
import com.ih.osm.designsystem.anatomy.AnatomyBanner
import com.ih.osm.designsystem.anatomy.AnatomyBannerType
import com.ih.osm.designsystem.anatomy.AnatomyButton
import com.ih.osm.designsystem.anatomy.AnatomyButtonStyle
import com.ih.osm.designsystem.anatomy.AnatomyImage
import com.ih.osm.designsystem.anatomy.AnatomyImageSource
import com.ih.osm.designsystem.anatomy.AnatomyText
import com.ih.osm.designsystem.anatomy.AnatomyTextProperties
import com.ih.osm.designsystem.preview.PreviewScreen
import com.ih.osm.designsystem.theme.OneSmartMateTheme
import com.ih.osm.features.auth.domain.model.AuthenticatedUser
import com.ih.osm.features.auth.domain.model.UserSite
import com.ih.osm.features.catalog.domain.model.CatalogKind
import com.ih.osm.features.catalog.domain.model.CatalogSyncStatus
import com.ih.osm.features.permissions.PermissionsBottomSheetHost
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingsScreenRoute(
    user: AuthenticatedUser,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.getStateFlow().collectAsStateWithLifecycle()
    val catalogSyncState = state.catalogSyncStatus
    val isTransientSyncState = catalogSyncState is CatalogSyncStatus.WaitingForNetwork ||
        catalogSyncState is CatalogSyncStatus.Downloading
    var showTransientSyncState by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.process(SettingsViewModel.Action.RefreshPermissions) }
    LaunchedEffect(user) { viewModel.process(SettingsViewModel.Action.BindUser(user)) }
    LaunchedEffect(isTransientSyncState) {
        showTransientSyncState = if (isTransientSyncState) {
            delay(SYNC_PROGRESS_VISIBILITY_DELAY_MS)
            true
        } else {
            false
        }
    }

    SettingsScreen(
        user = user,
        state = state,
        catalogSyncState = when {
            catalogSyncState is CatalogSyncStatus.Failed -> catalogSyncState
            showTransientSyncState -> catalogSyncState
            else -> CatalogSyncStatus.Idle
        },
        versionName = BuildConfig.VERSION_NAME,
        onInformationClick = { viewModel.process(SettingsViewModel.Action.ShowAccountInformation) },
        onPermissionsClick = { viewModel.process(SettingsViewModel.Action.ShowPermissions) },
        onAllowMobileDataChange = {
            viewModel.process(SettingsViewModel.Action.SetAllowMobileData(it))
        },
        onCatalogSyncClick = { viewModel.process(SettingsViewModel.Action.RequestCatalogSync) },
        onLogoutClick = { viewModel.process(SettingsViewModel.Action.RequestLogout(user)) },
        onDismissError = { viewModel.process(SettingsViewModel.Action.DismissError) },
        onDismissCatalogBanner = { viewModel.process(SettingsViewModel.Action.DismissCatalogBanner) },
        modifier = modifier,
    )

    PermissionsBottomSheetHost(
        autoPrompt = false,
        isRequested = state.showPermissions,
        onPermissionsChanged = { viewModel.process(SettingsViewModel.Action.RefreshPermissions) },
        onDismissed = { viewModel.process(SettingsViewModel.Action.DismissPermissions) },
    )

    if (state.showAccountInformation) {
        AccountInformationSheet(
            user = user,
            onDismiss = { viewModel.process(SettingsViewModel.Action.DismissAccountInformation) },
        )
    }

    if (state.showPendingLogoutWarning) {
        PendingCardsLogoutDialog(
            pendingCount = state.pendingCardCount,
            isLoggingOut = state.isLoggingOut,
            onDismiss = { viewModel.process(SettingsViewModel.Action.DismissLogoutWarning) },
            onConfirm = { viewModel.process(SettingsViewModel.Action.ConfirmLogout(user)) },
        )
    }

    if (state.showCatalogSyncConfirmation) {
        CatalogSyncConfirmationDialog(
            onDismiss = { viewModel.process(SettingsViewModel.Action.DismissCatalogSyncConfirmation) },
            onConfirm = { viewModel.process(SettingsViewModel.Action.ConfirmCatalogSync) },
        )
    }
}

private const val SYNC_PROGRESS_VISIBILITY_DELAY_MS = 500L

@Composable
private fun SettingsScreen(
    user: AuthenticatedUser,
    state: SettingsUiState,
    catalogSyncState: CatalogSyncStatus,
    versionName: String,
    onInformationClick: () -> Unit,
    onPermissionsClick: () -> Unit,
    onAllowMobileDataChange: (Boolean) -> Unit,
    onCatalogSyncClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onDismissError: () -> Unit,
    onDismissCatalogBanner: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            AnatomyText(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                properties = AnatomyTextProperties(fontWeight = FontWeight.Bold),
            )
        }
        item { AccountSummary(user) }
        if (state.logoutFailed) {
            item {
                AnatomyBanner(
                    message = stringResource(R.string.settings_logout_error),
                    type = AnatomyBannerType.ERROR,
                    onDismiss = onDismissError,
                )
            }
        }
        state.catalogBanner?.let { banner ->
            item {
                AnatomyBanner(
                    message = stringResource(banner.messageResource()),
                    type = AnatomyBannerType.ERROR,
                    onDismiss = onDismissCatalogBanner,
                )
            }
        }
        item { SectionLabel(stringResource(R.string.settings_account_section)) }
        item {
            SettingsGroup {
                SettingsRow(
                    icon = Icons.Outlined.AccountCircle,
                    title = stringResource(R.string.settings_information),
                    subtitle = stringResource(R.string.settings_information_description),
                    onClick = onInformationClick,
                    trailing = { Chevron() },
                )
            }
        }
        item { SectionLabel(stringResource(R.string.settings_application_section)) }
        item {
            SettingsGroup {
                SettingsRow(
                    icon = Icons.Outlined.Security,
                    title = stringResource(R.string.settings_permissions),
                    subtitle = stringResource(R.string.settings_permissions_description),
                    onClick = onPermissionsClick,
                    trailing = {
                        if (state.missingPermissionCount > 0) {
                            PendingBadge(state.missingPermissionCount)
                            Spacer(Modifier.width(4.dp))
                        }
                        Chevron()
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                CatalogSyncSettingsRow(
                    state = catalogSyncState,
                    isBusy = state.isCatalogSyncBusy(),
                    onClick = onCatalogSyncClick,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsRow(
                    icon = Icons.Outlined.SignalCellularAlt,
                    title = stringResource(R.string.settings_mobile_data),
                    subtitle = stringResource(R.string.settings_mobile_data_description),
                    onClick = { onAllowMobileDataChange(!state.allowMobileData) },
                    trailing = {
                        Switch(
                            checked = state.allowMobileData,
                            onCheckedChange = onAllowMobileDataChange,
                        )
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsRow(
                    icon = Icons.Outlined.Info,
                    title = stringResource(R.string.settings_version),
                    subtitle = versionName,
                )
            }
        }
        item {
            SettingsGroup {
                SettingsRow(
                    icon = Icons.AutoMirrored.Outlined.Logout,
                    title = stringResource(R.string.settings_logout),
                    titleColor = MaterialTheme.colorScheme.error,
                    iconColor = MaterialTheme.colorScheme.error,
                    enabled = !state.isLoggingOut,
                    onClick = onLogoutClick,
                    trailing = {
                        if (state.isLoggingOut) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.error,
                            )
                        } else {
                            Chevron(tint = MaterialTheme.colorScheme.error)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun CatalogSyncSettingsRow(
    state: CatalogSyncStatus,
    isBusy: Boolean,
    onClick: () -> Unit,
) {
    val isFailure = state is CatalogSyncStatus.Failed
    val status = when (state) {
        CatalogSyncStatus.Idle -> stringResource(R.string.home_catalog_sync_action)
        CatalogSyncStatus.WaitingForNetwork -> stringResource(R.string.catalog_sync_waiting)
        is CatalogSyncStatus.Downloading -> state.catalog.catalogLabel()
        is CatalogSyncStatus.Failed -> state.message ?: stringResource(R.string.catalog_sync_failed)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isBusy, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = if (isFailure) Icons.Outlined.CloudOff else Icons.Outlined.CloudDownload,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = if (isFailure) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            AnatomyText(
                text = stringResource(R.string.home_catalog_sync_title),
                style = MaterialTheme.typography.bodyMedium,
                properties = AnatomyTextProperties(fontWeight = FontWeight.Medium),
            )
            AnatomyText(
                text = if (state is CatalogSyncStatus.Idle) {
                    stringResource(R.string.home_catalog_sync_body)
                } else {
                    status
                },
                style = MaterialTheme.typography.bodySmall,
                properties = AnatomyTextProperties(
                    color = if (isFailure) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                ),
            )
            if (state is CatalogSyncStatus.Idle) {
                AnatomyText(
                    text = status,
                    style = MaterialTheme.typography.labelMedium,
                    properties = AnatomyTextProperties(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }
            when (state) {
                CatalogSyncStatus.WaitingForNetwork -> {
                    Spacer(Modifier.height(5.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                is CatalogSyncStatus.Downloading -> {
                    Spacer(Modifier.height(5.dp))
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun AccountSummary(user: AuthenticatedUser) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AnatomyImage(
                source = user.logo?.takeIf(String::isNotBlank)?.let(AnatomyImageSource::Url),
                contentDescription = null,
                shape = CircleShape,
                modifier = Modifier.size(48.dp),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                AnatomyText(
                    text = user.name,
                    style = MaterialTheme.typography.titleSmall,
                    properties = AnatomyTextProperties(
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    ),
                )
                AnatomyText(
                    text = user.email,
                    style = MaterialTheme.typography.bodySmall,
                    properties = AnatomyTextProperties(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    ),
                )
                AnatomyText(
                    text = accountScope(user),
                    style = MaterialTheme.typography.bodySmall,
                    properties = AnatomyTextProperties(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    ),
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    AnatomyText(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        properties = AnatomyTextProperties(
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        ),
    )
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    iconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            AnatomyText(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                properties = AnatomyTextProperties(color = titleColor, fontWeight = FontWeight.Medium),
            )
            subtitle?.let {
                AnatomyText(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    properties = AnatomyTextProperties(color = MaterialTheme.colorScheme.onSurfaceVariant),
                )
            }
        }
        trailing?.invoke(this)
    }
}

@Composable
private fun Chevron(tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = tint)
}

@Composable
private fun PendingBadge(count: Int) {
    Surface(
        shape = RoundedCornerShape(50),
        color = Color(0xFFFFE8BD),
        contentColor = Color(0xFF8A5500),
    ) {
        AnatomyText(
            text = stringResource(R.string.settings_permissions_pending, count),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountInformationSheet(user: AuthenticatedUser, onDismiss: () -> Unit) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            AnatomyText(
                text = stringResource(R.string.settings_account_information_title),
                style = MaterialTheme.typography.titleLarge,
                properties = AnatomyTextProperties(fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(16.dp))
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { InformationRow(stringResource(R.string.settings_name), user.name) }
                item { InformationRow(stringResource(R.string.settings_email), user.email) }
                item { InformationRow(stringResource(R.string.settings_company), user.companyName) }
                item {
                    InformationRow(
                        stringResource(R.string.settings_sites),
                        user.sites.joinToString { it.name }.ifBlank { stringResource(R.string.settings_no_sites) },
                    )
                }
                item {
                    InformationRow(
                        stringResource(R.string.settings_roles),
                        user.roles.joinToString(" · ").ifBlank { stringResource(R.string.settings_no_roles) },
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            AnatomyButton(
                text = stringResource(R.string.settings_close),
                onClick = onDismiss,
                style = AnatomyButtonStyle.SECONDARY,
            )
        }
    }
}

@Composable
private fun InformationRow(label: String, value: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            AnatomyText(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.width(86.dp),
                properties = AnatomyTextProperties(color = MaterialTheme.colorScheme.onSurfaceVariant),
            )
            AnatomyText(text = value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun CatalogSyncConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.CloudDownload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            AnatomyText(
                text = stringResource(R.string.home_catalog_sync_confirm_title),
                style = MaterialTheme.typography.titleLarge,
                properties = AnatomyTextProperties(fontWeight = FontWeight.Bold),
            )
        },
        text = {
            AnatomyText(
                text = stringResource(R.string.home_catalog_sync_confirm_body),
                style = MaterialTheme.typography.bodyMedium,
                properties = AnatomyTextProperties(color = MaterialTheme.colorScheme.onSurfaceVariant),
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                AnatomyText(stringResource(R.string.settings_cancel))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                AnatomyText(
                    text = stringResource(R.string.home_catalog_sync_confirm_action),
                    properties = AnatomyTextProperties(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }
        },
    )
}

private fun SettingsBanner.messageResource(): Int = when (this) {
    SettingsBanner.MOBILE_DATA_DISABLED -> R.string.home_catalog_sync_mobile_data_disabled
    SettingsBanner.NO_INTERNET -> R.string.home_catalog_sync_no_internet
    SettingsBanner.CATALOG_SYNC_FAILED -> R.string.catalog_sync_failed
}

@Composable
private fun CatalogKind?.catalogLabel(): String = when (this) {
    CatalogKind.CARD_TYPES -> stringResource(R.string.catalog_sync_card_types)
    CatalogKind.PRECLASSIFIERS -> stringResource(R.string.catalog_sync_preclassifiers)
    CatalogKind.PRIORITIES -> stringResource(R.string.catalog_sync_priorities)
    CatalogKind.LEVELS -> stringResource(R.string.catalog_sync_levels)
    CatalogKind.EMPLOYEES -> stringResource(R.string.catalog_sync_employees)
    null -> stringResource(R.string.catalog_sync_starting)
}

@Composable
private fun PendingCardsLogoutDialog(
    pendingCount: Long,
    isLoggingOut: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isLoggingOut) onDismiss() },
        icon = {
            Surface(shape = CircleShape, color = Color(0xFFFFE8BD)) {
                Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = Color(0xFF9A6000))
                }
            }
        },
        title = {
            AnatomyText(
                text = stringResource(R.string.settings_logout_warning_title),
                style = MaterialTheme.typography.titleLarge,
                properties = AnatomyTextProperties(fontWeight = FontWeight.Bold),
            )
        },
        text = {
            AnatomyText(
                text = pluralStringResource(
                    R.plurals.settings_logout_warning_body,
                    pendingCount.toInt(),
                    pendingCount,
                ),
                style = MaterialTheme.typography.bodyMedium,
                properties = AnatomyTextProperties(color = MaterialTheme.colorScheme.onSurfaceVariant),
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoggingOut) {
                AnatomyText(stringResource(R.string.settings_cancel))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isLoggingOut) {
                if (isLoggingOut) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    AnatomyText(
                        text = stringResource(R.string.settings_logout),
                        properties = AnatomyTextProperties(color = MaterialTheme.colorScheme.error),
                    )
                }
            }
        },
    )
}

private fun accountScope(user: AuthenticatedUser): String {
    val sites = user.sites.joinToString { it.name }
    return listOf(user.companyName, sites).filter(String::isNotBlank).joinToString(" · ")
}

@PreviewScreen
@Composable
private fun SettingsScreenPreview() {
    OneSmartMateTheme {
        SettingsScreen(
            user = AuthenticatedUser(
                id = 1,
                name = "TestUsuario",
                email = "testuser1@gmail.com",
                roles = listOf("local_admin", "operator"),
                logo = null,
                companyId = 1,
                companyName = "OSM",
                sites = listOf(UserSite(1, "Test Site", null)),
                appHistory = 1,
                dueDate = null,
            ),
            state = SettingsUiState(missingPermissionCount = 2),
            catalogSyncState = CatalogSyncStatus.Idle,
            versionName = "1.0-dev",
            onInformationClick = {},
            onPermissionsClick = {},
            onAllowMobileDataChange = {},
            onCatalogSyncClick = {},
            onLogoutClick = {},
            onDismissError = {},
            onDismissCatalogBanner = {},
        )
    }
}
