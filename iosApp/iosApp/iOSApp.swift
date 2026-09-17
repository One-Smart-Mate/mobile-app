import SwiftUI
import SharedLogic

@main
struct iOSApp: App {
    private let dependencies: AppDependencies

    init() {
        KoinIosKt.doInitKoinIos(
            baseUrl: AppConfiguration.apiBaseURL,
            environment: AppConfiguration.environment,
            enableNetworkLogging: AppConfiguration.enableNetworkLogging
        )
        dependencies = .live()
    }

    var body: some Scene {
        WindowGroup {
            OneSmartMateTheme {
                ContentView(dependencies: dependencies)
            }
        }
    }
}
