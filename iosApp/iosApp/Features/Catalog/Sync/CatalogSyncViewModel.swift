import Observation
import SharedLogic
import UIKit

enum CatalogSyncSelection: String, CaseIterable, Hashable {
    case cardTypes = "CARD_TYPES"
    case preclassifiers = "PRECLASSIFIERS"
    case priorities = "PRIORITIES"
    case levels = "LEVELS"
    case employees = "EMPLOYEES"
}

enum CatalogSyncViewState: Equatable {
    case idle
    case waitingForNetwork
    case downloading(progress: Double, catalogKey: String?)
    case failed(message: String)
}

@MainActor
@Observable
final class CatalogSyncViewModel {
    private(set) var state: CatalogSyncViewState = .idle

    private let controller: IosCatalogSyncController
    private let backgroundScheduler: CatalogSyncBackgroundScheduler
    private var syncTask: Task<Void, Never>?
    private var backgroundTaskIdentifier: UIBackgroundTaskIdentifier = .invalid

    init(
        controller: IosCatalogSyncController,
        backgroundScheduler: CatalogSyncBackgroundScheduler
    ) {
        self.controller = controller
        self.backgroundScheduler = backgroundScheduler
    }

    func syncIfNeeded() {
        run(catalogs: backgroundScheduler.pendingCatalogKeys)
    }

    func syncManually(_ catalogs: Set<CatalogSyncSelection>) {
        guard !catalogs.isEmpty else { return }
        run(catalogs: catalogs.map(\.rawValue))
    }

    func appDidEnterBackground() {
        guard syncTask != nil else { return }
        backgroundScheduler.schedule()
    }

    private func run(catalogs: [String]?) {
        guard syncTask == nil else { return }

        backgroundScheduler.schedule(
            catalogKeys: catalogs,
            resetRetryCount: true
        )
        beginBackgroundExecution()
        state = .downloading(progress: 0, catalogKey: nil)

        syncTask = Task { [weak self] in
            guard let self else { return }
            defer {
                endBackgroundExecution()
                syncTask = nil
            }

            do {
                let outcome: IosCatalogSyncOutcome
                let progressHandler: (IosCatalogSyncProgress) -> Void = { [weak self] progress in
                    Task { @MainActor [weak self] in
                        self?.state = .downloading(
                            progress: progress.fraction,
                            catalogKey: progress.catalogKey
                        )
                    }
                }

                if let catalogs {
                    outcome = try await controller.syncManual(
                        catalogKeys: catalogs,
                        onProgress: progressHandler
                    )
                } else {
                    outcome = try await controller.syncIfNeeded(onProgress: progressHandler)
                }
                apply(outcome)
            } catch {
                if Task.isCancelled {
                    state = .waitingForNetwork
                } else {
                    state = .failed(
                        message: String(localized: AppStrings.CatalogSync.genericError)
                    )
                }
                backgroundScheduler.scheduleRetry()
            }
        }
    }

    private func apply(_ outcome: IosCatalogSyncOutcome) {
        if outcome.succeeded {
            state = .idle
            backgroundScheduler.cancelPending()
        } else if outcome.connectivityFailure {
            state = .waitingForNetwork
            backgroundScheduler.scheduleRetry()
        } else {
            state = .failed(
                message: outcome.errorMessage
                    ?? String(localized: AppStrings.CatalogSync.genericError)
            )
            backgroundScheduler.scheduleRetry()
        }
    }

    private func beginBackgroundExecution() {
        guard backgroundTaskIdentifier == .invalid else { return }
        backgroundTaskIdentifier = UIApplication.shared.beginBackgroundTask(
            withName: CatalogSyncBackgroundScheduler.taskIdentifier
        ) { [weak self] in
            Task { @MainActor [weak self] in
                guard let self else { return }
                syncTask?.cancel()
                controller.cancelActiveSync()
                endBackgroundExecution()
            }
        }
    }

    private func endBackgroundExecution() {
        guard backgroundTaskIdentifier != .invalid else { return }
        UIApplication.shared.endBackgroundTask(backgroundTaskIdentifier)
        backgroundTaskIdentifier = .invalid
    }
}
