import Foundation
import Observation
import SharedLogic

struct SessionSite: Equatable, Identifiable {
    let id: Int64
    let name: String
    let logoURL: URL?

    init(_ site: UserSite_) {
        id = site.id
        name = site.name
        logoURL = site.logo.flatMap(URL.init(string:))
    }

    init(id: Int64, name: String, logoURL: URL? = nil) {
        self.id = id
        self.name = name
        self.logoURL = logoURL
    }
}

struct SessionUser: Equatable {
    let id: Int64
    let name: String
    let email: String
    let companyName: String
    let roles: [String]
    let logoURL: URL?
    let sites: [SessionSite]

    init(_ user: AuthenticatedUser) {
        id = user.id
        name = user.name
        email = user.email
        companyName = user.companyName
        roles = user.roles
        logoURL = user.logo.flatMap(URL.init(string:))
        sites = user.sites.map(SessionSite.init)
    }

    init(
        id: Int64,
        name: String,
        email: String,
        companyName: String,
        roles: [String] = [],
        logoURL: URL? = nil,
        sites: [SessionSite] = []
    ) {
        self.id = id
        self.name = name
        self.email = email
        self.companyName = companyName
        self.roles = roles
        self.logoURL = logoURL
        self.sites = sites
    }
}

enum AppSessionState: Equatable {
    case loading
    case signedOut
    case signedIn(SessionUser)
}

@MainActor
@Observable
final class SessionViewModel {
    typealias StatusObservation = (@escaping (any SessionStatus) -> Void) -> Void
    typealias SessionRestoration = () async throws -> Void
    typealias ObservationStop = () -> Void

    private(set) var state: AppSessionState
    private let observeStatus: StatusObservation
    private let restoreSession: SessionRestoration
    private let stopObservation: ObservationStop
    private var hasStarted = false

    init(
        initialState: AppSessionState = .loading,
        observeStatus: @escaping StatusObservation,
        restoreSession: @escaping SessionRestoration,
        stopObservation: @escaping ObservationStop
    ) {
        state = initialState
        self.observeStatus = observeStatus
        self.restoreSession = restoreSession
        self.stopObservation = stopObservation
    }

    func start() async {
        guard !hasStarted else { return }
        hasStarted = true
        observeStatus { [weak self] status in
            Task { @MainActor [weak self] in self?.apply(status) }
        }
        do {
            try await restoreSession()
        } catch {
            guard !Task.isCancelled else { return }
            state = .signedOut
        }
    }

    func stop() {
        stopObservation()
        hasStarted = false
    }

    private func apply(_ status: any SessionStatus) {
        switch status {
        case let authenticated as SessionStatusAuthenticated:
            state = .signedIn(SessionUser(authenticated.user))
        case is SessionStatusUnauthenticated:
            state = .signedOut
        default:
            state = .loading
        }
    }
}
