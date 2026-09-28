import SwiftUI
import SharedLogic

@MainActor
struct ForgotPasswordView: View {
    private enum Field: Hashable {
        case email
        case code
        case password
        case confirmation
    }

    @State private var viewModel: ForgotPasswordViewModel
    @FocusState private var focusedField: Field?
    let onClose: () -> Void

    init(
        viewModel: @autoclosure @escaping () -> ForgotPasswordViewModel,
        onClose: @escaping () -> Void
    ) {
        _viewModel = State(initialValue: viewModel())
        self.onClose = onClose
    }

    var body: some View {
        GeometryReader { geometry in
            ScrollView {
                VStack(spacing: 0) {
                    if viewModel.step != .success {
                        stepProgress
                        Spacer().frame(height: OSMSpacing.xl)
                    }

                    recoveryHeader

                    if let banner = viewModel.banner {
                        Spacer().frame(height: OSMSpacing.lg)
                        AnatomyBanner(
                            message: bannerMessage(banner),
                            type: banner.type,
                            onDismiss: viewModel.dismissBanner
                        )
                    }

                    Spacer().frame(height: OSMSpacing.xl)
                    stepContent
                    Spacer().frame(height: OSMSpacing.xl)

                    AnatomyButton(
                        title: primaryButtonTitle,
                        action: primaryAction,
                        size: .large,
                        isLoading: viewModel.isLoading
                    )
                    .disabled(viewModel.isLoading)
                }
                .padding(.horizontal, OSMSpacing.lg)
                .padding(.vertical, OSMSpacing.md)
                .frame(maxWidth: 488)
                .frame(maxWidth: .infinity)
                .frame(minHeight: geometry.size.height, alignment: .top)
            }
            .scrollIndicators(.hidden)
            .scrollBounceBehavior(.basedOnSize)
            .scrollDismissesKeyboard(.interactively)
        }
        .background(Color.osmBackground.ignoresSafeArea())
        .navigationTitle(AppStrings.PasswordRecovery.screenTitle)
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(true)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button(action: back) {
                    Image(systemName: "chevron.left")
                        .font(.body.weight(.semibold))
                }
                .disabled(viewModel.isLoading)
                .accessibilityLabel(AppStrings.PasswordRecovery.back)
            }
        }
        .onDisappear(perform: viewModel.stop)
        .animation(.easeInOut(duration: 0.2), value: viewModel.step)
        .animation(.easeInOut(duration: 0.2), value: viewModel.banner)
    }

    private var stepProgress: some View {
        VStack(alignment: .leading, spacing: OSMSpacing.xs) {
            Text(
                String(
                    format: String(localized: AppStrings.PasswordRecovery.step),
                    locale: .current,
                    Int64(viewModel.step.progressStep),
                    Int64(3)
                )
            )
            .font(OSMTypography.caption)
            .foregroundStyle(Color.osmOnSurfaceVariant)

            ProgressView(value: Double(viewModel.step.progressStep), total: 3)
                .tint(Color.osmPrimary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }

    private var recoveryHeader: some View {
        VStack(spacing: OSMSpacing.sm) {
            Image(systemName: headerIcon)
                .font(.system(size: 28, weight: .semibold))
                .foregroundStyle(Color.osmPrimary)
                .frame(width: 68, height: 68)
                .background(Color.osmPrimaryContainer)
                .clipShape(Circle())
                .accessibilityHidden(true)

            AnatomyText(
                headerTitle,
                font: OSMTypography.title2,
                alignment: .center
            )
            .accessibilityAddTraits(.isHeader)

            AnatomyText(
                headerSubtitle,
                font: OSMTypography.callout,
                color: .osmOnSurfaceVariant,
                alignment: .center
            )
        }
    }

    @ViewBuilder
    private var stepContent: some View {
        switch viewModel.step {
        case .email:
            AnatomyTextField(
                text: emailBinding,
                label: AppStrings.Login.emailLabel,
                placeholder: AppStrings.Login.emailPlaceholder,
                supportingText: emailErrorMessage,
                isError: viewModel.emailError != nil,
                leadingSystemImage: "envelope",
                keyboardType: .emailAddress,
                textContentType: .emailAddress,
                capitalization: .never,
                autocorrectionDisabled: true,
                submitLabel: .continue,
                focus: focusBinding(for: .email),
                autoFocus: true,
                onSubmit: primaryAction
            )
            .disabled(viewModel.isLoading)

        case .code:
            VStack(spacing: OSMSpacing.md) {
                AnatomyTextField(
                    text: codeBinding,
                    label: AppStrings.PasswordRecovery.codeLabel,
                    placeholder: AppStrings.PasswordRecovery.codePlaceholder,
                    supportingText: codeSupportingText,
                    isError: viewModel.codeError != nil,
                    leadingSystemImage: "key",
                    keyboardType: .asciiCapable,
                    textContentType: .oneTimeCode,
                    capitalization: .characters,
                    autocorrectionDisabled: true,
                    submitLabel: .continue,
                    focus: focusBinding(for: .code),
                    autoFocus: true,
                    onSubmit: primaryAction
                )
                .disabled(viewModel.isLoading)

                Button(action: resendCode) {
                    Text(resendTitle)
                        .font(OSMTypography.bodyEmphasized)
                        .foregroundStyle(
                            viewModel.resendSeconds == 0 && !viewModel.isLoading
                                ? Color.osmPrimary
                                : Color.osmOnSurfaceVariant
                        )
                        .frame(maxWidth: .infinity, alignment: .trailing)
                        .contentShape(Rectangle())
                        .padding(.vertical, OSMSpacing.xs)
                }
                .buttonStyle(.plain)
                .disabled(viewModel.resendSeconds > 0 || viewModel.isLoading)
            }

        case .password:
            VStack(spacing: OSMSpacing.md) {
                AnatomyPasswordField(
                    text: passwordBinding,
                    label: AppStrings.PasswordRecovery.newPassword,
                    placeholder: AppStrings.PasswordRecovery.passwordPlaceholder,
                    supportingText: passwordErrorMessage,
                    isError: viewModel.passwordError != nil,
                    submitLabel: .next,
                    focus: focusBinding(for: .password),
                    autoFocus: true,
                    onSubmit: { focusedField = .confirmation }
                )
                .disabled(viewModel.isLoading)

                AnatomyPasswordField(
                    text: confirmationBinding,
                    label: AppStrings.PasswordRecovery.confirmPassword,
                    placeholder: AppStrings.PasswordRecovery.confirmPlaceholder,
                    supportingText: confirmationErrorMessage,
                    isError: viewModel.confirmationError != nil,
                    submitLabel: .done,
                    focus: focusBinding(for: .confirmation),
                    onSubmit: primaryAction
                )
                .disabled(viewModel.isLoading)
            }

        case .success:
            AnatomyText(
                AppStrings.PasswordRecovery.successSupport,
                font: OSMTypography.body,
                color: .osmOnSurfaceVariant,
                alignment: .center
            )
            .padding(.horizontal, OSMSpacing.md)
        }
    }

    private var emailBinding: Binding<String> {
        Binding(get: { viewModel.email }, set: { viewModel.updateEmail($0) })
    }

    private var codeBinding: Binding<String> {
        Binding(get: { viewModel.code }, set: { viewModel.updateCode($0) })
    }

    private var passwordBinding: Binding<String> {
        Binding(get: { viewModel.password }, set: { viewModel.updatePassword($0) })
    }

    private var confirmationBinding: Binding<String> {
        Binding(
            get: { viewModel.passwordConfirmation },
            set: { viewModel.updatePasswordConfirmation($0) }
        )
    }

    private var headerIcon: String {
        switch viewModel.step {
        case .email: "envelope"
        case .code: "key"
        case .password: "lock"
        case .success: "checkmark.circle"
        }
    }

    private var headerTitle: LocalizedStringResource {
        switch viewModel.step {
        case .email: AppStrings.PasswordRecovery.emailTitle
        case .code: AppStrings.PasswordRecovery.codeTitle
        case .password: AppStrings.PasswordRecovery.passwordTitle
        case .success: AppStrings.PasswordRecovery.successTitle
        }
    }

    private var headerSubtitle: LocalizedStringResource {
        switch viewModel.step {
        case .email: AppStrings.PasswordRecovery.emailSubtitle
        case .code: AppStrings.PasswordRecovery.codeSubtitle
        case .password: AppStrings.PasswordRecovery.passwordSubtitle
        case .success: AppStrings.PasswordRecovery.successSubtitle
        }
    }

    private var primaryButtonTitle: LocalizedStringResource {
        switch viewModel.step {
        case .email: AppStrings.PasswordRecovery.sendCode
        case .code: AppStrings.PasswordRecovery.verifyCode
        case .password: AppStrings.PasswordRecovery.updatePassword
        case .success: AppStrings.PasswordRecovery.backToLogin
        }
    }

    private var emailErrorMessage: String? {
        switch viewModel.emailError {
        case .emailRequired: String(localized: AppStrings.Login.emailRequired)
        case .emailInvalid: String(localized: AppStrings.Login.emailInvalid)
        case nil: nil
        default: String(localized: AppStrings.Login.emailInvalid)
        }
    }

    private var codeSupportingText: String? {
        if viewModel.codeError != nil {
            return String(localized: AppStrings.PasswordRecovery.codeInvalid)
        }
        return String(
            format: String(localized: AppStrings.PasswordRecovery.codeSentTo),
            locale: .current,
            viewModel.email
        )
    }

    private var passwordErrorMessage: String? {
        switch viewModel.passwordError {
        case .passwordRequired: String(localized: AppStrings.Login.passwordRequired)
        case .passwordTooShort: String(localized: AppStrings.Login.passwordTooShort)
        case nil: nil
        default: String(localized: AppStrings.Login.passwordTooShort)
        }
    }

    private var confirmationErrorMessage: String? {
        switch viewModel.confirmationError {
        case .confirmationRequired:
            String(localized: AppStrings.PasswordRecovery.confirmationRequired)
        case .passwordsDoNotMatch:
            String(localized: AppStrings.PasswordRecovery.passwordsDoNotMatch)
        case nil: nil
        default: String(localized: AppStrings.PasswordRecovery.passwordsDoNotMatch)
        }
    }

    private var resendTitle: String {
        guard viewModel.resendSeconds > 0 else {
            return String(localized: AppStrings.PasswordRecovery.resend)
        }
        return String(
            format: String(localized: AppStrings.PasswordRecovery.resendCountdown),
            locale: .current,
            Int64(viewModel.resendSeconds)
        )
    }

    private func bannerMessage(_ banner: ForgotPasswordBanner) -> String {
        let resource: LocalizedStringResource = switch banner {
        case .codeSent: AppStrings.PasswordRecovery.codeSent
        case .invalidCode: AppStrings.PasswordRecovery.codeInvalid
        case .expiredCode: AppStrings.PasswordRecovery.codeExpired
        case .tooManyAttempts: AppStrings.PasswordRecovery.tooManyAttempts
        case .noConnection: AppStrings.PasswordRecovery.noConnection
        case .requestFailed: AppStrings.PasswordRecovery.requestFailed
        }
        return String(localized: resource)
    }

    private func focusBinding(for field: Field) -> Binding<Bool> {
        Binding(
            get: { focusedField == field },
            set: { isFocused in
                if isFocused {
                    focusedField = field
                } else if focusedField == field {
                    focusedField = nil
                }
            }
        )
    }

    private func primaryAction() {
        focusedField = nil
        Task {
            if await viewModel.submit() { onClose() }
        }
    }

    private func resendCode() {
        Task { await viewModel.resendCode() }
    }

    private func back() {
        focusedField = nil
        if viewModel.back() { onClose() }
    }
}

#Preview("Password Recovery · Light") {
    NavigationStack {
        ForgotPasswordView(
            viewModel: ForgotPasswordViewModel(initialEmail: "operaciones@empresa.com"),
            onClose: {}
        )
    }
    .preferredColorScheme(.light)
}

#Preview("Password Recovery · Dark") {
    NavigationStack {
        ForgotPasswordView(
            viewModel: ForgotPasswordViewModel(initialEmail: "operaciones@empresa.com"),
            onClose: {}
        )
    }
    .preferredColorScheme(.dark)
}
