import Foundation
import Observation
import SharedLogic

@MainActor
@Observable
final class CardDetailViewModel {
    private(set) var card: Card?
    private(set) var isLoading = true
    private(set) var evidenceFiles: [String: String] = [:]
    private(set) var loadingEvidenceIDs = Set<String>()
    private(set) var failedEvidenceIDs = Set<String>()

    private let controller: IosCardDetailController
    private var observedUUID: String?

    init(controller: IosCardDetailController) {
        self.controller = controller
    }

    func start(uuid: String) {
        guard observedUUID != uuid else { return }
        observedUUID = uuid
        isLoading = true
        controller.observe(uuid: uuid) { [weak self] card in
            Task { @MainActor [weak self] in
                self?.card = card
                self?.isLoading = false
                self?.discardMissingCacheFiles()
            }
        }
    }

    func prepareImageEvidence() {
        card?.evidences
            .filter { $0.mediaType == .image }
            .forEach(resolve)
    }

    func resolve(_ evidence: CardEvidence) {
        guard evidenceFiles[evidence.id] == nil,
              !loadingEvidenceIDs.contains(evidence.id) else { return }
        loadingEvidenceIDs.insert(evidence.id)
        failedEvidenceIDs.remove(evidence.id)
        Task {
            do {
                let outcome = try await controller.resolveEvidence(evidence: evidence)
                loadingEvidenceIDs.remove(evidence.id)
                if let path = outcome.path {
                    evidenceFiles[evidence.id] = path
                    failedEvidenceIDs.remove(evidence.id)
                } else {
                    failedEvidenceIDs.insert(evidence.id)
                }
            } catch {
                loadingEvidenceIDs.remove(evidence.id)
                failedEvidenceIDs.insert(evidence.id)
            }
        }
    }

    func stop() {
        controller.stop()
        observedUUID = nil
    }

    private func discardMissingCacheFiles() {
        evidenceFiles = evidenceFiles.filter { FileManager.default.fileExists(atPath: $0.value) }
    }
}
