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
        let liveDependencies = AppDependencies.live()
        dependencies = liveDependencies
        if let catalogSyncController = liveDependencies.catalogSyncController {
            CatalogSyncBackgroundScheduler.shared.configure(controller: catalogSyncController)
            CatalogSyncBackgroundScheduler.shared.register()
        }
        if let cardSyncController = liveDependencies.cardSyncController {
            CardSyncBackgroundScheduler.shared.configure(controller: cardSyncController)
            CardSyncBackgroundScheduler.shared.register()
        }
    }

    var body: some Scene {
        WindowGroup {
            OneSmartMateTheme {
                ContentView(dependencies: dependencies)
            }
        }
    }
}
