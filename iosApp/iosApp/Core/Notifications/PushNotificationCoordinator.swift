import FirebaseCore
import FirebaseMessaging
import OSLog
import SharedLogic
import UIKit
import UserNotifications

@MainActor
final class PushNotificationCoordinator {
    static let shared = PushNotificationCoordinator()

    private let logger = Logger(
        subsystem: Bundle.main.bundleIdentifier ?? "com.ih.osm",
        category: "PushNotifications"
    )
    private let pendingTokenKey = "push.pending-fcm-token"
    private var controller: IosPushNotificationController?
    private var registrationTask: Task<Void, Never>?

    private init() {}

    func configure(controller: IosPushNotificationController) {
        self.controller = controller
        registerPendingToken()
    }

    func configureFirebase() {
        guard FirebaseApp.app() == nil else { return }
        let resource = AppConfiguration.environment == "dev"
            ? "GoogleService-Info-Dev"
            : "GoogleService-Info-Prod"
        guard
            let path = Bundle.main.path(forResource: resource, ofType: "plist"),
            let options = FirebaseOptions(contentsOfFile: path)
        else {
            logger.notice("Firebase config \(resource, privacy: .public).plist is not bundled; remote push is disabled")
            return
        }
        FirebaseApp.configure(options: options)
        enableRemoteNotificationsIfAuthorized()
    }

    func enableRemoteNotificationsIfAuthorized() {
        guard FirebaseApp.app() != nil else { return }
        Task {
            let settings = await UNUserNotificationCenter.current().notificationSettings()
            guard settings.authorizationStatus == .authorized
                || settings.authorizationStatus == .provisional
                || settings.authorizationStatus == .ephemeral
            else { return }
            UIApplication.shared.registerForRemoteNotifications()
        }
    }

    func receivedFCMToken(_ token: String?) {
        guard let token, !token.isEmpty else { return }
        UserDefaults.standard.set(token, forKey: pendingTokenKey)
        registerPendingToken()
    }

    func sessionDidBecomeAvailable() {
        registerPendingToken()
    }

    func receivedRemoteNotification(_ userInfo: [AnyHashable: Any]) -> Bool {
        guard stringValue(userInfo["sync_scope"]) == "cards",
              let siteID = Int64(stringValue(userInfo["site_id"]) ?? "")
        else { return false }
        CardSyncBackgroundScheduler.shared.enqueueRemoteChanges(siteID: siteID)
        return true
    }

    private func registerPendingToken() {
        guard let controller,
              let token = UserDefaults.standard.string(forKey: pendingTokenKey),
              !token.isEmpty
        else { return }
        registrationTask?.cancel()
        registrationTask = Task {
            do {
                let outcome = try await controller.registerToken(token: token)
                guard !Task.isCancelled else { return }
                if outcome.succeeded {
                    UserDefaults.standard.removeObject(forKey: pendingTokenKey)
                } else if let message = outcome.errorMessage {
                    logger.error("FCM token registration failed: \(message, privacy: .public)")
                }
            } catch {
                guard !Task.isCancelled else { return }
                logger.error("FCM token registration failed: \(error.localizedDescription, privacy: .public)")
            }
        }
    }

    private func stringValue(_ value: Any?) -> String? {
        switch value {
        case let string as String: string
        case let number as NSNumber: number.stringValue
        default: nil
        }
    }
}

@MainActor
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate, MessagingDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        PushNotificationCoordinator.shared.configureFirebase()
        if FirebaseApp.app() != nil { Messaging.messaging().delegate = self }
        return true
    }

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        Messaging.messaging().apnsToken = deviceToken
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        // Registration is retried by iOS on a later launch or permission change.
    }

    nonisolated func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        Task { @MainActor in PushNotificationCoordinator.shared.receivedFCMToken(fcmToken) }
    }

    func application(
        _ application: UIApplication,
        didReceiveRemoteNotification userInfo: [AnyHashable: Any],
        fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void
    ) {
        Task { @MainActor in
            let handled = PushNotificationCoordinator.shared.receivedRemoteNotification(userInfo)
            completionHandler(handled ? .newData : .noData)
        }
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification
    ) async -> UNNotificationPresentationOptions {
        await MainActor.run {
            _ = PushNotificationCoordinator.shared.receivedRemoteNotification(
                notification.request.content.userInfo
            )
        }
        return [.banner, .list, .sound, .badge]
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse
    ) async {
        await MainActor.run {
            _ = PushNotificationCoordinator.shared.receivedRemoteNotification(
                response.notification.request.content.userInfo
            )
        }
    }
}
