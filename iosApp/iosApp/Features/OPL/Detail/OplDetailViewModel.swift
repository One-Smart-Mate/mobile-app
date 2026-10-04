import Foundation
import Observation
import SharedLogic

enum OplConfirmation: String, Identifiable {
    case download, replace, delete
    var id: String { rawValue }
    var title: LocalizedStringResource {
        switch self {
        case .download: OplStrings.downloadTitle
        case .replace: OplStrings.replaceTitle
        case .delete: OplStrings.deleteTitle
        }
    }
    var message: LocalizedStringResource {
        switch self {
        case .download: OplStrings.downloadBody
        case .replace: OplStrings.replaceBody
        case .delete: OplStrings.deleteBody
        }
    }
    var action: LocalizedStringResource {
        switch self {
        case .download: OplStrings.download
        case .replace: OplStrings.replace
        case .delete: OplStrings.delete
        }
    }
}

@MainActor
@Observable
final class OplDetailViewModel {
    let site: SessionSite
    let oplID: Int64
    private(set) var opl: Opl?
    private(set) var isLoading = true
    private(set) var isDownloaded = false
    private(set) var isDownloading = false
    private(set) var isDeleting = false
    private(set) var progress: OplDownloadProgress?
    private(set) var files: [Int64: String] = [:]
    private(set) var loadingIDs = Set<Int64>()
    private(set) var failedIDs = Set<Int64>()
    private(set) var mediaEpoch = UUID()
    private(set) var preloadImages = true
    var error: String?
    var operationError: String?
    var notice: String?
    var confirmation: OplConfirmation?
    var selectedPDF: OplContent?
    var isShowingMediaPreview = false
    private let controller: IosOplController
    private var active = false
    private var lifecycle = UUID()
    private var operation: Task<Void, Never>?
    private var mediaTasks: [Int64: Task<Void, Never>] = [:]

    init(site: SessionSite, oplID: Int64, controller: IosOplController) {
        self.site = site
        self.oplID = oplID
        self.controller = controller
    }

    var isBusy: Bool { isLoading || isDownloading || isDeleting }

    func start() async {
        guard !active else { return }
        active = true
        await load()
    }

    func retry() {
        guard !isBusy else { return }
        operation = Task { await load() }
    }

    private func load() async {
        isLoading = true
        error = nil
        let token = lifecycle
        defer { if lifecycle == token { isLoading = false } }
        do {
            let result = try await controller.getById(siteId: site.id, oplId: oplID)
            guard lifecycle == token, active, !Task.isCancelled else { return }
            if let lesson = result.opl {
                opl = lesson
                isDownloaded = lesson.isDownloaded
                files = files.filter { FileManager.default.fileExists(atPath: $0.value) }
            } else { error = oplErrorMessage(kind: result.errorKind, status: result.statusCode, detail: true) }
        } catch {
            guard lifecycle == token, !Task.isCancelled else { return }
            self.error = String(localized: OplStrings.loadFailed)
        }
    }

    func requestDownload() {
        guard !isBusy, opl != nil else { return }
        confirmation = isDownloaded ? .replace : .download
    }

    func requestDelete() {
        guard !isBusy, isDownloaded else { return }
        confirmation = .delete
    }

    func confirm(_ choice: OplConfirmation) {
        guard !isBusy, opl != nil, choice != .delete || isDownloaded else { return }
        confirmation = nil
        selectedPDF = nil
        operationError = nil
        notice = nil
        mediaEpoch = UUID()
        mediaTasks.values.forEach { $0.cancel() }
        let tasks = Array(mediaTasks.values)
        // Swift task cancellation alone does not cancel exported Kotlin suspend work.
        controller.stop()
        let token = lifecycle
        if choice == .delete { isDeleting = true } else { isDownloading = true }
        operation = Task { [self] in
            defer {
                if lifecycle == token { isDownloading = false; isDeleting = false; progress = nil }
            }
            for task in tasks { await task.value }
            guard lifecycle == token, !Task.isCancelled else { return }
            do {
                if choice == .delete {
                    let result = try await controller.deleteDownloaded(siteId: site.id, oplId: oplID)
                    guard lifecycle == token, !Task.isCancelled else { return }
                    if result.succeeded {
                        isDownloaded = false
                        preloadImages = false
                        clearMedia()
                        notice = String(localized: OplStrings.deleted)
                    } else { operationError = String(localized: OplStrings.deleteFailed) }
                } else {
                    let result = try await controller.download(siteId: site.id, oplId: oplID) { [weak self] update in
                        Task { @MainActor [weak self] in
                            guard let self, self.lifecycle == token, self.isDownloading else { return }
                            self.progress = update
                        }
                    }
                    guard lifecycle == token, !Task.isCancelled else { return }
                    if let lesson = result.opl {
                        opl = lesson
                        isDownloaded = true
                        preloadImages = true
                        clearMedia()
                        notice = String(localized: OplStrings.downloadComplete)
                    } else { operationError = String(localized: OplStrings.downloadFailed) }
                }
            } catch {
                guard lifecycle == token, !Task.isCancelled else { return }
                operationError = String(localized: choice == .delete ? OplStrings.deleteFailed : OplStrings.downloadFailed)
                // Reconcile SQLite if the commit succeeded but materialized-cache cleanup failed.
                if let result = try? await controller.getDownloaded(siteId: site.id) {
                    let local = result.opls.first { $0.id == oplID }
                    isDownloaded = local != nil
                    if let local { opl = local }
                    clearMedia()
                }
            }
        }
    }

    func prepareImage(_ content: OplContent) {
        if preloadImages, content.type == .image, !failedIDs.contains(content.id) { resolve(content) }
    }

    func openPDF(_ content: OplContent) {
        guard !isBusy, content.type == .pdf else { return }
        selectedPDF = content
        resolve(content)
    }

    func resolve(_ content: OplContent) {
        guard !isBusy, files[content.id] == nil, !loadingIDs.contains(content.id) else { return }
        loadingIDs.insert(content.id)
        failedIDs.remove(content.id)
        let token = lifecycle
        mediaTasks[content.id] = Task {
            defer {
                if lifecycle == token { loadingIDs.remove(content.id); mediaTasks[content.id] = nil }
            }
            do {
                let result = try await controller.resolveMedia(siteId: site.id, oplId: oplID, contentId: content.id)
                guard lifecycle == token, !Task.isCancelled else { return }
                if let path = result.path { files[content.id] = path }
                else { failedIDs.insert(content.id) }
            } catch {
                guard lifecycle == token, !Task.isCancelled else { return }
                failedIDs.insert(content.id)
            }
        }
    }

    func galleryItem(_ content: OplContent) -> EvidenceMediaItem {
        EvidenceMediaItem(id: String(content.id), path: files[content.id], displayName: content.oplFileName,
            mediaType: content.oplGalleryType ?? .image, isLoading: loadingIDs.contains(content.id),
            hasFailed: failedIDs.contains(content.id))
    }

    func stop() {
        active = false
        lifecycle = UUID()
        operation?.cancel()
        mediaTasks.values.forEach { $0.cancel() }
        controller.stop()
        mediaTasks = [:]
        loadingIDs = []
        isDownloading = false
        isDeleting = false
        progress = nil
    }

    deinit { controller.stop() }

    private func clearMedia() { files = [:]; failedIDs = []; loadingIDs = [] }
}
