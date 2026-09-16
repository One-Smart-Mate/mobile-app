package com.ih.osm.designsystem.anatomy

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ih.osm.designsystem.preview.AnatomyPreview
import com.ih.osm.designsystem.preview.PreviewComponent
import com.ih.osm.R

enum class AnatomyCardStyle {
    FILLED,
    OUTLINED,
    ELEVATED,
}

@Composable
fun AnatomyCard(
    modifier: Modifier = Modifier,
    style: AnatomyCardStyle = AnatomyCardStyle.OUTLINED,
    enabled: Boolean = true,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    imageSource: AnatomyImageSource? = null,
    imageContentDescription: String? = null,
    imageHeight: Dp = 156.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(
        containerColor = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            when (style) {
                AnatomyCardStyle.FILLED -> MaterialTheme.colorScheme.surfaceVariant
                AnatomyCardStyle.OUTLINED,
                AnatomyCardStyle.ELEVATED,
                -> MaterialTheme.colorScheme.surface
            }
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    )
    val border = when {
        selected -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        style == AnatomyCardStyle.OUTLINED -> BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
        )
        else -> null
    }
    val elevation = CardDefaults.cardElevation(
        defaultElevation = if (style == AnatomyCardStyle.ELEVATED) 3.dp else 0.dp,
    )
    val resolvedModifier = modifier.alpha(if (enabled) 1f else 0.55f)

    val cardContent: @Composable ColumnScope.() -> Unit = {
        imageSource?.let {
            AnatomyImage(
                source = it,
                contentDescription = imageContentDescription,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(imageHeight),
                shape = MaterialTheme.shapes.medium,
            )
        }
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content,
        )
    }

    if (onClick != null) {
        Card(
            onClick = onClick,
            enabled = enabled,
            modifier = resolvedModifier,
            shape = MaterialTheme.shapes.medium,
            colors = colors,
            border = border,
            elevation = elevation,
            content = cardContent,
        )
    } else {
        Card(
            modifier = resolvedModifier,
            shape = MaterialTheme.shapes.medium,
            colors = colors,
            border = border,
            elevation = elevation,
            content = cardContent,
        )
    }
}

@PreviewComponent
@Composable
private fun AnatomyCardPreview() = AnatomyPreview {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AnatomyCard(
            imageSource = AnatomyImageSource.Resource(R.drawable.anatomy_preview_equipment),
            imageContentDescription = "Motor industrial",
            onClick = {},
        ) {
            AnatomyText("Motor M-204", style = MaterialTheme.typography.titleMedium)
            AnatomyText(
                text = "Empacadora · Línea 2",
                style = MaterialTheme.typography.bodyMedium,
                properties = AnatomyTextProperties(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
        AnatomyCard(selected = true, onClick = {}) {
            AnatomyText("Tarjeta seleccionada", style = MaterialTheme.typography.titleMedium)
            AnatomyText("Estado y contenido destacados sin perder contraste.")
        }
    }
}
