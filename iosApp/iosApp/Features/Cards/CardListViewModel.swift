import Foundation
import Observation
import SharedLogic

enum CardListPrimaryFilter: String, CaseIterable, Identifiable {
    case active
    case assigned
    case overdue

    var id: Self { self }
}

enum CardListAdvancedFilter: String, CaseIterable, Identifiable {
    case allOpen
    case myOpen
    case myAssigned
    case unassigned
    case due
    case closed

    var id: Self { self }
}

@MainActor
@Observable
final class CardListViewModel {
    private(set) var cards: [Card] = []
    private(set) var count = 0
    private(set) var isInitialLoading = true
    private(set) var isRefreshing = false
    private(set) var errorMessage: String?
    private(set) var primaryFilter: CardListPrimaryFilter = .active
    private(set) var advancedFilter: CardListAdvancedFilter?
    private(set) var siteNames: [Int64: String] = [:]

    var query = "" {
        didSet {
            guard query != oldValue else { return }
            controller.setQuery(value: query)
        }
    }

    var isUsingAdvancedFilter: Bool { advancedFilter != nil }

    private let controller: IosCardListController
    private var hasStarted = false

    init(controller: IosCardListController) {
        self.controller = controller
    }

    func start(user: SessionUser) {
        siteNames = Dictionary(uniqueKeysWithValues: user.sites.map { ($0.id, $0.name) })
        guard !hasStarted else { return }
        hasStarted = true

        let sites = user.sites.map { IosCardSite(siteId: $0.id, name: $0.name) }
        controller.start(
            userId: user.id,
            sites: sites,
            todayIso: Self.todayISO
        ) { [weak self] state in
            Task { @MainActor [weak self] in
                self?.apply(state)
            }
        }
    }

    func selectPrimary(_ filter: CardListPrimaryFilter) {
        primaryFilter = filter
        advancedFilter = nil
        controller.selectFilter(filter: filter.sharedValue)
    }

    func selectAdvanced(_ filter: CardListAdvancedFilter) {
        advancedFilter = filter
        primaryFilter = .active
        controller.selectCustomFilter(filter: filter.sharedValue)
    }

    func clearFilters() {
        query = ""
        primaryFilter = .active
        advancedFilter = nil
        controller.clearFilters()
    }

    func refresh() async {
        do {
            try await controller.refresh()
        } catch is CancellationError {
            return
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func requestRefresh() {
        controller.requestRefresh()
    }

    func stop() {
        controller.stop()
        hasStarted = false
    }

    func dismissError() {
        controller.dismissError()
    }

    private func apply(_ state: IosCardListState) {
        cards = state.cards
        count = Int(state.totalCount)
        isInitialLoading = state.isInitialLoading
        isRefreshing = state.isRefreshing
        errorMessage = state.errorMessage
        query = state.query

        if let custom = state.customFilter {
            advancedFilter = CardListAdvancedFilter(custom)
            primaryFilter = .active
        } else {
            advancedFilter = nil
            primaryFilter = CardListPrimaryFilter(state.filter)
        }
    }

    private static var todayISO: String {
        Date.now.formatted(
            .iso8601.year().month().day().dateSeparator(.dash)
        )
    }
}

private extension CardListPrimaryFilter {
    init(_ value: IosCardListFilter) {
        switch value {
        case .assigned: self = .assigned
        case .overdue: self = .overdue
        case .open, .custom: self = .active
        default: self = .active
        }
    }

    var sharedValue: IosCardListFilter {
        switch self {
        case .active: .open
        case .assigned: .assigned
        case .overdue: .overdue
        }
    }
}

private extension CardListAdvancedFilter {
    init(_ value: IosCardListCustomFilter) {
        switch value {
        case .allOpen: self = .allOpen
        case .myOpen: self = .myOpen
        case .myAssigned: self = .myAssigned
        case .unassigned: self = .unassigned
        case .due: self = .due
        case .closed: self = .closed
        default: self = .allOpen
        }
    }

    var sharedValue: IosCardListCustomFilter {
        switch self {
        case .allOpen: .allOpen
        case .myOpen: .myOpen
        case .myAssigned: .myAssigned
        case .unassigned: .unassigned
        case .due: .due
        case .closed: .closed
        }
    }
}
