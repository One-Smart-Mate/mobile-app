package com.ih.osm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import com.ih.osm.designsystem.preview.PreviewScreen
import com.ih.osm.designsystem.theme.OneSmartMateTheme
import com.ih.osm.features.auth.login.LoginScreen
import com.ih.osm.features.auth.login.LoginViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            OneSmartMateTheme {
                OneSmartMateApp()
            }
        }
    }
}

@PreviewScreen
@Composable
fun AppAndroidPreview() {
    OneSmartMateTheme {
        LoginScreen(
            uiState = LoginViewModel.UiState(),
            onAction = {},
        )
    }
}
