import Foundation
import Observation
import OSLog
import SharedLogic

@MainActor
@Observable
final class OplViewModel {
    let site: SessionSite
    private(set) var query = ""
    private(set) var selectedPath: [Level] = []
    private(set) var isSearching = false
    private(set) var isLoadingLevels = false
    var showsLevelSheet = false
    var levelQuery = ""
    private(set) var navigationPath: [Level] = []
    private(set) var opls: [Opl] = []
    private(set) var downloaded: [Opl] = []
    private(set) var hasSearched = false
    var error: String?

    private let controller: IosOplController
    private var levels: [Level] = []
    private var isObserving = false
    private var lifecycle = UUID()
    private var request: Task<Void, Never>?
    private let logger = Logger(subsystem: Bundle.main.bundleIdentifier ?? "OneSmartMate", category: "OPL")

    init(site: SessionSite, controller: IosOplController) {
        self.site = site
        self.controller = controller
    }

    var isBusy: Bool { isSearching || isLoadingLevels }
    var selectedLocation: String { selectedPath.map(\.name).joined(separator: " › ") }
    var selectedLevel: Level? { selectedPath.last }
    var canSearch: Bool { !isBusy && ((selectedLevel == nil && !query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
        || (selectedLevel != nil && query.isEmpty)) }
    var downloadedIDs: Set<Int64> { Set(downloaded.map(\.id)) }
    var showingDownloads: Bool { query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && selectedLevel == nil }

    func start() async {
        guard !isObserving else { return }
        isObserving = true
        let token = lifecycle
        controller.observeDownloads(siteId: site.id) { [weak self] lessons in
            Task { @MainActor [weak self] in
                guard let self, self.isObserving, self.lifecycle == token else { return }
                self.downloaded = lessons
                self.applyDownloads()
            }
        }
        await loadLevels(openSheet: false)
    }

    func stop() {
        lifecycle = UUID()
        request?.cancel()
        request = nil
        controller.stop()
        isObserving = false
        isSearching = false
        isLoadingLevels = false
        showsLevelSheet = false
    }

    func setQuery(_ value: String) {
        guard !isBusy, selectedLevel == nil else { return }
        query = value
        error = nil
        opls = showingDownloads ? downloaded : []
        hasSearched = showingDownloads && !downloaded.isEmpty
    }

    func clearLevel() {
        guard !isBusy else { return }
        selectedPath = []
        error = nil
        applyDownloads()
    }

    func openLevels() {
        guard !isBusy, query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        // Reserve synchronously before launching to reject rapid repeated taps.
        isLoadingLevels = true
        request = Task { await loadLevels(openSheet: true) }
    }

    private func loadLevels(openSheet: Bool) async {
        isLoadingLevels = true
        let token = lifecycle
        defer { if lifecycle == token { isLoadingLevels = false } }
        do {
            let result = try await controller.getLevels(siteId: site.id)
            guard lifecycle == token, !Task.isCancelled else { return }
            guard result.succeeded else { error = String(localized: OplStrings.levelsUnavailable); return }
            var seen = Set<String>()
            levels = result.levels.filter { $0.status.uppercased() == "A" && seen.insert($0.id).inserted }
            if openSheet {
                navigationPath = []
                levelQuery = ""
                showsLevelSheet = !levels.isEmpty
                if levels.isEmpty { error = String(localized: OplStrings.levelsUnavailable) }
            }
        } catch {
            guard lifecycle == token, !Task.isCancelled else { return }
            self.error = String(localized: OplStrings.levelsUnavailable)
        }
    }

    var choices: [AnatomySelectionItem] {
        let normalized = levelQuery.trimmingCharacters(in: .whitespacesAndNewlines)
        let ids = Set(levels.map(\.id))
        let source = levels.filter { level in
            if !normalized.isEmpty {
                return [level.name, level.description_, level.machineId, level.ownerName].compactMap { $0 }
                    .contains { $0.localizedCaseInsensitiveContains(normalized) }
            }
            if let current = navigationPath.last { return level.superiorId == current.id }
            return level.superiorId.isEmpty || level.superiorId == "0" || !ids.contains(level.superiorId)
        }
        return source.sorted { $0.depth == $1.depth ? $0.name.localizedStandardCompare($1.name) == .orderedAscending : $0.depth < $1.depth }
            .map { level in
                let subtitle = [level.machineId, level.ownerName].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " · ")
                return AnatomySelectionItem(id: level.id, title: level.name,
                    subtitle: subtitle.isEmpty ? level.description_ : subtitle, hasChildren: hasChildren(level.id))
            }
    }

    func navigate(_ id: String?) {
        guard !isBusy else { return }
        navigationPath = id.map(pathTo) ?? []
        levelQuery = ""
    }

    func select(_ id: String, allowParent: Bool = false) {
        guard !isBusy, query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              levels.contains(where: { $0.id == id }) else { return }
        if hasChildren(id), !allowParent { navigate(id); return }
        selectedPath = pathTo(id)
        query = ""
        showsLevelSheet = false
        levelQuery = ""
        search()
    }

    func selectCurrent() {
        if let id = navigationPath.last?.id { select(id, allowParent: true) }
    }

    private func hasChildren(_ id: String) -> Bool { levels.contains { $0.superiorId == id } }

    private func pathTo(_ id: String) -> [Level] {
        let indexed = Dictionary(uniqueKeysWithValues: levels.map { ($0.id, $0) })
        var path: [Level] = []
        var visited = Set<String>()
        var current = indexed[id]
        while let level = current, visited.insert(level.id).inserted {
            path.append(level)
            current = indexed[level.superiorId]
        }
        return Array(path.reversed())
    }

    func search() {
        guard canSearch else { return }
        let term = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard term.count <= 100 else { error = String(localized: OplStrings.queryTooLong); return }
        let levelID = selectedLevel?.id
        let token = lifecycle
        isSearching = true
        error = nil
        opls = []
        hasSearched = false
        request = Task {
            defer { if lifecycle == token { isSearching = false } }
            do {
                let result: IosOplListOutcome
                if let levelID { result = try await controller.getByLevel(siteId: site.id, levelId: levelID) }
                else { result = try await controller.search(siteId: site.id, query: term) }
                guard lifecycle == token, !Task.isCancelled else { return }
                if result.errorKind == nil || (levelID != nil && result.statusCode == 404) {
                    opls = result.opls
                    hasSearched = true
                    applyDownloads()
                    logger.info("OPL results site=\(self.site.id) count=\(self.opls.count)")
                } else { error = oplErrorMessage(kind: result.errorKind, status: result.statusCode) }
            } catch {
                guard lifecycle == token, !Task.isCancelled else { return }
                self.error = String(localized: OplStrings.searchFailed)
            }
        }
    }

    private func applyDownloads() {
        if showingDownloads, !isSearching {
            opls = downloaded
            hasSearched = !downloaded.isEmpty
        } else {
            let snapshots = Dictionary(uniqueKeysWithValues: downloaded.map { ($0.id, $0) })
            opls = opls.map { snapshots[$0.id] ?? $0 }
        }
    }

    deinit { controller.stop() }
}

func oplErrorMessage(kind: String?, status: Int32, detail: Bool = false) -> String {
    if status == 401 || status == 403 { return String(localized: OplStrings.accessDenied) }
    if kind == "CONNECTIVITY" { return String(localized: OplStrings.noConnection) }
    if detail, status == 404 { return String(localized: OplStrings.notFound) }
    return String(localized: detail ? OplStrings.loadFailed : OplStrings.searchFailed)
}
