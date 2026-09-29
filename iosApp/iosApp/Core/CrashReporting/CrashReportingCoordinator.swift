import FirebaseCore
import FirebaseCrashlytics

enum CrashReportingCoordinator {
    static func configure() {
        Crashlytics.crashlytics().setCrashlyticsCollectionEnabled(true)
        Crashlytics.crashlytics().setCustomValue(
            AppConfiguration.buildEnvironment.rawValue,
            forKey: "build_environment"
        )
        updateAPIEnvironment(AppConfiguration.selectedAPIEnvironment)
    }

    static func updateAPIEnvironment(_ environment: APIEnvironment) {
        guard FirebaseApp.app() != nil else { return }
        Crashlytics.crashlytics().setCustomValue(
            environment.rawValue,
            forKey: "api_environment"
        )
    }

    static func record(_ error: Error) {
        Crashlytics.crashlytics().record(error: error)
    }

    static func setUserIdentifier(_ identifier: String?) {
        Crashlytics.crashlytics().setUserID(identifier ?? "")
    }
}
