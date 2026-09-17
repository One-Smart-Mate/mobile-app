import SwiftUI

@MainActor
struct LoginView: View {
    private enum Field: Hashable {
        case email
        case password
    }

    @State private var viewModel: LoginViewModel
    @FocusState private var focusedField: Field?

    init() {
        _viewModel = State(initialValue: LoginViewModel())
    }

    init(viewModel: @autoclosure @escaping () -> LoginViewModel) {
        _viewModel = State(initialValue: viewModel())
    }

    var body: some View {
        GeometryReader { geometry in
            ScrollView {
                Group {
                    if usesWideLayout(geometry.size) {
                        wideContent(minHeight: geometry.size.height)
                    } else {
                        portraitContent(minHeight: geometry.size.height)
                    }
                }
                .frame(maxWidth: .infinity)
            }
            .scrollIndicators(.hidden)
            .scrollBounceBehavior(.basedOnSize)
            .scrollDismissesKeyboard(.interactively)
            .background(Color.osmBackground)
        }
        .background(Color.osmBackground.ignoresSafeArea())
        .safeAreaInset(edge: .top, spacing: 0) {
            if let bannerMessage = viewModel.bannerMessage {
                AnatomyBanner(
                    message: bannerMessage,
                    type: .error,
                    onDismiss: viewModel.dismissBanner
                )
                .padding(.horizontal, OSMSpacing.md)
                .padding(.top, OSMSpacing.xs)
                .transition(.move(edge: .top).combined(with: .opacity))
            }
        }
        .animation(.easeInOut(duration: 0.2), value: viewModel.bannerMessage)
    }

    private func portraitContent(minHeight: CGFloat) -> some View {
        VStack(spacing: 0) {
            Spacer(minLength: OSMSpacing.xl)

            BrandHeader()

            Spacer()
                .frame(height: OSMSpacing.xxl)

            loginForm

            Spacer(minLength: OSMSpacing.xl)
        }
        .padding(.horizontal, OSMSpacing.lg)
        .padding(.vertical, OSMSpacing.md)
        .frame(maxWidth: 488)
        .frame(maxWidth: .infinity)
        .frame(minHeight: minHeight)
    }

    private func wideContent(minHeight: CGFloat) -> some View {
        HStack(spacing: 72) {
            BrandHeader()
                .frame(maxWidth: 360)

            loginForm
                .frame(maxWidth: 440)
        }
        .padding(.horizontal, OSMSpacing.xxl)
        .padding(.vertical, OSMSpacing.xl)
        .frame(maxWidth: 1_080)
        .frame(maxWidth: .infinity)
        .frame(minHeight: minHeight)
    }

    private var loginForm: some View {
        VStack(spacing: 0) {
            AnatomyText(
                AppStrings.Login.title,
                font: OSMTypography.title,
                alignment: .center
            )
            .accessibilityAddTraits(.isHeader)

            Spacer()
                .frame(height: 6)

            AnatomyText(
                AppStrings.Login.subtitle,
                font: OSMTypography.callout,
                color: .osmOnSurfaceVariant,
                alignment: .center
            )

            Spacer()
                .frame(height: OSMSpacing.xl)

            AnatomyTextField(
                text: emailBinding,
                label: AppStrings.Login.emailLabel,
                placeholder: AppStrings.Login.emailPlaceholder,
                supportingText: emailSupportingText,
                isError: viewModel.emailError != nil,
                leadingSystemImage: "envelope",
                keyboardType: .emailAddress,
                textContentType: .username,
                capitalization: .never,
                autocorrectionDisabled: true,
                submitLabel: .next,
                focus: focusBinding(for: .email),
                onSubmit: { focusedField = .password }
            )
            .disabled(viewModel.isLoading)

            Spacer()
                .frame(height: OSMSpacing.md)

            AnatomyPasswordField(
                text: passwordBinding,
                label: AppStrings.Login.passwordLabel,
                placeholder: AppStrings.Login.passwordPlaceholder,
                supportingText: passwordSupportingText,
                isError: viewModel.passwordError != nil,
                submitLabel: .go,
                focus: focusBinding(for: .password),
                onSubmit: requestLogin
            )
            .disabled(viewModel.isLoading)

            Button(action: viewModel.requestPasswordReset) {
                Text(AppStrings.Login.forgotPassword)
                    .font(OSMTypography.bodyEmphasized)
                    .foregroundStyle(Color.osmPrimary)
                    .frame(maxWidth: .infinity, alignment: .trailing)
                    .contentShape(Rectangle())
                    .padding(.vertical, OSMSpacing.sm)
            }
            .buttonStyle(.plain)
            .disabled(viewModel.isLoading)

            Spacer()
                .frame(height: OSMSpacing.md)

            AnatomyButton(
                title: AppStrings.Login.submit,
                action: requestLogin,
                size: .large,
                isLoading: viewModel.isLoading
            )

            Spacer()
                .frame(height: OSMSpacing.lg)

            Label {
                AnatomyText(
                    AppStrings.Login.secureAccess,
                    font: OSMTypography.caption,
                    color: .osmOnSurfaceVariant
                )
            } icon: {
                Image(systemName: "lock")
                    .font(.caption)
                    .foregroundStyle(Color.osmOnSurfaceVariant)
            }
        }
    }

    private var emailBinding: Binding<String> {
        Binding(
            get: { viewModel.email },
            set: { viewModel.updateEmail($0) }
        )
    }

    private var passwordBinding: Binding<String> {
        Binding(
            get: { viewModel.password },
            set: { viewModel.updatePassword($0) }
        )
    }

    private var emailSupportingText: String? {
        switch viewModel.emailError {
        case .required: String(localized: AppStrings.Login.emailRequired)
        case .invalid: String(localized: AppStrings.Login.emailInvalid)
        case nil: nil
        }
    }

    private var passwordSupportingText: String? {
        switch viewModel.passwordError {
        case .required: String(localized: AppStrings.Login.passwordRequired)
        case .tooShort: String(localized: AppStrings.Login.passwordTooShort)
        case nil: nil
        }
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

    private func requestLogin() {
        focusedField = nil
        Task {
            await viewModel.logIn()
        }
    }

    private func usesWideLayout(_ size: CGSize) -> Bool {
        size.width > 760 && size.width > size.height
    }
}

private struct BrandHeader: View {
    var body: some View {
        VStack(spacing: 0) {
            Image(systemName: "checkmark.shield")
                .font(.system(size: 34, weight: .semibold))
                .foregroundStyle(Color.osmPrimary)
                .frame(width: 72, height: 72)
                .background(Color.osmPrimaryContainer)
                .clipShape(Circle())
                .accessibilityHidden(true)

            Spacer()
                .frame(height: 14)

            AnatomyText(
                AppStrings.Login.brandName,
                font: OSMTypography.title2,
                alignment: .center
            )

            Spacer()
                .frame(height: 2)

            AnatomyText(
                AppStrings.Login.brandTagline,
                font: OSMTypography.caption,
                color: .osmOnSurfaceVariant,
                alignment: .center
            )
        }
        .accessibilityElement(children: .combine)
    }
}

#Preview("Login · Light") {
    OneSmartMateTheme {
        LoginView()
    }
    .preferredColorScheme(.light)
}

#Preview("Login · Dark") {
    OneSmartMateTheme {
        LoginView(
            viewModel: LoginViewModel(
                email: "operaciones@empresa.com",
                password: "password"
            )
        )
    }
    .preferredColorScheme(.dark)
}

#Preview("Login · Validation") {
    OneSmartMateTheme {
        LoginView(
            viewModel: LoginViewModel(
                email: "correo-invalido",
                password: "",
                emailError: .invalid,
                passwordError: .required
            )
        )
    }
    .preferredColorScheme(.light)
}
