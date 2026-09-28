package com.ih.osm.features.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ih.osm.R
import com.ih.osm.core.network.NetworkConnectionStatus
import com.ih.osm.designsystem.anatomy.AnatomyCard
import com.ih.osm.designsystem.anatomy.AnatomyCardStyle
import com.ih.osm.designsystem.anatomy.AnatomyBanner
import com.ih.osm.designsystem.anatomy.AnatomyBannerType
import com.ih.osm.designsystem.anatomy.AnatomyDropdown
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
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeScreenRoute(
    user: AuthenticatedUser,
    onCreateCard: (Long) -> Unit,
    onOpenNotes: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.getStateFlow().collectAsStateWithLifecycle()
    val catalogSyncState = state.catalogSyncStatus
    val isTransientSyncState = catalogSyncState is CatalogSyncStatus.WaitingForNetwork ||
        catalogSyncState is CatalogSyncStatus.Downloading
    var showTransientSyncState by remember { mutableStateOf(false) }
    var selectedSiteId by rememberSaveable(user.id) {
        mutableStateOf(user.sites.firstOrNull()?.id)
    }

    LaunchedEffect(user) {
        viewModel.process(HomeViewModel.Action.BindUser(user))
    }
    LaunchedEffect(user.sites) {
        if (user.sites.none { it.id == selectedSiteId }) {
            selectedSiteId = user.sites.firstOrNull()?.id
        }
    }
    LaunchedEffect(isTransientSyncState) {
        showTransientSyncState = if (isTransientSyncState) {
            delay(SYNC_PROGRESS_VISIBILITY_DELAY_MS)
            true
        } else {
            false
        }
    }

    val selectedSite = user.sites.firstOrNull { it.id == selectedSiteId }
        ?: user.sites.firstOrNull()

    HomeScreen(
        user = user,
        selectedSite = selectedSite,
        networkStatus = state.networkStatus,
        catalogSyncState = when {
            catalogSyncState is CatalogSyncStatus.Failed -> catalogSyncState
            showTransientSyncState -> catalogSyncState
            else -> CatalogSyncStatus.Idle
        },
        isCatalogSyncBusy = state.isCatalogSyncBusy(),
        showCatalogSyncConfirmation = state.showCatalogSyncConfirmation,
        banner = state.banner,
        onSiteSelected = { selectedSiteId = it.id },
        onCreateNote = { selectedSite?.let { onCreateCard(it.id) } },
        onOpenNotes = onOpenNotes,
        pendingCardCount = state.pendingCount,
        isCardSyncing = state.isSyncing,
        cardSyncCompleted = state.completed,
        cardSyncTotal = state.total,
        onSyncPendingCards = { viewModel.process(HomeViewModel.Action.SyncPendingCards) },
        onRequestCatalogSync = { viewModel.process(HomeViewModel.Action.RequestCatalogSync) },
        onConfirmCatalogSync = { viewModel.process(HomeViewModel.Action.ConfirmCatalogSync) },
        onDismissCatalogSyncConfirmation = {
            viewModel.process(HomeViewModel.Action.DismissCatalogSyncConfirmation)
        },
        onDismissBanner = { viewModel.process(HomeViewModel.Action.DismissBanner) },
        modifier = modifier,
    )

    PermissionsBottomSheetHost()
}

private const val SYNC_PROGRESS_VISIBILITY_DELAY_MS = 500L

@Composable
fun HomeScreen(
    user: AuthenticatedUser,
    selectedSite: UserSite?,
    networkStatus: NetworkConnectionStatus,
    catalogSyncState: CatalogSyncStatus,
    isCatalogSyncBusy: Boolean,
    showCatalogSyncConfirmation: Boolean,
    banner: HomeBanner?,
    onSiteSelected: (UserSite) -> Unit,
    onCreateNote: () -> Unit,
    onOpenNotes: () -> Unit,
    pendingCardCount: Long,
    isCardSyncing: Boolean,
    cardSyncCompleted: Int,
    cardSyncTotal: Int,
    onSyncPendingCards: () -> Unit,
    onRequestCatalogSync: () -> Unit,
    onConfirmCatalogSync: () -> Unit,
    onDismissCatalogSyncConfirmation: () -> Unit,
    onDismissBanner: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            HomeHeader(user = user, selectedSite = selectedSite)

            Spacer(Modifier.height(16.dp))

            if (user.sites.isNotEmpty()) {
                AnatomyDropdown(
                    items = user.sites,
                    selectedItem = selectedSite,
                    onItemSelected = onSiteSelected,
                    itemLabel = UserSite::name,
                )
                Spacer(Modifier.height(10.dp))
            }

            NetworkStatusBadge(status = networkStatus)

            if (banner != null) {
                Spacer(Modifier.height(12.dp))
                AnatomyBanner(
                    message = stringResource(banner.messageResource()),
                    type = AnatomyBannerType.ERROR,
                    onDismiss = onDismissBanner,
                )
            }

            Spacer(Modifier.height(12.dp))
            ManualCatalogSyncCard(
                state = catalogSyncState,
                isBusy = isCatalogSyncBusy,
                onClick = onRequestCatalogSync,
            )

            if (pendingCardCount > 0) {
                Spacer(Modifier.height(12.dp))
                PendingCardsSyncCard(
                    count = pendingCardCount,
                    isSyncing = isCardSyncing,
                    completed = cardSyncCompleted,
                    total = cardSyncTotal,
                    onClick = onSyncPendingCards,
                )
            }

            Spacer(Modifier.height(24.dp))

            AnatomyText(
                text = stringResource(R.string.home_quick_actions),
                style = MaterialTheme.typography.titleMedium,
                properties = AnatomyTextProperties(fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(12.dp))

            QuickActionsGrid(
                onCreateNote = onCreateNote,
                onOpenNotes = onOpenNotes,
            )
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showCatalogSyncConfirmation) {
        CatalogSyncConfirmationDialog(
            onDismiss = onDismissCatalogSyncConfirmation,
            onConfirm = onConfirmCatalogSync,
        )
    }
}

@Composable
private fun PendingCardsSyncCard(
    count: Long,
    isSyncing: Boolean,
    completed: Int,
    total: Int,
    onClick: () -> Unit,
) {
    val displayCount = count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    AnatomyCard(
        onClick = onClick,
        enabled = !isSyncing,
        style = AnatomyCardStyle.FILLED,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.CloudUpload,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                AnatomyText(
                    text = stringResource(R.string.home_pending_cards_title),
                    style = MaterialTheme.typography.labelLarge,
                    properties = AnatomyTextProperties(fontWeight = FontWeight.SemiBold),
                )
                Spacer(Modifier.height(2.dp))
                AnatomyText(
                    text = pluralStringResource(
                        R.plurals.home_pending_cards_body,
                        displayCount,
                        displayCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    properties = AnatomyTextProperties(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
                Spacer(Modifier.height(5.dp))
                AnatomyText(
                    text = if (isSyncing) {
                        if (total > 0) {
                            stringResource(R.string.card_sync_progress, completed, total)
                        } else {
                            stringResource(R.string.card_sync_preparing)
                        }
                    } else {
                        stringResource(R.string.home_pending_cards_action)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    properties = AnatomyTextProperties(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
                if (isSyncing) {
                    Spacer(Modifier.height(7.dp))
                    if (total > 0) {
                        LinearProgressIndicator(
                            progress = { (completed.toFloat() / total).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualCatalogSyncCard(
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

    AnatomyCard(
        onClick = onClick,
        enabled = !isBusy,
        style = AnatomyCardStyle.FILLED,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Icon(
                imageVector = if (isFailure) Icons.Outlined.CloudOff else Icons.Outlined.CloudDownload,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (isFailure) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                AnatomyText(
                    text = stringResource(R.string.home_catalog_sync_title),
                    style = MaterialTheme.typography.labelLarge,
                    properties = AnatomyTextProperties(fontWeight = FontWeight.SemiBold),
                )
                Spacer(Modifier.height(2.dp))
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
                Spacer(Modifier.height(5.dp))
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
                        Spacer(Modifier.height(7.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    is CatalogSyncStatus.Downloading -> {
                        Spacer(Modifier.height(7.dp))
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

private fun HomeBanner.messageResource(): Int = when (this) {
    HomeBanner.MOBILE_DATA_DISABLED -> R.string.home_catalog_sync_mobile_data_disabled
    HomeBanner.NO_INTERNET -> R.string.home_catalog_sync_no_internet
    HomeBanner.CATALOG_SYNC_FAILED -> R.string.catalog_sync_failed
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
private fun HomeHeader(
    user: AuthenticatedUser,
    selectedSite: UserSite?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            AnatomyText(
                text = user.companyName,
                style = MaterialTheme.typography.headlineMedium,
                properties = AnatomyTextProperties(
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(Modifier.height(2.dp))
            AnatomyText(
                text = stringResource(R.string.home_greeting, user.name.firstName()),
                style = MaterialTheme.typography.bodyMedium,
                properties = AnatomyTextProperties(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
            if (user.roles.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    user.roles.forEach { role -> RoleBadge(role) }
                }
            }
        }

        Surface(
            modifier = Modifier.size(52.dp),
            shape = CircleShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            AnatomyImage(
                source = (selectedSite?.logo ?: user.logo)
                    ?.takeIf(String::isNotBlank)
                    ?.let(AnatomyImageSource::Url),
                contentDescription = selectedSite?.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                shape = CircleShape,
            )
        }
    }
}

@Composable
private fun RoleBadge(role: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        AnatomyText(
            text = role,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            properties = AnatomyTextProperties(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

@Composable
private fun NetworkStatusBadge(status: NetworkConnectionStatus) {
    val isDark = isSystemInDarkTheme()
    val visual = when (status) {
        NetworkConnectionStatus.WIFI_CONNECTED -> NetworkVisual(
            label = stringResource(R.string.network_wifi_connected),
            icon = Icons.Outlined.Wifi,
            background = MaterialTheme.colorScheme.primaryContainer,
            foreground = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        NetworkConnectionStatus.CELLULAR_CONNECTED -> NetworkVisual(
            label = stringResource(R.string.network_mobile_connected),
            icon = Icons.Outlined.SignalCellularAlt,
            background = MaterialTheme.colorScheme.primaryContainer,
            foreground = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        NetworkConnectionStatus.WIFI_NO_INTERNET -> NetworkVisual(
            label = stringResource(R.string.network_wifi_no_internet),
            icon = Icons.Outlined.Wifi,
            background = if (isDark) Color(0xFF3A301A) else Color(0xFFFFF3D6),
            foreground = if (isDark) Color(0xFFFFE2A3) else Color(0xFF574100),
        )
        NetworkConnectionStatus.CELLULAR_NO_INTERNET -> NetworkVisual(
            label = stringResource(R.string.network_mobile_no_internet),
            icon = Icons.Outlined.SignalCellularAlt,
            background = if (isDark) Color(0xFF3A301A) else Color(0xFFFFF3D6),
            foreground = if (isDark) Color(0xFFFFE2A3) else Color(0xFF574100),
        )
        NetworkConnectionStatus.OFFLINE -> NetworkVisual(
            label = stringResource(R.string.network_offline),
            icon = Icons.Outlined.WifiOff,
            background = MaterialTheme.colorScheme.errorContainer,
            foreground = MaterialTheme.colorScheme.onErrorContainer,
        )
    }

    Surface(shape = RoundedCornerShape(50), color = visual.background) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(
                imageVector = visual.icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = visual.foreground,
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(visual.foreground),
            )
            AnatomyText(
                text = visual.label,
                style = MaterialTheme.typography.labelMedium,
                properties = AnatomyTextProperties(
                    color = visual.foreground,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }
}

@Composable
private fun QuickActionsGrid(
    onCreateNote: () -> Unit,
    onOpenNotes: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            QuickActionCard(
                title = stringResource(R.string.home_action_new_note),
                icon = Icons.Outlined.NoteAdd,
                onClick = onCreateNote,
                modifier = Modifier.weight(1f),
            )
//            QuickActionCard(
//                title = stringResource(R.string.home_action_scan_qr),
//                icon = Icons.Outlined.QrCodeScanner,
//                onClick = {},
//                modifier = Modifier.weight(1f),
//            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
//            QuickActionCard(
//                title = stringResource(R.string.home_action_fast_password),
//                icon = Icons.Outlined.Key,
//                onClick = {},
//                modifier = Modifier.weight(1f),
//            )
            QuickActionCard(
                title = stringResource(R.string.cards_title),
                icon = Icons.Outlined.Description,
                onClick = onOpenNotes,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun QuickActionCard(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnatomyCard(
        modifier = modifier.heightIn(min = 108.dp, max = 116.dp),
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(30.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(10.dp))
            AnatomyText(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                properties = AnatomyTextProperties(
                    maxLines = 2,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                ),
            )
        }
    }
}

private data class NetworkVisual(
    val label: String,
    val icon: ImageVector,
    val background: Color,
    val foreground: Color,
)

private fun String.firstName(): String = trim().substringBefore(' ').ifBlank { this }

private val previewUser = AuthenticatedUser(
    id = 1,
    name = "Diego López",
    email = "diego@empresa.com",
    roles = listOf("Supervisor", "Operaciones"),
    logo = null,
    companyId = 1,
    companyName = "Industrias Nova",
    sites = listOf(
        UserSite(1, "Planta Norte", null),
        UserSite(2, "Planta Sur", null),
    ),
    appHistory = 10,
    dueDate = null,
)

@PreviewScreen
@Composable
private fun HomeScreenPreview() {
    OneSmartMateTheme {
        HomeScreen(
            user = previewUser,
            selectedSite = previewUser.sites.first(),
            networkStatus = NetworkConnectionStatus.WIFI_CONNECTED,
            catalogSyncState = CatalogSyncStatus.Downloading(0.45f, CatalogKind.LEVELS),
            isCatalogSyncBusy = true,
            showCatalogSyncConfirmation = false,
            banner = null,
            onSiteSelected = {},
            onCreateNote = {},
            onOpenNotes = {},
            pendingCardCount = 2,
            isCardSyncing = true,
            cardSyncCompleted = 1,
            cardSyncTotal = 2,
            onSyncPendingCards = {},
            onRequestCatalogSync = {},
            onConfirmCatalogSync = {},
            onDismissCatalogSyncConfirmation = {},
            onDismissBanner = {},
        )
    }
}

@Preview(name = "Home landscape", widthDp = 844, heightDp = 390, showBackground = true)
@Composable
private fun HomeScreenLandscapePreview() {
    OneSmartMateTheme {
        HomeScreen(
            user = previewUser,
            selectedSite = previewUser.sites.first(),
            networkStatus = NetworkConnectionStatus.CELLULAR_CONNECTED,
            catalogSyncState = CatalogSyncStatus.Downloading(0.7f, CatalogKind.EMPLOYEES),
            isCatalogSyncBusy = true,
            showCatalogSyncConfirmation = false,
            banner = null,
            onSiteSelected = {},
            onCreateNote = {},
            onOpenNotes = {},
            pendingCardCount = 2,
            isCardSyncing = false,
            cardSyncCompleted = 0,
            cardSyncTotal = 0,
            onSyncPendingCards = {},
            onRequestCatalogSync = {},
            onConfirmCatalogSync = {},
            onDismissCatalogSyncConfirmation = {},
            onDismissBanner = {},
        )
    }
}
