import Foundation
import SharedLogic

struct AppDependencies {
    let makeSessionViewModel: @MainActor () -> SessionViewModel
    let makeLoginViewModel: @MainActor (
        _ passwordResetHandler: @escaping LoginViewModel.PasswordResetHandler
    ) -> LoginViewModel
    let makeForgotPasswordViewModel: @MainActor (String) -> ForgotPasswordViewModel
    let makeCatalogSyncViewModel: @MainActor () -> CatalogSyncViewModel?
    let makeCardListViewModel: @MainActor () -> CardListViewModel?
    let makeCardDetailViewModel: @MainActor () -> CardDetailViewModel?
    let makeCreateCardViewModel: @MainActor () -> CreateCardViewModel?
    let makeCardSolutionViewModel: @MainActor (String, CardSolutionType) -> CardSolutionViewModel?
    let makeSettingsViewModel: @MainActor () -> SettingsViewModel?
    let catalogSyncController: IosCatalogSyncController?
    let cardSyncController: IosCardSyncController?

    @MainActor
    static func live() -> AppDependencies {
        let authController = KoinIosKt.createIosAuthController()
        let catalogSyncController = KoinIosKt.createIosCatalogSyncController()
        let cardListController = KoinIosKt.createIosCardListController()
        let cardSyncController = KoinIosKt.createIosCardSyncController()
        let apiEnvironmentController = KoinIosKt.createIosApiEnvironmentController()

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
            makeLoginViewModel: { passwordResetHandler in
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
                }, passwordResetHandler: passwordResetHandler,
                   initialEnvironment: AppConfiguration.selectedAPIEnvironment,
                   environmentChangeHandler: { environment in
                       AppConfiguration.selectAPIEnvironment(environment)
                       apiEnvironmentController.selectEnvironment(
                           baseUrl: AppConfiguration.apiBaseURL(for: environment),
                           environment: environment.rawValue
                       )
                       CrashReportingCoordinator.updateAPIEnvironment(environment)
                       PushNotificationCoordinator.shared.apiEnvironmentDidChange()
                   })
            },
            makeForgotPasswordViewModel: { email in
                ForgotPasswordViewModel(
                    initialEmail: email,
                    controller: KoinIosKt.createIosPasswordRecoveryController()
                )
            },
            makeCatalogSyncViewModel: {
                CatalogSyncViewModel(
                    controller: catalogSyncController,
                    backgroundScheduler: .shared
                )
            },
            makeCardListViewModel: {
                CardListViewModel(controller: cardListController)
            },
            makeCardDetailViewModel: {
                CardDetailViewModel(controller: KoinIosKt.createIosCardDetailController())
            },
            makeCreateCardViewModel: {
                CreateCardViewModel(
                    controller: KoinIosKt.createIosCreateCardController(),
                    syncScheduler: .shared
                )
            },
            makeCardSolutionViewModel: { cardUUID, type in
                CardSolutionViewModel(
                    cardUUID: cardUUID,
                    type: type,
                    controller: KoinIosKt.createIosCardSolutionController(),
                    syncScheduler: .shared
                )
            },
            makeSettingsViewModel: {
                SettingsViewModel(controller: KoinIosKt.createIosSettingsController())
            },
            catalogSyncController: catalogSyncController,
            cardSyncController: cardSyncController
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
            makeLoginViewModel: { passwordResetHandler in
                LoginViewModel(passwordResetHandler: passwordResetHandler)
            },
            makeForgotPasswordViewModel: { email in
                ForgotPasswordViewModel(initialEmail: email)
            },
            makeCatalogSyncViewModel: { nil },
            makeCardListViewModel: { nil },
            makeCardDetailViewModel: { nil },
            makeCreateCardViewModel: { nil },
            makeCardSolutionViewModel: { _, _ in nil },
            makeSettingsViewModel: { nil },
            catalogSyncController: nil,
            cardSyncController: nil
        )
    }
}

private struct AuthenticationFailure: LocalizedError {
    let message: String
    var errorDescription: String? { message }
}
