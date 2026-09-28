import SharedLogic

@MainActor
final class SettingsLogoutManager {
    private let controller: IosSettingsController
    private let preferences: MobileDataSyncPreferences
    private let evidenceStorage: CardEvidenceStorage

    init(
        controller: IosSettingsController,
        preferences: MobileDataSyncPreferences,
        evidenceStorage: CardEvidenceStorage = .shared
    ) {
        self.controller = controller
        self.preferences = preferences
        self.evidenceStorage = evidenceStorage
    }

    func logout() async throws {
        CardSyncBackgroundScheduler.shared.cancelAll()
        CatalogSyncBackgroundScheduler.shared.cancelAll()
        await evidenceStorage.clearAll()
        preferences.reset()
        try await controller.logout()
    }
}
