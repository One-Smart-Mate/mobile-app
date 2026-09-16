package com.ih.osm.designsystem.anatomy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ih.osm.designsystem.preview.AnatomyPreview
import com.ih.osm.designsystem.preview.PreviewComponent

@Immutable
data class AnatomyTextProperties(
    val color: Color? = null,
    val isError: Boolean = false,
    val textAlign: TextAlign? = null,
    val maxLines: Int = Int.MAX_VALUE,
    val overflow: TextOverflow = TextOverflow.Clip,
    val fontWeight: FontWeight? = null,
    val textDecoration: TextDecoration? = null,
)

@Composable
fun AnatomyText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    properties: AnatomyTextProperties = AnatomyTextProperties(),
) {
    val color = when {
        properties.isError -> MaterialTheme.colorScheme.error
        properties.color != null -> properties.color
        else -> Color.Unspecified
    }

    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = style,
        textAlign = properties.textAlign,
        maxLines = properties.maxLines,
        overflow = properties.overflow,
        fontWeight = properties.fontWeight,
        textDecoration = properties.textDecoration,
    )
}

@PreviewComponent
@Composable
private fun AnatomyTextPreview() = AnatomyPreview {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AnatomyText("Título principal", style = MaterialTheme.typography.headlineMedium)
        AnatomyText("Título de sección", style = MaterialTheme.typography.titleMedium)
        AnatomyText("Texto de contenido claro y fácil de leer.")
        AnatomyText(
            text = "Información secundaria",
            style = MaterialTheme.typography.bodySmall,
            properties = AnatomyTextProperties(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
        AnatomyText(
            text = "Mensaje de error",
            style = MaterialTheme.typography.bodySmall,
            properties = AnatomyTextProperties(isError = true),
        )
    }
}
