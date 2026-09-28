enum CatalogSyncNetworkBlockReason: Equatable {
    case mobileDataDisabled
    case noInternet
}

struct CatalogSyncNetworkPolicy {
    private let preferences: MobileDataSyncPreferences

    init(preferences: MobileDataSyncPreferences = MobileDataSyncPreferences()) {
        self.preferences = preferences
    }

    func blockReason(for status: NetworkConnectionStatus) -> CatalogSyncNetworkBlockReason? {
        switch status {
        case .wifiConnected:
            nil
        case .cellularConnected:
            preferences.isEnabled ? nil : .mobileDataDisabled
        case .wifiNoInternet, .cellularNoInternet, .offline:
            .noInternet
        }
    }
}
