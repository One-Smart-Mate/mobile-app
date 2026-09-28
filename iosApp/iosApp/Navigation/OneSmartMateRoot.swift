import SwiftUI

struct OneSmartMateRoot: View {
    private enum SignedOutRoute: Hashable {
        case forgotPassword(email: String)
    }

    let dependencies: AppDependencies
    @State private var sessionViewModel: SessionViewModel
    @State private var signedOutPath: [SignedOutRoute] = []

    init(dependencies: AppDependencies) {
        self.dependencies = dependencies
        _sessionViewModel = State(initialValue: dependencies.makeSessionViewModel())
    }

    var body: some View {
        sessionContent
            .task { await sessionViewModel.start() }
            .onDisappear { sessionViewModel.stop() }
    }

    @ViewBuilder
    private var sessionContent: some View {
        switch sessionViewModel.state {
        case .loading:
            ZStack {
                Color.osmBackground.ignoresSafeArea()
                ProgressView()
                    .tint(Color.osmPrimary)
                    .accessibilityLabel(AppStrings.Session.loading)
            }
        case .signedOut:
            NavigationStack(path: $signedOutPath) {
                LoginView(
                    viewModel: dependencies.makeLoginViewModel { email in
                        signedOutPath.append(.forgotPassword(email: email))
                    }
                )
                .navigationDestination(for: SignedOutRoute.self) { route in
                    switch route {
                    case let .forgotPassword(email):
                        ForgotPasswordView(
                            viewModel: dependencies.makeForgotPasswordViewModel(email),
                            onClose: closePasswordRecovery
                        )
                    }
                }
            }
        case let .signedIn(user):
            MainTabRoot(
                user: user,
                catalogSyncViewModel: dependencies.makeCatalogSyncViewModel(),
                cardListViewModel: dependencies.makeCardListViewModel(),
                makeCardDetailViewModel: dependencies.makeCardDetailViewModel,
                makeCreateCardViewModel: dependencies.makeCreateCardViewModel,
                makeCardSolutionViewModel: dependencies.makeCardSolutionViewModel,
                settingsViewModel: dependencies.makeSettingsViewModel()
            )
            .id(user.id)
        }
    }

    private func closePasswordRecovery() {
        guard !signedOutPath.isEmpty else { return }
        signedOutPath.removeLast()
    }
}

#Preview("Root · Signed out") {
    OneSmartMateTheme {
        OneSmartMateRoot(dependencies: .preview)
    }
}
