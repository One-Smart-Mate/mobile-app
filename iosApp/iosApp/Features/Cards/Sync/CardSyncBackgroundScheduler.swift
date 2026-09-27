import BackgroundTasks
import Foundation
import Network
import Observation
import OSLog
import SharedLogic
import UIKit
import UserNotifications

@MainActor
@Observable
final class CardSyncBackgroundScheduler {
    static let shared = CardSyncBackgroundScheduler()
    static let taskIdentifier = "com.ih.osm.card-sync"

    private let logger = Logger(
        subsystem: Bundle.main.bundleIdentifier ?? "com.ih.osm",
        category: "CardSync"
    )
    private let networkMonitor = NWPathMonitor()
    private let networkQueue = DispatchQueue(label: "com.ih.osm.card-sync.network")
    private let retryCountKey = "card-sync.retry-count"
    private let pendingRemoteSitesKey = "card-sync.pending-remote-sites"
    private let allowMobileDataKey = "settings.allow-mobile-data"
    private let maximumRetries = 5
    private var controller: IosCardSyncController?
    private var activeTask: Task<Void, Never>?
    private var remoteSitesReceivedWhileSyncing = Set<Int64>()
    private var backgroundExecution: UIBackgroundTaskIdentifier = .invalid
    private var isRegistered = false
    private var canUseCurrentNetwork = false

    private(set) var pendingCount = 0
    private(set) var isSyncing = false
    private(set) var completed = 0
    private(set) var total = 0
    private(set) var lastError: String?

    private init() {
        networkMonitor.pathUpdateHandler = { [weak self] path in
            Task { @MainActor [weak self] in
                guard let self else { return }
                canUseCurrentNetwork = isAllowed(path)
                if canUseCurrentNetwork && (!pendingRemoteSiteIDs.isEmpty || pendingCount > 0) {
                    runImmediately()
                }
            }
        }
        networkMonitor.start(queue: networkQueue)
    }

    func configure(controller: IosCardSyncController) {
        self.controller = controller
        pendingCount = Int(controller.pendingCount())
        controller.observePendingCount { [weak self] count in
            Task { @MainActor [weak self] in
                self?.pendingCount = Int(count.int64Value)
            }
        }
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
            Task { @MainActor [weak self] in self?.handle(processingTask) }
        }
        if !isRegistered { logger.error("Unable to register card background sync") }
    }

    func enqueueAfterLocalChange() {
        schedule(resetRetryCount: true)
        Task { await notificationsAuthorizationIfNeeded() }
        guard isAllowed(networkMonitor.currentPath) else { return }
        runImmediately()
    }

    func enqueueRemoteChanges(siteID: Int64) {
        var sites = pendingRemoteSiteIDs
        sites.insert(siteID)
        persistRemoteSiteIDs(sites)
        if isSyncing { remoteSitesReceivedWhileSyncing.insert(siteID) }
        schedule(resetRetryCount: true)
        guard isAllowed(networkMonitor.currentPath) else { return }
        runImmediately()
    }

    func syncManually() {
        guard !isSyncing else { return }
        schedule(resetRetryCount: true)
        Task { await notificationsAuthorizationIfNeeded() }
        runImmediately()
    }

    func networkPolicyDidChange() {
        canUseCurrentNetwork = isAllowed(networkMonitor.currentPath)
        if canUseCurrentNetwork { runImmediately() }
    }

    func appDidEnterBackground() {
        guard activeTask != nil else { return }
        schedule()
    }

    private func runImmediately() {
        guard activeTask == nil, let controller, hasPendingWork(controller) else { return }
        guard isAllowed(networkMonitor.currentPath) else { return }
        isSyncing = true
        completed = 0
        total = Int(controller.pendingCount())
        lastError = nil
        beginBackgroundExecution()
        activeTask = Task { [weak self] in
            guard let self else { return }
            defer {
                endBackgroundExecution()
                activeTask = nil
                isSyncing = false
            }
            await notificationsAuthorizationIfNeeded()
            await postProgress(completed: 0, total: Int(controller.pendingCount()))
            do {
                let outcome = try await performAllSync(controller: controller)
                await finish(outcome)
            } catch {
                guard !Task.isCancelled else { return }
                await postFailure(error.localizedDescription)
                scheduleRetry()
            }
        }
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
            isSyncing = true
            completed = 0
            total = Int(controller.pendingCount())
            defer { isSyncing = false }
            do {
                guard isAllowed(networkMonitor.currentPath) else {
                    backgroundTask.setTaskCompleted(success: false)
                    scheduleRetry()
                    return
                }
                let outcome = try await performAllSync(controller: controller)
                backgroundTask.setTaskCompleted(success: outcome.succeeded)
                await finish(outcome)
            } catch {
                backgroundTask.setTaskCompleted(success: false)
                if !Task.isCancelled {
                    await postFailure(error.localizedDescription)
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

    private func finish(_ outcome: IosCardSyncOutcome) async {
        if outcome.succeeded {
            BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: Self.taskIdentifier)
            UserDefaults.standard.removeObject(forKey: retryCountKey)
            await postSuccess(count: Int(outcome.synced))
        } else {
            lastError = outcome.errorMessage
            await postFailure(
                outcome.errorMessage ?? String(localized: AppStrings.CardSync.failureBody)
            )
            if outcome.retryable { scheduleRetry() }
        }
    }

    private func performAllSync(controller: IosCardSyncController) async throws -> IosCardSyncOutcome {
        var totalSynced = 0
        var didRun = false
        while let siteID = pendingRemoteSiteIDs.sorted().first {
            let remote = try await controller.syncRemoteChanges(siteId: siteID)
            didRun = didRun || remote.didRun
            totalSynced += Int(remote.synced)
            guard remote.succeeded else {
                return IosCardSyncOutcome(
                    succeeded: false,
                    didRun: didRun,
                    synced: Int32(totalSynced),
                    errorMessage: remote.errorMessage,
                    retryable: remote.retryable
                )
            }
            if remoteSitesReceivedWhileSyncing.remove(siteID) == nil {
                var remaining = pendingRemoteSiteIDs
                remaining.remove(siteID)
                persistRemoteSiteIDs(remaining)
            }
        }

        guard controller.pendingCount() > 0 else {
            return IosCardSyncOutcome(
                succeeded: true,
                didRun: didRun,
                synced: Int32(totalSynced),
                errorMessage: nil,
                retryable: false
            )
        }
        let local = try await controller.syncPending { [weak self] progress in
            Task { @MainActor [weak self] in
                await self?.postProgress(
                    completed: Int(progress.completed),
                    total: Int(progress.total)
                )
            }
        }
        return IosCardSyncOutcome(
            succeeded: local.succeeded,
            didRun: true,
            synced: Int32(totalSynced + Int(local.synced)),
            errorMessage: local.errorMessage,
            retryable: local.retryable
        )
    }

    private func hasPendingWork(_ controller: IosCardSyncController) -> Bool {
        controller.pendingCount() > 0 || !pendingRemoteSiteIDs.isEmpty
    }

    private var pendingRemoteSiteIDs: Set<Int64> {
        Set(
            (UserDefaults.standard.array(forKey: pendingRemoteSitesKey) ?? [])
                .compactMap { ($0 as? NSNumber)?.int64Value }
        )
    }

    private func persistRemoteSiteIDs(_ values: Set<Int64>) {
        UserDefaults.standard.set(values.sorted(), forKey: pendingRemoteSitesKey)
    }

    private func isAllowed(_ path: NWPath) -> Bool {
        guard path.status == .satisfied else { return false }
        guard path.usesInterfaceType(.cellular) else { return true }
        if UserDefaults.standard.object(forKey: allowMobileDataKey) == nil { return true }
        return UserDefaults.standard.bool(forKey: allowMobileDataKey)
    }

    private func schedule(resetRetryCount: Bool = false) {
        guard isRegistered else { return }
        if resetRetryCount { UserDefaults.standard.set(0, forKey: retryCountKey) }
        BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: Self.taskIdentifier)
        let request = BGProcessingTaskRequest(identifier: Self.taskIdentifier)
        request.requiresNetworkConnectivity = true
        request.requiresExternalPower = false
        request.earliestBeginDate = Date(timeIntervalSinceNow: 60)
        do {
            try BGTaskScheduler.shared.submit(request)
        } catch {
            logger.error("Unable to schedule card sync: \(error.localizedDescription, privacy: .public)")
        }
    }

    private func scheduleRetry() {
        let count = UserDefaults.standard.integer(forKey: retryCountKey) + 1
        UserDefaults.standard.set(count, forKey: retryCountKey)
        guard count <= maximumRetries else { return }
        schedule()
    }

    private func beginBackgroundExecution() {
        guard backgroundExecution == .invalid else { return }
        backgroundExecution = UIApplication.shared.beginBackgroundTask(withName: Self.taskIdentifier) { [weak self] in
            Task { @MainActor [weak self] in
                guard let self else { return }
                activeTask?.cancel()
                controller?.cancelActiveSync()
                endBackgroundExecution()
            }
        }
    }

    private func endBackgroundExecution() {
        guard backgroundExecution != .invalid else { return }
        UIApplication.shared.endBackgroundTask(backgroundExecution)
        backgroundExecution = .invalid
    }

    private func notificationsAuthorizationIfNeeded() async {
        let center = UNUserNotificationCenter.current()
        let settings = await center.notificationSettings()
        guard settings.authorizationStatus == .notDetermined else { return }
        _ = try? await center.requestAuthorization(options: [.alert, .sound, .badge])
    }

    private func postProgress(completed: Int, total: Int) async {
        self.completed = completed
        self.total = total
        let body = total > 0
            ? String(
                format: String(localized: AppStrings.CardSync.progress),
                locale: .current,
                Int64(completed), Int64(total)
            )
            : String(localized: AppStrings.CardSync.preparing)
        UNUserNotificationCenter.current().removeDeliveredNotifications(withIdentifiers: ["card-sync-progress"])
        await postNotification(
            identifier: "card-sync-progress",
            title: String(localized: AppStrings.CardSync.inProgress),
            body: body,
            sound: nil
        )
    }

    private func postSuccess(count: Int) async {
        UNUserNotificationCenter.current().removeDeliveredNotifications(withIdentifiers: ["card-sync-progress"])
        await postNotification(
            identifier: "card-sync-result",
            title: String(localized: AppStrings.CardSync.successTitle),
            body: String(
                format: String(localized: AppStrings.CardSync.successBody),
                locale: .current,
                Int64(count)
            ),
            sound: .default
        )
    }

    private func postFailure(_ message: String) async {
        lastError = message
        UNUserNotificationCenter.current().removeDeliveredNotifications(withIdentifiers: ["card-sync-progress"])
        await postNotification(
            identifier: "card-sync-result",
            title: String(localized: AppStrings.CardSync.failureTitle),
            body: message,
            sound: .default
        )
    }

    private func postNotification(
        identifier: String,
        title: String,
        body: String,
        sound: UNNotificationSound?
    ) async {
        let settings = await UNUserNotificationCenter.current().notificationSettings()
        guard settings.authorizationStatus == .authorized || settings.authorizationStatus == .provisional else { return }
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        content.sound = sound
        content.threadIdentifier = "card-sync"
        let request = UNNotificationRequest(identifier: identifier, content: content, trigger: nil)
        try? await UNUserNotificationCenter.current().add(request)
    }
}
