import Foundation

enum APIEnvironment: String, CaseIterable, Identifiable {
    case dev
    case prod

    var id: String { rawValue }
    var displayName: String { rawValue.uppercased() }
}

enum AppConfiguration {
    #if DEBUG
    static let buildEnvironment = APIEnvironment.dev
    #else
    static let buildEnvironment = APIEnvironment.prod
    #endif

    static var selectedAPIEnvironment: APIEnvironment {
        guard
            let storedValue = UserDefaults.standard.string(forKey: selectedEnvironmentKey),
            let environment = APIEnvironment(rawValue: storedValue)
        else { return buildEnvironment }
        return environment
    }

    static var environment: String { selectedAPIEnvironment.rawValue }
    static var enableNetworkLogging: Bool { selectedAPIEnvironment == .dev }
    static var apiBaseURL: String { apiBaseURL(for: selectedAPIEnvironment) }

    static func apiBaseURL(for environment: APIEnvironment) -> String {
        let key = environment == .dev
            ? "DevelopmentApiBaseURL"
            : "ProductionApiBaseURL"
        guard
            let value = Bundle.main.object(forInfoDictionaryKey: key) as? String,
            !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
            !value.contains("$(")
        else {
            fatalError("Missing \(key) in Secrets.xcconfig")
        }
        return value
    }

    static func selectAPIEnvironment(_ environment: APIEnvironment) {
        UserDefaults.standard.set(environment.rawValue, forKey: selectedEnvironmentKey)
    }

    private static let selectedEnvironmentKey = "api.selected-environment"
}
