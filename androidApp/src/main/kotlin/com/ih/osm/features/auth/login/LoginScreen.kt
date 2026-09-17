package com.ih.osm.features.auth.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ih.osm.R
import com.ih.osm.designsystem.anatomy.AnatomyBanner
import com.ih.osm.designsystem.anatomy.AnatomyBannerType
import com.ih.osm.designsystem.anatomy.AnatomyButton
import com.ih.osm.designsystem.anatomy.AnatomyText
import com.ih.osm.designsystem.anatomy.AnatomyTextField
import com.ih.osm.designsystem.anatomy.AnatomyTextFieldPassword
import com.ih.osm.designsystem.anatomy.AnatomyTextProperties
import com.ih.osm.designsystem.preview.PreviewScreen
import com.ih.osm.designsystem.theme.OneSmartMateTheme
import kotlinx.coroutines.flow.collect
import org.koin.androidx.compose.koinViewModel

@Composable
fun LoginScreenRoute(
    viewModel: LoginViewModel = koinViewModel(),
    onForgotPasswordClick: (email: String) -> Unit = {},
) {
    val uiState by viewModel.getStateFlow().collectAsStateWithLifecycle()
    val currentOnForgotPasswordClick by rememberUpdatedState(onForgotPasswordClick)

    LaunchedEffect(viewModel) {
        viewModel.getEventFlow().collect { event ->
            when (event) {
                is LoginViewModel.Event.ForgotPasswordRequested -> {
                    currentOnForgotPasswordClick(event.email)
                }
            }
        }
    }

    LoginScreen(
        uiState = uiState,
        onAction = viewModel::process,
    )
}

@Composable
fun LoginScreen(
    uiState: LoginViewModel.UiState,
    onAction: (LoginViewModel.Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    val emailSupportingText = when (uiState.emailError) {
        LoginViewModel.EmailError.REQUIRED -> stringResource(R.string.login_email_required)
        LoginViewModel.EmailError.INVALID -> stringResource(R.string.login_email_invalid)
        null -> null
    }
    val passwordSupportingText = when (uiState.passwordError) {
        LoginViewModel.PasswordError.REQUIRED -> stringResource(R.string.login_password_required)
        LoginViewModel.PasswordError.TOO_SHORT -> stringResource(R.string.login_password_too_short)
        null -> null
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            uiState.bannerMessage?.let { message ->
                AnatomyBanner(
                    message = message,
                    type = AnatomyBannerType.ERROR,
                    onDismiss = { onAction(LoginViewModel.Action.DismissBanner) },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .widthIn(max = 480.dp),
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .widthIn(max = 440.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BrandHeader()

                Spacer(Modifier.height(40.dp))

                AnatomyText(
                    text = stringResource(R.string.login_title),
                    style = MaterialTheme.typography.headlineMedium,
                    properties = AnatomyTextProperties(
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Spacer(Modifier.height(6.dp))
                AnatomyText(
                    text = stringResource(R.string.login_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    properties = AnatomyTextProperties(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    ),
                )

                Spacer(Modifier.height(32.dp))

                AnatomyTextField(
                    value = uiState.email,
                    onValueChange = { onAction(LoginViewModel.Action.EmailChanged(it)) },
                    label = stringResource(R.string.login_email_label),
                    placeholder = stringResource(R.string.login_email_placeholder),
                    supportingText = emailSupportingText,
                    isError = uiState.emailError != null,
                    enabled = !uiState.isLoading,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                    ),
                    leadingIcon = {
                        Icon(Icons.Outlined.Email, contentDescription = null)
                    },
                )

                Spacer(Modifier.height(18.dp))

                AnatomyTextFieldPassword(
                    value = uiState.password,
                    onValueChange = { onAction(LoginViewModel.Action.PasswordChanged(it)) },
                    label = stringResource(R.string.login_password_label),
                    placeholder = stringResource(R.string.login_password_placeholder),
                    supportingText = passwordSupportingText,
                    isError = uiState.passwordError != null,
                    enabled = !uiState.isLoading,
                    keyboardActions = KeyboardActions(
                        onDone = { onAction(LoginViewModel.Action.LoginClicked) },
                    ),
                )

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    TextButton(
                        onClick = { onAction(LoginViewModel.Action.ForgotPasswordClicked) },
                        enabled = !uiState.isLoading,
                    ) {
                        AnatomyText(
                            text = stringResource(R.string.login_forgot_password),
                            style = MaterialTheme.typography.labelLarge,
                            properties = AnatomyTextProperties(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                AnatomyButton(
                    text = stringResource(R.string.login_submit),
                    onClick = { onAction(LoginViewModel.Action.LoginClicked) },
                    enabled = !uiState.isLoading,
                    isLoading = uiState.isLoading,
                )

                Spacer(Modifier.height(24.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    )
                    AnatomyText(
                        text = stringResource(R.string.login_secure_access),
                        style = MaterialTheme.typography.bodySmall,
                        properties = AnatomyTextProperties(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun BrandHeader() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.VerifiedUser,
                contentDescription = null,
                modifier = Modifier.size(34.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(14.dp))
        AnatomyText(
            text = stringResource(R.string.login_brand_name),
            style = MaterialTheme.typography.titleLarge,
            properties = AnatomyTextProperties(fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.height(2.dp))
        AnatomyText(
            text = stringResource(R.string.login_brand_tagline),
            style = MaterialTheme.typography.bodySmall,
            properties = AnatomyTextProperties(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
    }
}

@PreviewScreen
@Composable
private fun LoginScreenPreview() {
    OneSmartMateTheme {
        LoginScreen(
            uiState = LoginViewModel.UiState(),
            onAction = {},
        )
    }
}
