package com.ih.osm.designsystem.anatomy

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ih.osm.designsystem.preview.AnatomyPreview
import com.ih.osm.designsystem.preview.PreviewComponent

enum class AnatomyButtonStyle {
    PRIMARY,
    SECONDARY,
    TERTIARY,
    DESTRUCTIVE,
}

enum class AnatomyButtonSize(val height: Dp) {
    SMALL(40.dp),
    MEDIUM(48.dp),
    LARGE(56.dp),
}

@Composable
fun AnatomyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: AnatomyButtonStyle = AnatomyButtonStyle.PRIMARY,
    size: AnatomyButtonSize = AnatomyButtonSize.MEDIUM,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    isFullWidth: Boolean = true,
    shape: Shape = MaterialTheme.shapes.medium,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
) {
    val actualEnabled = enabled && !isLoading
    val sizedModifier = modifier
        .then(if (isFullWidth) Modifier.fillMaxWidth() else Modifier.wrapContentWidth())
        .height(size.height)

    val content: @Composable () -> Unit = {
        AnatomyButtonContent(
            text = text,
            isLoading = isLoading,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
        )
    }

    when (style) {
        AnatomyButtonStyle.PRIMARY -> Button(
            onClick = onClick,
            enabled = actualEnabled,
            modifier = sizedModifier,
            shape = shape,
            content = { content() },
        )

        AnatomyButtonStyle.SECONDARY -> OutlinedButton(
            onClick = onClick,
            enabled = actualEnabled,
            modifier = sizedModifier,
            shape = shape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.primary,
            ),
            content = { content() },
        )

        AnatomyButtonStyle.TERTIARY -> TextButton(
            onClick = onClick,
            enabled = actualEnabled,
            modifier = sizedModifier,
            shape = shape,
            content = { content() },
        )

        AnatomyButtonStyle.DESTRUCTIVE -> Button(
            onClick = onClick,
            enabled = actualEnabled,
            modifier = sizedModifier,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
            content = { content() },
        )
    }
}

@Composable
private fun AnatomyButtonContent(
    text: String,
    isLoading: Boolean,
    leadingIcon: ImageVector?,
    trailingIcon: ImageVector?,
) {
    if (isLoading) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            color = LocalContentColor.current,
            strokeWidth = 2.dp,
        )
        return
    }

    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leadingIcon?.let {
            Icon(imageVector = it, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        AnatomyText(text = text, style = MaterialTheme.typography.labelLarge)
        trailingIcon?.let {
            Spacer(Modifier.width(8.dp))
            Icon(imageVector = it, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

@PreviewComponent
@Composable
private fun AnatomyButtonPreview() = AnatomyPreview {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AnatomyButton("Acción principal", onClick = {})
        AnatomyButton(
            text = "Acción secundaria",
            onClick = {},
            style = AnatomyButtonStyle.SECONDARY,
        )
        AnatomyButton(
            text = "Continuar",
            onClick = {},
            style = AnatomyButtonStyle.TERTIARY,
            trailingIcon = Icons.AutoMirrored.Rounded.ArrowForward,
        )
        AnatomyButton(
            text = "Eliminar",
            onClick = {},
            style = AnatomyButtonStyle.DESTRUCTIVE,
        )
        AnatomyButton("Cargando", onClick = {}, isLoading = true)
        AnatomyButton("Deshabilitado", onClick = {}, enabled = false)
    }
}
