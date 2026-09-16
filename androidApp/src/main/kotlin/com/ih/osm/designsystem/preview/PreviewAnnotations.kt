package com.ih.osm.designsystem.preview

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ih.osm.designsystem.theme.OneSmartMateTheme

@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
@Preview(
    name = "Light",
    group = "Theme",
    uiMode = Configuration.UI_MODE_NIGHT_NO,
    widthDp = 390,
    showBackground = true,
    backgroundColor = 0xFFF8FAF7,
)
@Preview(
    name = "Dark",
    group = "Theme",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    widthDp = 390,
    showBackground = true,
    backgroundColor = 0xFF101410,
)
annotation class PreviewComponent

@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
@Preview(
    name = "Light screen",
    group = "Screen",
    uiMode = Configuration.UI_MODE_NIGHT_NO,
    widthDp = 390,
    heightDp = 844,
    showBackground = true,
    backgroundColor = 0xFFF8FAF7,
)
@Preview(
    name = "Dark screen",
    group = "Screen",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    widthDp = 390,
    heightDp = 844,
    showBackground = true,
    backgroundColor = 0xFF101410,
)
annotation class PreviewScreen

@Composable
fun AnatomyPreview(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit,
) {
    OneSmartMateTheme {
        Surface(
            modifier = modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(modifier = Modifier.padding(contentPadding)) {
                content()
            }
        }
    }
}
