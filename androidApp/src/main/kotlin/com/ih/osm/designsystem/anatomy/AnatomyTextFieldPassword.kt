package com.ih.osm.designsystem.anatomy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.ih.osm.designsystem.preview.AnatomyPreview
import com.ih.osm.designsystem.preview.PreviewComponent

@Composable
fun AnatomyTextFieldPassword(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Contraseña",
    placeholder: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    showPasswordContentDescription: String = "Mostrar contraseña",
    hidePasswordContentDescription: String = "Ocultar contraseña",
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    AnatomyTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        supportingText = supportingText,
        isError = isError,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = keyboardActions,
        visualTransformation = if (passwordVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        leadingIcon = {
            Icon(Icons.Outlined.Lock, contentDescription = null)
        },
        trailingIcon = {
            IconButton(
                onClick = { passwordVisible = !passwordVisible },
                enabled = enabled,
            ) {
                Icon(
                    imageVector = if (passwordVisible) {
                        Icons.Outlined.VisibilityOff
                    } else {
                        Icons.Outlined.Visibility
                    },
                    contentDescription = if (passwordVisible) {
                        hidePasswordContentDescription
                    } else {
                        showPasswordContentDescription
                    },
                )
            }
        },
    )
}

@PreviewComponent
@Composable
private fun AnatomyTextFieldPasswordPreview() = AnatomyPreview {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AnatomyTextFieldPassword(
            value = "password",
            onValueChange = {},
            placeholder = "Ingresa tu contraseña",
        )
        AnatomyTextFieldPassword(
            value = "password",
            onValueChange = {},
            supportingText = "La contraseña no es correcta",
            isError = true,
        )
    }
}
