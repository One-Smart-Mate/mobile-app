import Foundation

enum AppConfiguration {
    #if DEBUG
    static let environment = "dev"
    static let enableNetworkLogging = true
    private static let apiBaseURLKey = "DevelopmentApiBaseURL"
    #else
    static let environment = "prod"
    static let enableNetworkLogging = false
    private static let apiBaseURLKey = "ProductionApiBaseURL"
    #endif

    static let apiBaseURL: String = {
        guard
            let value = Bundle.main.object(forInfoDictionaryKey: apiBaseURLKey) as? String,
            !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
            !value.contains("$(")
        else {
            fatalError("Missing \(apiBaseURLKey) in Secrets.xcconfig")
        }
        return value
    }()
}
