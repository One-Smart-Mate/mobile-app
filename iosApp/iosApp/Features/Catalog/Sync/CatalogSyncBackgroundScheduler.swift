import BackgroundTasks
import Foundation
import OSLog
import SharedLogic

@MainActor
final class CatalogSyncBackgroundScheduler {
    static let shared = CatalogSyncBackgroundScheduler()
    static let taskIdentifier = "com.ih.osm.catalog-sync"

    private let logger = Logger(
        subsystem: Bundle.main.bundleIdentifier ?? "com.ih.osm",
        category: "CatalogSync"
    )
    private let pendingCatalogKeysStorageKey = "catalog-sync.pending-catalog-keys"
    private let retryCountStorageKey = "catalog-sync.retry-count"
    private let maximumRetryCount = 3
    private var controller: IosCatalogSyncController?
    private var isRegistered = false

    private init() {}

    func configure(controller: IosCatalogSyncController) {
        self.controller = controller
    }

    func register() {
        guard !isRegistered else { return }
        isRegistered = BGTaskScheduler.shared.register(
            forTaskWithIdentifier: Self.taskIdentifier,
            using: nil
        ) { [weak self] task in
            guard let processingTask = task as? BGProcessingTask else {
                task.setTaskCompleted(success: false)
                return
            }
            Task { @MainActor [weak self] in
                self?.handle(processingTask)
            }
        }

        if !isRegistered {
            logger.error("Unable to register the catalog background task")
        }
    }

    var pendingCatalogKeys: [String]? {
        UserDefaults.standard.stringArray(forKey: pendingCatalogKeysStorageKey)
    }

    func schedule(
        catalogKeys: [String]? = nil,
        resetRetryCount: Bool = false
    ) {
        guard isRegistered else { return }
        if let catalogKeys, !catalogKeys.isEmpty {
            UserDefaults.standard.set(catalogKeys, forKey: pendingCatalogKeysStorageKey)
        }
        if resetRetryCount {
            UserDefaults.standard.set(0, forKey: retryCountStorageKey)
        }
        BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: Self.taskIdentifier)

        let request = BGProcessingTaskRequest(identifier: Self.taskIdentifier)
        request.requiresNetworkConnectivity = true
        request.requiresExternalPower = false
        request.earliestBeginDate = Date(timeIntervalSinceNow: 5 * 60)

        do {
            try BGTaskScheduler.shared.submit(request)
        } catch {
            logger.error("Unable to schedule catalog sync: \(error.localizedDescription, privacy: .public)")
        }
    }

    func cancelPending() {
        BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: Self.taskIdentifier)
        UserDefaults.standard.removeObject(forKey: pendingCatalogKeysStorageKey)
        UserDefaults.standard.removeObject(forKey: retryCountStorageKey)
    }

    func cancelAll() {
        controller?.cancelActiveSync()
        cancelPending()
    }

    func scheduleRetry() {
        let retryCount = UserDefaults.standard.integer(forKey: retryCountStorageKey) + 1
        UserDefaults.standard.set(retryCount, forKey: retryCountStorageKey)
        guard retryCount <= maximumRetryCount else { return }
        schedule()
    }

    private func handle(_ backgroundTask: BGProcessingTask) {
        guard let controller else {
            backgroundTask.setTaskCompleted(success: false)
            return
        }

        let operation = Task { @MainActor [weak self] in
            guard let self else {
                backgroundTask.setTaskCompleted(success: false)
                return
            }

            do {
                let outcome: IosCatalogSyncOutcome
                if let catalogKeys = pendingCatalogKeys, !catalogKeys.isEmpty {
                    outcome = try await controller.syncManual(
                        catalogKeys: catalogKeys,
                        onProgress: { _ in }
                    )
                } else {
                    outcome = try await controller.syncIfNeeded(onProgress: { _ in })
                }
                backgroundTask.setTaskCompleted(success: outcome.succeeded)
                if outcome.succeeded {
                    cancelPending()
                } else {
                    scheduleRetry()
                }
            } catch {
                backgroundTask.setTaskCompleted(success: false)
                if !Task.isCancelled {
                    scheduleRetry()
                }
            }
        }

        backgroundTask.expirationHandler = { [weak self] in
            operation.cancel()
            Task { @MainActor [weak self] in
                self?.controller?.cancelActiveSync()
                self?.scheduleRetry()
            }
        }
    }
}
