import SwiftUI

struct OneSmartMateRoot: View {
    let dependencies: AppDependencies
    @State private var sessionViewModel: SessionViewModel

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
            NavigationStack {
                LoginView(viewModel: dependencies.makeLoginViewModel())
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
}

#Preview("Root · Signed out") {
    OneSmartMateTheme {
        OneSmartMateRoot(dependencies: .preview)
    }
}
