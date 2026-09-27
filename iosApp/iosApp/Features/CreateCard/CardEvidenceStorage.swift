import AVFoundation
import Foundation
import SharedLogic
import UniformTypeIdentifiers

struct PreparedCardEvidence: Identifiable {
    let id: String
    let localPath: String
    let displayName: String
    let mimeType: String
    let mediaType: CardEvidenceMediaType
    let durationMilliseconds: Int64
    let sizeBytes: Int64
}

actor CardEvidenceStorage {
    static let shared = CardEvidenceStorage()

    private let fileManager = FileManager.default

    func importFile(
        from sourceURL: URL,
        mediaType: CardEvidenceMediaType,
        preferredExtension: String? = nil
    ) async throws -> PreparedCardEvidence {
        let accessesSecurityScope = sourceURL.startAccessingSecurityScopedResource()
        defer {
            if accessesSecurityScope { sourceURL.stopAccessingSecurityScopedResource() }
        }

        let values = try sourceURL.resourceValues(forKeys: [.fileSizeKey, .nameKey, .contentTypeKey])
        let fileExtension = preferredExtension?.nonEmpty
            ?? sourceURL.pathExtension.nonEmpty
            ?? defaultExtension(for: mediaType)
        let id = UUID().uuidString.lowercased()
        let destination = try pendingDirectory()
            .appendingPathComponent("\(id).\(fileExtension)", isDirectory: false)

        if fileManager.fileExists(atPath: destination.path) {
            try fileManager.removeItem(at: destination)
        }
        try fileManager.copyItem(at: sourceURL, to: destination)

        let copiedValues = try destination.resourceValues(forKeys: [.fileSizeKey, .contentTypeKey])
        let duration = try await durationMilliseconds(for: destination, mediaType: mediaType)
        return PreparedCardEvidence(
            id: id,
            localPath: destination.path,
            displayName: values.name ?? destination.lastPathComponent,
            mimeType: copiedValues.contentType?.preferredMIMEType ?? mimeType(for: mediaType, extension: fileExtension),
            mediaType: mediaType,
            durationMilliseconds: duration,
            sizeBytes: Int64(copiedValues.fileSize ?? 0)
        )
    }

    func persistData(
        _ data: Data,
        fileName: String,
        mediaType: CardEvidenceMediaType,
        mimeType: String
    ) async throws -> PreparedCardEvidence {
        let id = UUID().uuidString.lowercased()
        let ext = URL(fileURLWithPath: fileName).pathExtension.nonEmpty ?? defaultExtension(for: mediaType)
        let destination = try pendingDirectory().appendingPathComponent("\(id).\(ext)")
        try data.write(to: destination, options: .atomic)
        let duration = try await durationMilliseconds(for: destination, mediaType: mediaType)
        return PreparedCardEvidence(
            id: id,
            localPath: destination.path,
            displayName: fileName,
            mimeType: mimeType,
            mediaType: mediaType,
            durationMilliseconds: duration,
            sizeBytes: Int64(data.count)
        )
    }

    func remove(path: String) {
        guard fileManager.fileExists(atPath: path) else { return }
        try? fileManager.removeItem(atPath: path)
    }

    private func pendingDirectory() throws -> URL {
        let root = try fileManager.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        )
        let directory = root.appendingPathComponent("CardEvidence/Pending", isDirectory: true)
        try fileManager.createDirectory(at: directory, withIntermediateDirectories: true)
        return directory
    }

    private func durationMilliseconds(
        for url: URL,
        mediaType: CardEvidenceMediaType
    ) async throws -> Int64 {
        guard mediaType != .image else { return 0 }
        let duration = try await AVURLAsset(url: url).load(.duration)
        let seconds = CMTimeGetSeconds(duration)
        guard seconds.isFinite, seconds > 0 else { return 0 }
        return Int64((seconds * 1_000).rounded())
    }

    private func defaultExtension(for mediaType: CardEvidenceMediaType) -> String {
        switch mediaType {
        case .image: "jpg"
        case .video: "mov"
        case .audio: "m4a"
        default: "bin"
        }
    }

    private func mimeType(for mediaType: CardEvidenceMediaType, extension value: String) -> String {
        if let type = UTType(filenameExtension: value), let mime = type.preferredMIMEType { return mime }
        return switch mediaType {
        case .image: "image/jpeg"
        case .video: "video/quicktime"
        case .audio: "audio/mp4"
        default: "application/octet-stream"
        }
    }
}

private extension String {
    var nonEmpty: String? { isEmpty ? nil : self }
}
