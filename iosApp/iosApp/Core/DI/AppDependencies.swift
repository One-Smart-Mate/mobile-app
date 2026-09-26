import Foundation
import SharedLogic

struct AppDependencies {
    let makeSessionViewModel: @MainActor () -> SessionViewModel
    let makeLoginViewModel: @MainActor () -> LoginViewModel
    let makeCatalogSyncViewModel: @MainActor () -> CatalogSyncViewModel?
    let catalogSyncController: IosCatalogSyncController?

    @MainActor
    static func live() -> AppDependencies {
        let authController = KoinIosKt.createIosAuthController()
        let catalogSyncController = KoinIosKt.createIosCatalogSyncController()

        return AppDependencies(
            makeSessionViewModel: {
                let controller = KoinIosKt.createIosSessionController()
                return SessionViewModel(
                    observeStatus: { observer in
                        controller.start(onStatusChanged: observer)
                    },
                    restoreSession: {
                        try await controller.restore()
                    },
                    stopObservation: controller.stop
                )
            },
            makeLoginViewModel: {
                LoginViewModel(loginHandler: { credentials in
                    let outcome = try await authController.authenticate(
                        email: credentials.email,
                        password: credentials.password
                    )
                    guard outcome.isSuccess else {
                        throw AuthenticationFailure(
                            message: outcome.errorMessage
                                ?? String(localized: AppStrings.Login.genericError)
                        )
                    }
                })
            },
            makeCatalogSyncViewModel: {
                CatalogSyncViewModel(
                    controller: catalogSyncController,
                    backgroundScheduler: .shared
                )
            },
            catalogSyncController: catalogSyncController
        )
    }

    @MainActor
    static var preview: AppDependencies {
        AppDependencies(
            makeSessionViewModel: {
                SessionViewModel(
                    initialState: .signedOut,
                    observeStatus: { _ in },
                    restoreSession: {},
                    stopObservation: {}
                )
            },
            makeLoginViewModel: { LoginViewModel() },
            makeCatalogSyncViewModel: { nil },
            catalogSyncController: nil
        )
    }
}

private struct AuthenticationFailure: LocalizedError {
    let message: String
    var errorDescription: String? { message }
}
