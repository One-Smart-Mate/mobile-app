package com.ih.osm.designsystem.anatomy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ih.osm.designsystem.preview.AnatomyPreview
import com.ih.osm.designsystem.preview.PreviewComponent

enum class AnatomyBannerType {
    ERROR,
    WARNING,
    INFO,
    SUCCESS,
}

@Composable
fun AnatomyBanner(
    message: String,
    type: AnatomyBannerType,
    modifier: Modifier = Modifier,
    title: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    dismissContentDescription: String = "Cerrar",
) {
    val palette = anatomyBannerPalette(type)
    val icon = type.icon
    val liveRegionMode = if (type == AnatomyBannerType.ERROR) {
        LiveRegionMode.Assertive
    } else {
        LiveRegionMode.Polite
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(palette.container)
            .drawBehind {
                drawRect(
                    color = palette.accent,
                    size = size.copy(width = 4.dp.toPx()),
                )
            }
            .semantics { liveRegion = liveRegionMode }
            .padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(palette.accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = palette.accent,
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            title?.let {
                AnatomyText(
                    text = it,
                    style = MaterialTheme.typography.labelLarge,
                    properties = AnatomyTextProperties(
                        color = palette.content,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }
            AnatomyText(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                properties = AnatomyTextProperties(color = palette.content),
            )
        }

        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.width(4.dp))
            TextButton(
                onClick = onAction,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = palette.accent),
            ) {
                AnatomyText(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    properties = AnatomyTextProperties(
                        color = palette.accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }
        }

        onDismiss?.let { dismiss ->
            IconButton(
                onClick = dismiss,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = dismissContentDescription,
                    modifier = Modifier.size(18.dp),
                    tint = palette.content.copy(alpha = 0.72f),
                )
            }
        }
    }
}

private val AnatomyBannerType.icon: ImageVector
    get() = when (this) {
        AnatomyBannerType.ERROR -> Icons.Outlined.ErrorOutline
        AnatomyBannerType.WARNING -> Icons.Outlined.WarningAmber
        AnatomyBannerType.INFO -> Icons.Outlined.Info
        AnatomyBannerType.SUCCESS -> Icons.Outlined.CheckCircleOutline
    }

@Immutable
private data class AnatomyBannerPalette(
    val container: Color,
    val content: Color,
    val accent: Color,
)

@Composable
private fun anatomyBannerPalette(type: AnatomyBannerType): AnatomyBannerPalette {
    val colors = MaterialTheme.colorScheme
    val isDark = colors.background.luminance() < 0.5f

    return when (type) {
        AnatomyBannerType.ERROR -> AnatomyBannerPalette(
            container = colors.errorContainer.copy(alpha = if (isDark) 0.42f else 0.72f),
            content = colors.onErrorContainer,
            accent = colors.error,
        )

        AnatomyBannerType.WARNING -> if (isDark) {
            AnatomyBannerPalette(
                container = Color(0xFF3A301A),
                content = Color(0xFFFFE2A3),
                accent = Color(0xFFFFC857),
            )
        } else {
            AnatomyBannerPalette(
                container = Color(0xFFFFF3D6),
                content = Color(0xFF574100),
                accent = Color(0xFFB87500),
            )
        }

        AnatomyBannerType.INFO -> if (isDark) {
            AnatomyBannerPalette(
                container = Color(0xFF1D3049),
                content = Color(0xFFD7E7FF),
                accent = Color(0xFF8AB4F8),
            )
        } else {
            AnatomyBannerPalette(
                container = Color(0xFFE8F1FF),
                content = Color(0xFF173E73),
                accent = Color(0xFF2F6FD6),
            )
        }

        AnatomyBannerType.SUCCESS -> AnatomyBannerPalette(
            container = colors.primaryContainer.copy(alpha = if (isDark) 0.52f else 0.72f),
            content = colors.onPrimaryContainer,
            accent = colors.primary,
        )
    }
}

@PreviewComponent
@Composable
private fun AnatomyBannerPreview() = AnatomyPreview {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AnatomyBanner(
            type = AnatomyBannerType.ERROR,
            title = "No se guardaron los cambios",
            message = "Revisa tu conexión e inténtalo nuevamente.",
            actionLabel = "Reintentar",
            onAction = {},
            onDismiss = {},
        )
        AnatomyBanner(
            type = AnatomyBannerType.WARNING,
            message = "Revisa la información ingresada.",
            onDismiss = {},
        )
        AnatomyBanner(
            type = AnatomyBannerType.INFO,
            message = "Nueva actualización disponible.",
            onDismiss = {},
        )
        AnatomyBanner(
            type = AnatomyBannerType.SUCCESS,
            message = "Cambios guardados correctamente.",
        )
    }
}
