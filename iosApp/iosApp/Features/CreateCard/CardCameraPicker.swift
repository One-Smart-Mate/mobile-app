import SwiftUI
import SharedLogic
import UIKit
import UniformTypeIdentifiers

struct CardCameraPicker: UIViewControllerRepresentable {
    let mediaType: CardEvidenceMediaType
    var maximumVideoDuration: TimeInterval = 0
    let onPicked: (URL) -> Void
    let onCancel: () -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(parent: self)
    }

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.delegate = context.coordinator
        picker.sourceType = .camera
        picker.mediaTypes = mediaType == .video
            ? [UTType.movie.identifier]
            : [UTType.image.identifier]
        picker.videoQuality = .typeMedium
        if mediaType == .video, maximumVideoDuration > 0 {
            picker.videoMaximumDuration = maximumVideoDuration
        }
        return picker
    }

    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}

    final class Coordinator: NSObject, UINavigationControllerDelegate, UIImagePickerControllerDelegate {
        let parent: CardCameraPicker

        init(parent: CardCameraPicker) {
            self.parent = parent
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            parent.onCancel()
        }

        func imagePickerController(
            _ picker: UIImagePickerController,
            didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]
        ) {
            if let mediaURL = info[.mediaURL] as? URL {
                parent.onPicked(mediaURL)
                return
            }
            guard let image = info[.originalImage] as? UIImage,
                  let data = image.jpegData(compressionQuality: 0.88) else {
                parent.onCancel()
                return
            }
            let url = FileManager.default.temporaryDirectory
                .appendingPathComponent("capture-\(UUID().uuidString).jpg")
            do {
                try data.write(to: url, options: .atomic)
                parent.onPicked(url)
            } catch {
                parent.onCancel()
            }
        }
    }
}
