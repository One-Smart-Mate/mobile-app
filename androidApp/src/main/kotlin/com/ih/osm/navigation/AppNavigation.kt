package com.ih.osm.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.ih.osm.R
import com.ih.osm.designsystem.anatomy.AnatomyText
import com.ih.osm.features.auth.domain.model.AuthenticatedUser
import com.ih.osm.features.auth.domain.session.SessionStatus
import com.ih.osm.features.auth.login.LoginScreenRoute
import com.ih.osm.features.catalog.sync.CatalogSyncScheduler
import com.ih.osm.features.cards.CardListScreenRoute
import com.ih.osm.features.cards.sync.CardSyncScheduler
import com.ih.osm.features.createcard.CreateCardScreenRoute
import com.ih.osm.features.home.HomeScreenRoute
import com.ih.osm.features.settings.SettingsScreen
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Serializable
private sealed interface AppRoute : NavKey {
    @Serializable data object Login : AppRoute
    @Serializable data object Main : AppRoute
    @Serializable data class CreateCard(val siteId: Long? = null) : AppRoute
}

private enum class MainTab(val labelRes: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    HOME(R.string.navigation_home, Icons.Outlined.Home, Icons.Rounded.Home),
    CARDS(R.string.navigation_cards, Icons.Outlined.Description, Icons.Rounded.Description),
    SETTINGS(R.string.navigation_settings, Icons.Outlined.Settings, Icons.Rounded.Settings),
}

@Composable
fun OneSmartMateRoot(session: SessionStatus, onExitRequested: () -> Unit) {
    when (session) {
        SessionStatus.Unknown -> SessionLoadingScreen()
        SessionStatus.Unauthenticated -> key(session) { AuthenticationRoot(onExitRequested) }
        is SessionStatus.Authenticated -> key(session.user.id) {
            AuthenticatedRoot(session.user, onExitRequested)
        }
    }
}

@Composable
private fun AuthenticationRoot(onExitRequested: () -> Unit) {
    val backStack = rememberNavBackStack(AppRoute.Login)
    NavDisplay(
        backStack = backStack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        onBack = onExitRequested,
        entryProvider = { route ->
            when (route) {
                AppRoute.Login -> NavEntry(route) { LoginScreenRoute() }
                else -> NavEntry(route) { Box(Modifier.fillMaxSize()) }
            }
        },
    )
}

@Composable
private fun AuthenticatedRoot(user: AuthenticatedUser, onExitRequested: () -> Unit) {
    val backStack = rememberNavBackStack(AppRoute.Main)
    NavDisplay(
        backStack = backStack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        onBack = {
            if (backStack.size > 1) backStack.removeLast() else onExitRequested()
        },
        entryProvider = { route ->
            when (route) {
                AppRoute.Main -> NavEntry(route) {
                    MainTabRoot(user = user, onCreateCard = { backStack.add(AppRoute.CreateCard(it)) })
                }
                is AppRoute.CreateCard -> NavEntry(route) {
                    CreateCardScreenRoute(
                        user = user,
                        siteId = route.siteId,
                        onFinished = { backStack.removeLast() },
                    )
                }
                else -> NavEntry(route) { Box(Modifier.fillMaxSize()) }
            }
        },
    )
}

@Composable
private fun MainTabRoot(
    user: AuthenticatedUser,
    onCreateCard: (Long?) -> Unit,
    catalogSyncScheduler: CatalogSyncScheduler = koinInject(),
    cardSyncScheduler: CardSyncScheduler = koinInject(),
) {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.HOME) }

    LaunchedEffect(user.id, user.sites) {
        catalogSyncScheduler.enqueueIfNeeded(user)
        cardSyncScheduler.enqueueIfPending()
    }

    Scaffold(
        bottomBar = {
            Column {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                ) {
                    MainTab.entries.forEach { tab ->
                        val selected = selectedTab == tab
                        NavigationBarItem(
                            selected = selected,
                            onClick = { selectedTab = tab },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.icon,
                                    contentDescription = null,
                                )
                            },
                            label = { AnatomyText(stringResource(tab.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        when (selectedTab) {
            MainTab.HOME -> HomeScreenRoute(
                user = user,
                onCreateCard = { onCreateCard(it) },
                onOpenNotes = { selectedTab = MainTab.CARDS },
                modifier = Modifier.padding(innerPadding),
            )
            MainTab.CARDS -> CardListScreenRoute(
                user = user,
                onCreateCard = { onCreateCard(user.sites.firstOrNull()?.id) },
                modifier = Modifier.padding(innerPadding),
            )
            MainTab.SETTINGS -> SettingsScreen(Modifier.padding(innerPadding))
        }
    }
}

@Composable
private fun SessionLoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}
