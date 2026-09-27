import Foundation
import Observation
import SharedLogic

@MainActor
@Observable
final class CardSolutionViewModel: Identifiable {
    let id = UUID()
    let cardUUID: String
    let type: CardSolutionType

    private(set) var state: CardSolutionState?
    private(set) var isLoading = true
    private(set) var shouldClose = false

    private let controller: IosCardSolutionController
    private let evidenceStorage: CardEvidenceStorage
    private let syncScheduler: CardSyncBackgroundScheduler
    private var hasStarted = false
    private var didSave = false
    private var knownEvidencePaths = Set<String>()

    init(
        cardUUID: String,
        type: CardSolutionType,
        controller: IosCardSolutionController,
        evidenceStorage: CardEvidenceStorage = .shared,
        syncScheduler: CardSyncBackgroundScheduler
    ) {
        self.cardUUID = cardUUID
        self.type = type
        self.controller = controller
        self.evidenceStorage = evidenceStorage
        self.syncScheduler = syncScheduler
    }

    func start() async {
        guard !hasStarted else { return }
        hasStarted = true
        do {
            let started = try await controller.start(cardUuid: cardUUID, type: type) { [weak self] state in
                Task { @MainActor [weak self] in self?.apply(state) }
            }
            if !started.boolValue { shouldClose = true }
        } catch {
            shouldClose = true
        }
        isLoading = false
    }

    func openEmployeeSheet() { controller.openEmployeeSheet() }
    func dismissEmployeeSheet() { controller.dismissEmployeeSheet() }
    func searchEmployees(_ value: String) { controller.searchEmployees(value: value) }
    func selectEmployee(_ id: String) { controller.selectEmployee(id: id) }
    func updateComments(_ value: String) { controller.updateComments(value: value) }
    func dismissError() { controller.dismissError() }

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

    func removeEvidence(_ id: String) async {
        if let path = controller.removeEvidence(id: id) {
            await evidenceStorage.remove(path: path)
        }
    }

    func evidenceImportFailed() { controller.evidenceImportFailed() }

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

    func close() { shouldClose = true }

    func stop() {
        controller.stop()
        guard !didSave else { return }
        let paths = knownEvidencePaths
        Task { [evidenceStorage] in
            for path in paths { await evidenceStorage.remove(path: path) }
        }
    }

    private func apply(_ newState: CardSolutionState) {
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
}
