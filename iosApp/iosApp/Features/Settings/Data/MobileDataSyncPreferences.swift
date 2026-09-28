import Foundation

final class MobileDataSyncPreferences {
    static let storageKey = "settings.allow-mobile-data"

    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    var isEnabled: Bool {
        defaults.object(forKey: Self.storageKey) == nil
            ? true
            : defaults.bool(forKey: Self.storageKey)
    }

    func setEnabled(_ enabled: Bool) {
        defaults.set(enabled, forKey: Self.storageKey)
    }

    func reset() {
        defaults.removeObject(forKey: Self.storageKey)
    }
}
