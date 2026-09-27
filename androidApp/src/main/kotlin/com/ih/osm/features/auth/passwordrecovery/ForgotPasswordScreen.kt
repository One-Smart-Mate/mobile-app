package com.ih.osm.features.auth.passwordrecovery

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.input.KeyboardCapitalization
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
import com.ih.osm.features.auth.passwordrecovery.domain.manager.PasswordRecoveryValidationError
import kotlinx.coroutines.flow.collect
import org.koin.androidx.compose.koinViewModel

@Composable
fun ForgotPasswordScreenRoute(
    initialEmail: String,
    onBack: () -> Unit,
    onFinished: () -> Unit,
    viewModel: ForgotPasswordViewModel = koinViewModel(),
) {
    val uiState by viewModel.getStateFlow().collectAsStateWithLifecycle()
    val currentOnBack by rememberUpdatedState(onBack)
    val currentOnFinished by rememberUpdatedState(onFinished)

    LaunchedEffect(initialEmail) {
        viewModel.process(ForgotPasswordViewModel.Action.Initialize(initialEmail))
    }
    LaunchedEffect(viewModel) {
        viewModel.getEventFlow().collect { event ->
            when (event) {
                ForgotPasswordViewModel.Event.NavigateBack -> currentOnBack()
                ForgotPasswordViewModel.Event.Finished -> currentOnFinished()
            }
        }
    }
    BackHandler { viewModel.process(ForgotPasswordViewModel.Action.BackClicked) }

    ForgotPasswordScreen(uiState = uiState, onAction = viewModel::process)
}

@Composable
fun ForgotPasswordScreen(
    uiState: ForgotPasswordViewModel.UiState,
    onAction: (ForgotPasswordViewModel.Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSuccessBanner = uiState.bannerMessage == ForgotPasswordViewModel.BannerMessage.CODE_SENT

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { onAction(ForgotPasswordViewModel.Action.BackClicked) },
                    enabled = !uiState.isLoading,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.navigation_back),
                    )
                }
                AnatomyText(
                    text = stringResource(R.string.forgot_password_screen_title),
                    style = MaterialTheme.typography.titleLarge,
                    properties = AnatomyTextProperties(fontWeight = FontWeight.SemiBold),
                )
            }

            Spacer(Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 440.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (uiState.step != ForgotPasswordViewModel.Step.SUCCESS) {
                    RecoveryProgress(uiState.step)
                    Spacer(Modifier.height(28.dp))
                }

                RecoveryHeader(uiState.step)

                uiState.bannerMessage?.let { message ->
                    Spacer(Modifier.height(20.dp))
                    AnatomyBanner(
                        message = bannerText(message),
                        type = if (isSuccessBanner) AnatomyBannerType.SUCCESS else AnatomyBannerType.ERROR,
                        onDismiss = { onAction(ForgotPasswordViewModel.Action.DismissBanner) },
                    )
                }

                Spacer(Modifier.height(28.dp))

                when (uiState.step) {
                    ForgotPasswordViewModel.Step.EMAIL -> EmailStep(uiState, onAction)
                    ForgotPasswordViewModel.Step.CODE -> CodeStep(uiState, onAction)
                    ForgotPasswordViewModel.Step.PASSWORD -> PasswordStep(uiState, onAction)
                    ForgotPasswordViewModel.Step.SUCCESS -> SuccessStep()
                }

                Spacer(Modifier.height(28.dp))

                AnatomyButton(
                    text = primaryButtonText(uiState.step),
                    onClick = { onAction(ForgotPasswordViewModel.Action.SubmitClicked) },
                    enabled = !uiState.isLoading,
                    isLoading = uiState.isLoading,
                )

                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun RecoveryProgress(step: ForgotPasswordViewModel.Step) {
    val stepNumber = when (step) {
        ForgotPasswordViewModel.Step.EMAIL -> 1
        ForgotPasswordViewModel.Step.CODE -> 2
        ForgotPasswordViewModel.Step.PASSWORD -> 3
        ForgotPasswordViewModel.Step.SUCCESS -> 3
    }
    AnatomyText(
        text = stringResource(R.string.forgot_password_step, stepNumber, 3),
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.labelMedium,
        properties = AnatomyTextProperties(color = MaterialTheme.colorScheme.onSurfaceVariant),
    )
    Spacer(Modifier.height(8.dp))
    LinearProgressIndicator(
        progress = { stepNumber / 3f },
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
    )
}

@Composable
private fun RecoveryHeader(step: ForgotPasswordViewModel.Step) {
    val icon = when (step) {
        ForgotPasswordViewModel.Step.EMAIL -> Icons.Outlined.Email
        ForgotPasswordViewModel.Step.CODE -> Icons.Outlined.Key
        ForgotPasswordViewModel.Step.PASSWORD -> Icons.Outlined.Lock
        ForgotPasswordViewModel.Step.SUCCESS -> Icons.Outlined.CheckCircleOutline
    }
    Box(
        modifier = Modifier
            .size(68.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
    Spacer(Modifier.height(18.dp))
    AnatomyText(
        text = stepTitle(step),
        style = MaterialTheme.typography.headlineSmall,
        properties = AnatomyTextProperties(
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
        ),
    )
    Spacer(Modifier.height(6.dp))
    AnatomyText(
        text = stepSubtitle(step),
        style = MaterialTheme.typography.bodyMedium,
        properties = AnatomyTextProperties(
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        ),
    )
}

@Composable
private fun EmailStep(
    uiState: ForgotPasswordViewModel.UiState,
    onAction: (ForgotPasswordViewModel.Action) -> Unit,
) {
    AnatomyTextField(
        value = uiState.email,
        onValueChange = { onAction(ForgotPasswordViewModel.Action.EmailChanged(it)) },
        label = stringResource(R.string.login_email_label),
        placeholder = stringResource(R.string.login_email_placeholder),
        supportingText = validationText(uiState.emailError),
        isError = uiState.emailError != null,
        enabled = !uiState.isLoading,
        autoFocus = uiState.email.isBlank(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(
            onDone = { onAction(ForgotPasswordViewModel.Action.SubmitClicked) },
        ),
        leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null) },
    )
}

@Composable
private fun CodeStep(
    uiState: ForgotPasswordViewModel.UiState,
    onAction: (ForgotPasswordViewModel.Action) -> Unit,
) {
    AnatomyText(
        text = stringResource(R.string.forgot_password_code_sent_to, uiState.email),
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodySmall,
        properties = AnatomyTextProperties(color = MaterialTheme.colorScheme.onSurfaceVariant),
    )
    Spacer(Modifier.height(14.dp))
    AnatomyTextField(
        value = uiState.code,
        onValueChange = { onAction(ForgotPasswordViewModel.Action.CodeChanged(it)) },
        label = stringResource(R.string.forgot_password_code_label),
        placeholder = stringResource(R.string.forgot_password_code_placeholder),
        supportingText = validationText(uiState.codeError),
        isError = uiState.codeError != null,
        enabled = !uiState.isLoading,
        autoFocus = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Ascii,
            capitalization = KeyboardCapitalization.Characters,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(
            onDone = { onAction(ForgotPasswordViewModel.Action.SubmitClicked) },
        ),
        leadingIcon = { Icon(Icons.Outlined.Key, contentDescription = null) },
    )
    Spacer(Modifier.height(8.dp))
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterEnd,
    ) {
        TextButton(
            onClick = { onAction(ForgotPasswordViewModel.Action.ResendCodeClicked) },
            enabled = !uiState.isLoading && uiState.resendSeconds == 0,
        ) {
            AnatomyText(
                text = if (uiState.resendSeconds > 0) {
                    stringResource(R.string.forgot_password_resend_countdown, uiState.resendSeconds)
                } else {
                    stringResource(R.string.forgot_password_resend)
                },
                style = MaterialTheme.typography.labelLarge,
                properties = AnatomyTextProperties(
                    color = if (uiState.resendSeconds == 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                ),
            )
        }
    }
}

@Composable
private fun PasswordStep(
    uiState: ForgotPasswordViewModel.UiState,
    onAction: (ForgotPasswordViewModel.Action) -> Unit,
) {
    AnatomyTextFieldPassword(
        value = uiState.password,
        onValueChange = { onAction(ForgotPasswordViewModel.Action.PasswordChanged(it)) },
        label = stringResource(R.string.forgot_password_new_password),
        placeholder = stringResource(R.string.forgot_password_password_placeholder),
        supportingText = validationText(uiState.passwordError),
        isError = uiState.passwordError != null,
        enabled = !uiState.isLoading,
    )
    Spacer(Modifier.height(18.dp))
    AnatomyTextFieldPassword(
        value = uiState.passwordConfirmation,
        onValueChange = {
            onAction(ForgotPasswordViewModel.Action.PasswordConfirmationChanged(it))
        },
        label = stringResource(R.string.forgot_password_confirm_password),
        placeholder = stringResource(R.string.forgot_password_confirm_placeholder),
        supportingText = validationText(uiState.confirmationError),
        isError = uiState.confirmationError != null,
        enabled = !uiState.isLoading,
        keyboardActions = KeyboardActions(
            onDone = { onAction(ForgotPasswordViewModel.Action.SubmitClicked) },
        ),
    )
}

@Composable
private fun SuccessStep() {
    AnatomyText(
        text = stringResource(R.string.forgot_password_success_support),
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyMedium,
        properties = AnatomyTextProperties(
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        ),
    )
}

@Composable
private fun stepTitle(step: ForgotPasswordViewModel.Step): String = stringResource(
    when (step) {
        ForgotPasswordViewModel.Step.EMAIL -> R.string.forgot_password_email_title
        ForgotPasswordViewModel.Step.CODE -> R.string.forgot_password_code_title
        ForgotPasswordViewModel.Step.PASSWORD -> R.string.forgot_password_password_title
        ForgotPasswordViewModel.Step.SUCCESS -> R.string.forgot_password_success_title
    },
)

@Composable
private fun stepSubtitle(step: ForgotPasswordViewModel.Step): String = stringResource(
    when (step) {
        ForgotPasswordViewModel.Step.EMAIL -> R.string.forgot_password_email_subtitle
        ForgotPasswordViewModel.Step.CODE -> R.string.forgot_password_code_subtitle
        ForgotPasswordViewModel.Step.PASSWORD -> R.string.forgot_password_password_subtitle
        ForgotPasswordViewModel.Step.SUCCESS -> R.string.forgot_password_success_subtitle
    },
)

@Composable
private fun primaryButtonText(step: ForgotPasswordViewModel.Step): String = stringResource(
    when (step) {
        ForgotPasswordViewModel.Step.EMAIL -> R.string.forgot_password_send_code
        ForgotPasswordViewModel.Step.CODE -> R.string.forgot_password_verify_code
        ForgotPasswordViewModel.Step.PASSWORD -> R.string.forgot_password_update_password
        ForgotPasswordViewModel.Step.SUCCESS -> R.string.forgot_password_back_to_login
    },
)

@Composable
private fun validationText(error: PasswordRecoveryValidationError?): String? = error?.let {
    stringResource(
        when (it) {
            PasswordRecoveryValidationError.EMAIL_REQUIRED -> R.string.login_email_required
            PasswordRecoveryValidationError.EMAIL_INVALID -> R.string.login_email_invalid
            PasswordRecoveryValidationError.CODE_INVALID -> R.string.forgot_password_code_invalid
            PasswordRecoveryValidationError.PASSWORD_REQUIRED -> R.string.login_password_required
            PasswordRecoveryValidationError.PASSWORD_TOO_SHORT -> R.string.login_password_too_short
            PasswordRecoveryValidationError.CONFIRMATION_REQUIRED ->
                R.string.forgot_password_confirmation_required
            PasswordRecoveryValidationError.PASSWORDS_DO_NOT_MATCH ->
                R.string.forgot_password_passwords_do_not_match
        },
    )
}

@Composable
private fun bannerText(message: ForgotPasswordViewModel.BannerMessage): String = stringResource(
    when (message) {
        ForgotPasswordViewModel.BannerMessage.CODE_SENT -> R.string.forgot_password_code_sent
        ForgotPasswordViewModel.BannerMessage.INVALID_CODE -> R.string.forgot_password_code_invalid
        ForgotPasswordViewModel.BannerMessage.EXPIRED_CODE -> R.string.forgot_password_code_expired
        ForgotPasswordViewModel.BannerMessage.TOO_MANY_ATTEMPTS ->
            R.string.forgot_password_too_many_attempts
        ForgotPasswordViewModel.BannerMessage.NO_CONNECTION -> R.string.forgot_password_no_connection
        ForgotPasswordViewModel.BannerMessage.REQUEST_FAILED -> R.string.forgot_password_request_failed
    },
)

@PreviewScreen
@Composable
private fun ForgotPasswordScreenPreview() {
    OneSmartMateTheme {
        ForgotPasswordScreen(
            uiState = ForgotPasswordViewModel.UiState(
                step = ForgotPasswordViewModel.Step.CODE,
                email = "nombre@empresa.com",
            ),
            onAction = {},
        )
    }
}
