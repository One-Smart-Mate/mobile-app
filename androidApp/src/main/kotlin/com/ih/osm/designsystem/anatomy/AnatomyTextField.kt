package com.ih.osm.designsystem.anatomy

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.ih.osm.designsystem.preview.AnatomyPreview
import com.ih.osm.designsystem.preview.PreviewComponent

@Composable
fun AnatomyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    accessibilityLabel: String? = null,
    autoFocus: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val colorScheme = MaterialTheme.colorScheme

    val targetContainerColor = when {
        !enabled -> colorScheme.surfaceVariant.copy(alpha = 0.55f)
        isError -> colorScheme.errorContainer.copy(alpha = 0.42f)
        isFocused -> colorScheme.primaryContainer.copy(alpha = 0.48f)
        else -> colorScheme.surfaceVariant
    }
    val targetAccentColor = when {
        !enabled -> Color.Transparent
        isError -> colorScheme.error
        isFocused -> colorScheme.primary
        else -> Color.Transparent
    }
    val labelColor = when {
        !enabled -> colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        isError -> colorScheme.error
        isFocused -> colorScheme.primary
        else -> colorScheme.onSurface
    }
    val contentColor = if (enabled) {
        colorScheme.onSurface
    } else {
        colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    }
    val iconColor = when {
        !enabled -> colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        isError -> colorScheme.error
        isFocused -> colorScheme.primary
        else -> colorScheme.onSurfaceVariant
    }
    val containerColor by animateColorAsState(
        targetValue = targetContainerColor,
        label = "AnatomyTextFieldContainer",
    )
    val accentColor by animateColorAsState(
        targetValue = targetAccentColor,
        label = "AnatomyTextFieldAccent",
    )

    LaunchedEffect(autoFocus) {
        if (autoFocus) focusRequester.requestFocus()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                accessibilityLabel?.let { labelValue ->
                    Modifier.semantics { contentDescription = labelValue }
                } ?: Modifier,
            ),
    ) {
        label?.let {
            AnatomyText(
                text = it,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                properties = AnatomyTextProperties(color = labelColor),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(containerColor)
                .drawBehind {
                    drawRect(
                        color = accentColor,
                        size = size.copy(width = 4.dp.toPx()),
                    )
                }
                .padding(start = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leadingIcon?.let { icon ->
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .heightIn(min = 56.dp)
                        .background(colorScheme.onSurface.copy(alpha = 0.025f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CompositionLocalProvider(LocalContentColor provides iconColor) {
                        icon()
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 15.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    enabled = enabled,
                    readOnly = readOnly,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = contentColor),
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    singleLine = singleLine,
                    minLines = minLines,
                    maxLines = maxLines,
                    visualTransformation = visualTransformation,
                    interactionSource = interactionSource,
                    cursorBrush = SolidColor(if (isError) colorScheme.error else colorScheme.primary),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (value.isEmpty() && placeholder != null) {
                                AnatomyText(
                                    text = placeholder,
                                    properties = AnatomyTextProperties(
                                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                    ),
                                )
                            }
                            innerTextField()
                        }
                    },
                )
            }

            trailingIcon?.let { icon ->
                Box(
                    modifier = Modifier.width(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CompositionLocalProvider(LocalContentColor provides iconColor) {
                        icon()
                    }
                }
            }
        }

        supportingText?.let {
            AnatomyText(
                text = it,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                properties = AnatomyTextProperties(
                    color = if (isError) colorScheme.error else colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

@PreviewComponent
@Composable
private fun AnatomyTextFieldPreview() = AnatomyPreview {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AnatomyTextField(
            value = "carlos@empresa.com",
            onValueChange = {},
            label = "Correo electrónico",
            placeholder = "nombre@empresa.com",
            autoFocus = true,
            leadingIcon = {
                Icon(Icons.Outlined.Email, contentDescription = null)
            },
        )
        AnatomyTextField(
            value = "correo-invalido",
            onValueChange = {},
            label = "Correo electrónico",
            supportingText = "Correo no válido",
            isError = true,
            leadingIcon = {
                Icon(Icons.Outlined.Email, contentDescription = null)
            },
        )
        AnatomyTextField(
            value = "No disponible",
            onValueChange = {},
            label = "Correo electrónico",
            enabled = false,
            leadingIcon = {
                Icon(Icons.Outlined.Email, contentDescription = null)
            },
        )
    }
}
