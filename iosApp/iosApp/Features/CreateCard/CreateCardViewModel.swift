import Foundation
import Observation
import SharedLogic

@MainActor
@Observable
final class CreateCardViewModel: Identifiable {
    let id = UUID()
    private(set) var state: CreateCardState?
    private(set) var isLoading = true
    private(set) var shouldClose = false

    private let controller: IosCreateCardController
    private let evidenceStorage: CardEvidenceStorage
    private let syncScheduler: CardSyncBackgroundScheduler
    private var hasStarted = false
    private var didSave = false
    private var knownEvidencePaths = Set<String>()

    init(
        controller: IosCreateCardController,
        evidenceStorage: CardEvidenceStorage = .shared,
        syncScheduler: CardSyncBackgroundScheduler
    ) {
        self.controller = controller
        self.evidenceStorage = evidenceStorage
        self.syncScheduler = syncScheduler
    }

    func start(siteID: Int64?) async {
        guard !hasStarted else { return }
        hasStarted = true
        do {
            let started = try await controller.start(
                siteId: siteID.map(KotlinLong.init),
                appVersion: Self.appVersion
            ) { [weak self] state in
                Task { @MainActor [weak self] in
                    self?.apply(state)
                }
            }
            if !started.boolValue { shouldClose = true }
        } catch {
            shouldClose = true
        }
        isLoading = false
    }

    func open(_ sheet: CreateCardSheet) { controller.openSheet(sheet: sheet) }
    func dismissSheet() { controller.dismissSheet() }
    func search(_ value: String) { controller.updateSheetQuery(value: value) }
    func select(_ id: String) { controller.selectSheetItem(id: id) }
    func navigateLevel(_ id: String?) { controller.navigateToLevel(id: id) }
    func setDueDate(_ value: String) { controller.updateCustomDueDate(value: value) }
    func setDescription(_ value: String) { controller.updateDescription(value: value) }
    func dismissError() { controller.dismissError() }
    func next() { _ = controller.next() }

    func back() {
        if !controller.back() { shouldClose = true }
    }

    func addEvidence(_ prepared: PreparedCardEvidence) async {
        controller.setEvidenceProcessing(value: true)
        let accepted = controller.addEvidence(
            id: prepared.id,
            localPath: prepared.localPath,
            displayName: prepared.displayName,
            mimeType: prepared.mimeType,
            mediaType: prepared.mediaType,
            durationMillis: prepared.durationMilliseconds,
            sizeBytes: prepared.sizeBytes
        )
        if !accepted { await evidenceStorage.remove(path: prepared.localPath) }
    }

    func evidenceImportFailed() {
        controller.evidenceImportFailed()
    }

    func removeEvidence(_ id: String) async {
        if let path = controller.removeEvidence(id: id) {
            await evidenceStorage.remove(path: path)
        }
    }

    func save() async {
        do {
            let outcome = try await controller.save()
            guard outcome.succeeded else { return }
            didSave = true
            syncScheduler.enqueueAfterLocalChange()
            shouldClose = true
        } catch {
            return
        }
    }

    func stop() {
        controller.stop()
        guard !didSave else { return }
        let paths = knownEvidencePaths
        Task { [evidenceStorage] in
            for path in paths { await evidenceStorage.remove(path: path) }
        }
    }

    private func apply(_ newState: CreateCardState) {
        let nextPaths = Set(newState.evidences.map(\.localPath))
        let removed = knownEvidencePaths.subtracting(nextPaths)
        if !removed.isEmpty {
            Task { [evidenceStorage] in
                for path in removed { await evidenceStorage.remove(path: path) }
            }
        }
        knownEvidencePaths = nextPaths
        state = newState
    }

    private static var appVersion: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0"
    }
}
