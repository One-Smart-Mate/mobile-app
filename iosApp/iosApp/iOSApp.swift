import SwiftUI
import SharedLogic

@main
struct iOSApp: App {
    init() {
        KoinIosKt.doInitKoinIos(
            baseUrl: AppConfiguration.apiBaseURL,
            environment: AppConfiguration.environment,
            enableNetworkLogging: AppConfiguration.enableNetworkLogging
        )
    }

    var body: some Scene {
        WindowGroup {
            OneSmartMateTheme {
                ContentView()
            }
        }
    }
}
