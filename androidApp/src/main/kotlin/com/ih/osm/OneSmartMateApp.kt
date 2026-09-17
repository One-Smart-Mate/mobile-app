package com.ih.osm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ih.osm.navigation.OneSmartMateRoot
import org.koin.androidx.compose.koinViewModel

@Composable
fun OneSmartMateApp(
    viewModel: AppViewModel = koinViewModel(),
    onExitRequested: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    OneSmartMateRoot(
        session = uiState.session,
        onExitRequested = onExitRequested,
    )
}
