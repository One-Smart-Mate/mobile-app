package com.ih.osm.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlinx.serialization.Serializable

@Serializable
private sealed interface AppRoute : NavKey {
    @Serializable data object Login : AppRoute
    @Serializable data object Home : AppRoute
}

@Composable
fun OneSmartMateRoot(
    session: SessionStatus,
    onExitRequested: () -> Unit,
) {
    when (session) {
        SessionStatus.Unknown -> SessionLoadingScreen()
        SessionStatus.Unauthenticated -> key(session) { AuthenticationRoot(onExitRequested) }
        is SessionStatus.Authenticated -> key(session.user.id) {
            HomeRoot(session.user, onExitRequested)
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
                AppRoute.Home -> NavEntry(route) { Box(Modifier.fillMaxSize()) }
                else -> NavEntry(route) { Box(Modifier.fillMaxSize()) }
            }
        },
    )
}

@Composable
private fun HomeRoot(
    user: AuthenticatedUser,
    onExitRequested: () -> Unit,
) {
    val backStack = rememberNavBackStack(AppRoute.Home)
    NavDisplay(
        backStack = backStack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        onBack = onExitRequested,
        entryProvider = { route ->
            when (route) {
                AppRoute.Home -> NavEntry(route) { HomeScreen(user) }
                AppRoute.Login -> NavEntry(route) { Box(Modifier.fillMaxSize()) }
                else -> NavEntry(route) { Box(Modifier.fillMaxSize()) }
            }
        },
    )
}

@Composable
private fun SessionLoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun HomeScreen(user: AuthenticatedUser) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnatomyText(
            text = stringResource(R.string.home_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        AnatomyText(
            text = stringResource(R.string.home_welcome, user.name),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
