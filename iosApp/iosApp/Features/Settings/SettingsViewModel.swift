import Observation
import SharedLogic

@MainActor
@Observable
final class SettingsViewModel {
    private(set) var pendingCardCount = 0
    private(set) var allowMobileData: Bool
    private(set) var isLoggingOut = false
    private(set) var logoutError: String?
    var showsAccountInformation = false
    var showsLogoutWarning = false

    private let controller: IosSettingsController
    private let preferences: MobileDataSyncPreferences
    private let logoutManager: SettingsLogoutManager
    private var hasStarted = false

    init(
        controller: IosSettingsController,
        preferences: MobileDataSyncPreferences = MobileDataSyncPreferences()
    ) {
        self.controller = controller
        self.preferences = preferences
        allowMobileData = preferences.isEnabled
        logoutManager = SettingsLogoutManager(
            controller: controller,
            preferences: preferences
        )
        pendingCardCount = Int(controller.pendingCount())
    }

    func start() {
        guard !hasStarted else { return }
        hasStarted = true
        controller.start { [weak self] count in
            Task { @MainActor [weak self] in
                guard let self else { return }
                pendingCardCount = Int(count.int64Value)
                if pendingCardCount == 0 { showsLogoutWarning = false }
            }
        }
    }

    func stop() {
        controller.stop()
        hasStarted = false
    }

    func setAllowMobileData(_ enabled: Bool) {
        allowMobileData = enabled
        preferences.setEnabled(enabled)
        CardSyncBackgroundScheduler.shared.networkPolicyDidChange()
    }

    func requestLogout() {
        guard !isLoggingOut else { return }
        if pendingCardCount > 0 {
            showsLogoutWarning = true
        } else {
            Task { await performLogout() }
        }
    }

    func confirmLogout() {
        guard !isLoggingOut else { return }
        showsLogoutWarning = false
        Task { await performLogout() }
    }

    func dismissError() {
        logoutError = nil
    }

    private func performLogout() async {
        isLoggingOut = true
        logoutError = nil
        do {
            try await logoutManager.logout()
        } catch {
            guard !Task.isCancelled else { return }
            isLoggingOut = false
            logoutError = String(localized: AppStrings.Settings.logoutError)
        }
    }
}
