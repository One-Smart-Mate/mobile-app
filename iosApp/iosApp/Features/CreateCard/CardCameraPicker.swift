import AVFoundation
import OSLog
import SwiftUI
import SharedLogic
import UIKit
import UniformTypeIdentifiers

private let cardCameraLogger = Logger(
    subsystem: Bundle.main.bundleIdentifier ?? "com.ih.osm",
    category: "EvidenceCamera"
)

struct CardCameraPicker: UIViewControllerRepresentable {
    let mediaType: CardEvidenceMediaType
    var maximumVideoDuration: TimeInterval = 0
    let onPicked: (URL) -> Void
    let onCancel: () -> Void

    func makeCoordinator() -> Coordinator {
        cardCameraLogger.notice(
            "Creating camera coordinator media=\(String(describing: mediaType), privacy: .public)"
        )
        return Coordinator(parent: self)
    }

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let authorization = AVCaptureDevice.authorizationStatus(for: .video)
        let sourceAvailable = UIImagePickerController.isSourceTypeAvailable(.camera)
        let availableTypes = UIImagePickerController.availableMediaTypes(for: .camera) ?? []
        let requestedType = mediaType == .video
            ? UTType.movie.identifier
            : UTType.image.identifier
        let defaultDevice = AVCaptureDevice.default(for: .video)
        let devices = AVCaptureDevice.DiscoverySession(
            deviceTypes: [.builtInWideAngleCamera],
            mediaType: .video,
            position: .unspecified
        ).devices
        let deviceSummary = devices.map {
            "\($0.localizedName):position=\($0.position.rawValue):connected=\($0.isConnected)"
        }.joined(separator: " | ")

        cardCameraLogger.notice(
            "Building UIImagePickerController authorization=\(authorization.rawValue) sourceAvailable=\(sourceAvailable) requestedType=\(requestedType, privacy: .public) availableTypes=\(availableTypes.joined(separator: ","), privacy: .public) defaultDevice=\(defaultDevice?.localizedName ?? "nil", privacy: .public) devices=\(deviceSummary, privacy: .public)"
        )

        let picker = DiagnosticImagePickerController()
        picker.delegate = context.coordinator
        picker.sourceType = .camera
        picker.mediaTypes = [requestedType]
        picker.cameraCaptureMode = mediaType == .video ? .video : .photo
        picker.videoQuality = .typeMedium
        if mediaType == .video, maximumVideoDuration > 0 {
            picker.videoMaximumDuration = maximumVideoDuration
        }
        cardCameraLogger.notice(
            "UIImagePickerController configured captureMode=\(picker.cameraCaptureMode.rawValue) cameraDevice=\(picker.cameraDevice.rawValue) maxVideoDuration=\(picker.videoMaximumDuration)"
        )

        DispatchQueue.main.asyncAfter(deadline: .now() + 1) { [weak picker] in
            guard let picker else {
                cardCameraLogger.error("Camera picker was released before delayed diagnostic")
                return
            }
            cardCameraLogger.notice(
                "Camera delayed diagnostic presented=\(picker.presentingViewController != nil) viewLoaded=\(picker.isViewLoaded) windowAttached=\(picker.viewIfLoaded?.window != nil) bounds=\(String(describing: picker.viewIfLoaded?.bounds), privacy: .public) appState=\(UIApplication.shared.applicationState.rawValue)"
            )
        }
        return picker
    }

    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {
        guard !context.coordinator.hasLoggedFirstUpdate else { return }
        context.coordinator.hasLoggedFirstUpdate = true
        cardCameraLogger.notice(
            "Camera representable updated presented=\(uiViewController.presentingViewController != nil) windowAttached=\(uiViewController.viewIfLoaded?.window != nil)"
        )
    }

    static func dismantleUIViewController(
        _ uiViewController: UIImagePickerController,
        coordinator: Coordinator
    ) {
        cardCameraLogger.notice(
            "Dismantling camera picker presented=\(uiViewController.presentingViewController != nil)"
        )
        uiViewController.delegate = nil
    }

    final class Coordinator: NSObject, UINavigationControllerDelegate, UIImagePickerControllerDelegate {
        let parent: CardCameraPicker
        var hasLoggedFirstUpdate = false

        init(parent: CardCameraPicker) {
            self.parent = parent
            super.init()
            cardCameraLogger.notice("Camera coordinator initialized")
        }

        deinit {
            cardCameraLogger.notice("Camera coordinator deinitialized")
        }

        func navigationController(
            _ navigationController: UINavigationController,
            willShow viewController: UIViewController,
            animated: Bool
        ) {
            cardCameraLogger.notice(
                "Camera navigation will show \(String(describing: type(of: viewController)), privacy: .public) animated=\(animated) bounds=\(String(describing: viewController.view.bounds), privacy: .public)"
            )
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            cardCameraLogger.notice("UIImagePickerController delegate received cancel")
            parent.onCancel()
        }

        func imagePickerController(
            _ picker: UIImagePickerController,
            didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]
        ) {
            let keys = info.keys.map(\.rawValue).sorted().joined(separator: ",")
            cardCameraLogger.notice(
                "UIImagePickerController finished keys=\(keys, privacy: .public)"
            )
            if let mediaURL = info[.mediaURL] as? URL {
                cardCameraLogger.notice(
                    "Camera produced video file=\(mediaURL.lastPathComponent, privacy: .public)"
                )
                parent.onPicked(mediaURL)
                return
            }
            guard let image = info[.originalImage] as? UIImage,
                  let data = image.jpegData(compressionQuality: 0.88) else {
                cardCameraLogger.error("Camera callback did not contain usable image or video data")
                parent.onCancel()
                return
            }
            let url = FileManager.default.temporaryDirectory
                .appendingPathComponent("capture-\(UUID().uuidString).jpg")
            do {
                try data.write(to: url, options: .atomic)
                cardCameraLogger.notice(
                    "Camera image persisted file=\(url.lastPathComponent, privacy: .public) bytes=\(data.count) dimensions=\(String(describing: image.size), privacy: .public)"
                )
                parent.onPicked(url)
            } catch {
                cardCameraLogger.error(
                    "Unable to persist camera image error=\(String(describing: error), privacy: .public)"
                )
                parent.onCancel()
            }
        }
    }
}

private final class DiagnosticImagePickerController: UIImagePickerController {
    override func viewDidLoad() {
        super.viewDidLoad()
        cardCameraLogger.notice(
            "UIImagePickerController viewDidLoad bounds=\(String(describing: self.view.bounds), privacy: .public)"
        )
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        cardCameraLogger.notice(
            "UIImagePickerController viewWillAppear animated=\(animated) appState=\(UIApplication.shared.applicationState.rawValue)"
        )
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        cardCameraLogger.notice(
            "UIImagePickerController viewDidAppear animated=\(animated) windowAttached=\(self.view.window != nil) bounds=\(String(describing: self.view.bounds), privacy: .public)"
        )
    }

    override func viewWillDisappear(_ animated: Bool) {
        cardCameraLogger.notice(
            "UIImagePickerController viewWillDisappear animated=\(animated)"
        )
        super.viewWillDisappear(animated)
    }

    deinit {
        cardCameraLogger.notice("UIImagePickerController deinitialized")
    }
}
