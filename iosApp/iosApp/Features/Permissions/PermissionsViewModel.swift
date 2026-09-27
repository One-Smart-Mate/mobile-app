import AVFoundation
import Observation
import Photos
import UIKit
import UserNotifications

enum AppPermissionKind: CaseIterable, Identifiable {
    case notifications
    case camera
    case microphone
    case photoLibrary
    case backgroundTasks

    var id: Self { self }
}

enum AppPermissionStatus {
    case granted
    case partial
    case missing
    case denied
    case systemManaged
}

struct AppPermissionItem: Identifiable {
    let kind: AppPermissionKind
    let status: AppPermissionStatus
    var id: AppPermissionKind { kind }
}

@MainActor
@Observable
final class PermissionsViewModel {
    private(set) var items: [AppPermissionItem] = []
    private(set) var isRequesting = false
    var showsSheet = false

    private var hasAutoPrompted = false

    var hasDeniedPermission: Bool {
        items.contains { $0.status == .denied }
    }

    func autoPromptIfNeeded() async {
        guard !hasAutoPrompted else { return }
        hasAutoPrompted = true
        await refresh()
        showsSheet = !allRuntimePermissionsReady
    }

    func refresh() async {
        let notificationSettings = await UNUserNotificationCenter.current().notificationSettings()
        items = [
            AppPermissionItem(
                kind: .notifications,
                status: notificationSettings.authorizationStatus.permissionStatus
            ),
            AppPermissionItem(
                kind: .camera,
                status: AVCaptureDevice.authorizationStatus(for: .video).permissionStatus
            ),
            AppPermissionItem(
                kind: .microphone,
                status: AVAudioApplication.shared.recordPermission.permissionStatus
            ),
            AppPermissionItem(
                kind: .photoLibrary,
                status: PHPhotoLibrary.authorizationStatus(for: .readWrite).permissionStatus
            ),
            AppPermissionItem(kind: .backgroundTasks, status: .systemManaged)
        ]
    }

    func requestPermissions() async {
        guard !isRequesting else { return }
        if hasDeniedPermission {
            guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
            await UIApplication.shared.open(url)
            return
        }

        isRequesting = true
        defer { isRequesting = false }

        let notificationSettings = await UNUserNotificationCenter.current().notificationSettings()
        if notificationSettings.authorizationStatus == .notDetermined {
            _ = try? await UNUserNotificationCenter.current()
                .requestAuthorization(options: [.alert, .sound, .badge])
        }
        PushNotificationCoordinator.shared.enableRemoteNotificationsIfAuthorized()
        if AVCaptureDevice.authorizationStatus(for: .video) == .notDetermined {
            _ = await AVCaptureDevice.requestAccess(for: .video)
        }
        if AVAudioApplication.shared.recordPermission == .undetermined {
            _ = await AVAudioApplication.requestRecordPermission()
        }
        if PHPhotoLibrary.authorizationStatus(for: .readWrite) == .notDetermined {
            _ = await PHPhotoLibrary.requestAuthorization(for: .readWrite)
        }

        await refresh()
        if allRuntimePermissionsReady { showsSheet = false }
    }

    func dismiss() {
        showsSheet = false
    }

    private var allRuntimePermissionsReady: Bool {
        items.allSatisfy {
            switch $0.status {
            case .granted, .partial, .systemManaged: true
            case .missing, .denied: false
            }
        }
    }
}

private extension UNAuthorizationStatus {
    var permissionStatus: AppPermissionStatus {
        switch self {
        case .authorized, .provisional, .ephemeral: .granted
        case .denied: .denied
        case .notDetermined: .missing
        @unknown default: .missing
        }
    }
}

private extension AVAuthorizationStatus {
    var permissionStatus: AppPermissionStatus {
        switch self {
        case .authorized: .granted
        case .denied, .restricted: .denied
        case .notDetermined: .missing
        @unknown default: .missing
        }
    }
}

private extension AVAudioApplication.recordPermission {
    var permissionStatus: AppPermissionStatus {
        switch self {
        case .granted: .granted
        case .denied: .denied
        case .undetermined: .missing
        @unknown default: .missing
        }
    }
}

private extension PHAuthorizationStatus {
    var permissionStatus: AppPermissionStatus {
        switch self {
        case .authorized: .granted
        case .limited: .partial
        case .denied, .restricted: .denied
        case .notDetermined: .missing
        @unknown default: .missing
        }
    }
}
