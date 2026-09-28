import Network
import Observation

enum NetworkConnectionStatus: Sendable, Equatable {
    case wifiConnected
    case wifiNoInternet
    case cellularConnected
    case cellularNoInternet
    case offline
}

@MainActor
@Observable
final class NetworkStatusMonitor {
    private(set) var status: NetworkConnectionStatus = .offline

    @ObservationIgnored private let monitor = NWPathMonitor()
    @ObservationIgnored private let queue = DispatchQueue(label: "com.ih.osm.network-monitor")

    init() {
        monitor.pathUpdateHandler = { [weak self] path in
            let status = Self.resolve(path)
            Task { @MainActor [weak self] in
                self?.status = status
            }
        }
        monitor.start(queue: queue)
    }

    nonisolated private static func resolve(_ path: NWPath) -> NetworkConnectionStatus {
        if path.status == .satisfied {
            if path.usesInterfaceType(.wifi) {
                return .wifiConnected
            }
            if path.usesInterfaceType(.cellular) {
                return .cellularConnected
            }
        }

        let interfaces = path.availableInterfaces.map(\.type)
        if interfaces.contains(.wifi) {
            return .wifiNoInternet
        }
        if interfaces.contains(.cellular) {
            return .cellularNoInternet
        }
        return .offline
    }
}
