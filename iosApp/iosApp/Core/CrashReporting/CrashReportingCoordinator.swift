import FirebaseCrashlytics

enum CrashReportingCoordinator {
    static func configure() {
        Crashlytics.crashlytics().setCrashlyticsCollectionEnabled(true)
        Crashlytics.crashlytics().setCustomValue(
            AppConfiguration.environment,
            forKey: "environment"
        )
    }

    static func record(_ error: Error) {
        Crashlytics.crashlytics().record(error: error)
    }

    static func setUserIdentifier(_ identifier: String?) {
        Crashlytics.crashlytics().setUserID(identifier ?? "")
    }
}
