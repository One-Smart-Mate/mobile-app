import SwiftUI
import SharedLogic

enum MainTab: Hashable {
    case home
    case cards
    case settings
}

struct MainTabRoot: View {
    let user: SessionUser

    @Environment(\.scenePhase) private var scenePhase
    @State private var selectedTab: MainTab = .home
    @State private var selectedSiteID: Int64?
    @State private var networkMonitor = NetworkStatusMonitor()
    @State private var catalogSyncViewModel: CatalogSyncViewModel?
    @State private var cardListViewModel: CardListViewModel?
    @State private var cardNavigationPath: [String] = []
    @State private var createCardViewModel: CreateCardViewModel?
    @State private var cardSolutionViewModel: CardSolutionViewModel?
    @State private var permissionsViewModel = PermissionsViewModel()
    @State private var cardSyncScheduler = CardSyncBackgroundScheduler.shared
    private let makeCreateCardViewModel: @MainActor () -> CreateCardViewModel?
    private let makeCardDetailViewModel: @MainActor () -> CardDetailViewModel?
    private let makeCardSolutionViewModel: @MainActor (String, CardSolutionType) -> CardSolutionViewModel?

    init(
        user: SessionUser,
        catalogSyncViewModel: CatalogSyncViewModel? = nil,
        cardListViewModel: CardListViewModel? = nil,
        makeCardDetailViewModel: @escaping @MainActor () -> CardDetailViewModel? = { nil },
        makeCreateCardViewModel: @escaping @MainActor () -> CreateCardViewModel? = { nil },
        makeCardSolutionViewModel: @escaping @MainActor (String, CardSolutionType) -> CardSolutionViewModel? = { _, _ in nil }
    ) {
        self.user = user
        self.makeCreateCardViewModel = makeCreateCardViewModel
        self.makeCardDetailViewModel = makeCardDetailViewModel
        self.makeCardSolutionViewModel = makeCardSolutionViewModel
        _selectedSiteID = State(initialValue: user.sites.first?.id)
        _catalogSyncViewModel = State(initialValue: catalogSyncViewModel)
        _cardListViewModel = State(initialValue: cardListViewModel)
    }

    var body: some View {
        TabView(selection: $selectedTab) {
            NavigationStack {
                HomeView(
                    user: user,
                    selectedSiteID: $selectedSiteID,
                    networkStatus: networkMonitor.status,
                    catalogSyncState: catalogSyncViewModel?.state ?? .idle,
                    pendingCardCount: cardSyncScheduler.pendingCount,
                    isCardSyncing: cardSyncScheduler.isSyncing,
                    cardSyncCompleted: cardSyncScheduler.completed,
                    cardSyncTotal: cardSyncScheduler.total,
                    onSyncPendingCards: cardSyncScheduler.syncManually,
                    onCreateNote: presentCreateCard,
                    onOpenNotes: { selectedTab = .cards }
                )
            }
            .tabItem { Label(AppStrings.Navigation.home, systemImage: "house") }
            .tag(MainTab.home)

            NavigationStack(path: $cardNavigationPath) {
                Group {
                    if let cardListViewModel {
                        CardListView(
                            user: user,
                            viewModel: cardListViewModel,
                            onCreateCard: presentCreateCard,
                            onOpenCard: { cardNavigationPath.append($0) },
                            onApplyProvisionalSolution: { presentSolution(cardUUID: $0, type: .provisional) },
                            onApplyDefinitiveSolution: { presentSolution(cardUUID: $0, type: .definitive) }
                        )
                    } else {
                        ProgressView()
                    }
                }
                .navigationDestination(for: String.self) { uuid in
                    if let detailViewModel = makeCardDetailViewModel() {
                        CardDetailView(
                            uuid: uuid,
                            siteNames: Dictionary(uniqueKeysWithValues: user.sites.map { ($0.id, $0.name) }),
                            viewModel: detailViewModel
                        )
                    } else {
                        ContentUnavailableView(AppStrings.CardDetail.notFound, systemImage: "doc.text.magnifyingglass")
                    }
                }
            }
            .tabItem { Label(AppStrings.Navigation.cards, systemImage: "doc.text") }
            .tag(MainTab.cards)

            NavigationStack { SettingsView() }
                .tabItem { Label(AppStrings.Navigation.settings, systemImage: "gearshape") }
                .tag(MainTab.settings)
        }
        .tint(.osmPrimary)
        .sheet(isPresented: $permissionsViewModel.showsSheet) {
            PermissionsSheet(viewModel: permissionsViewModel)
        }
        .fullScreenCover(item: $createCardViewModel) { presentedViewModel in
            NavigationStack {
                CreateCardView(
                    viewModel: presentedViewModel,
                    siteID: selectedSiteID,
                    onClose: {
                        presentedViewModel.stop()
                        createCardViewModel = nil
                    }
                )
            }
        }
        .fullScreenCover(item: $cardSolutionViewModel) { presentedViewModel in
            NavigationStack {
                CardSolutionView(
                    viewModel: presentedViewModel,
                    onClose: {
                        presentedViewModel.stop()
                        cardSolutionViewModel = nil
                    }
                )
            }
        }
        .task {
            catalogSyncViewModel?.syncIfNeeded()
            PushNotificationCoordinator.shared.sessionDidBecomeAvailable()
            await permissionsViewModel.autoPromptIfNeeded()
        }
        .onDisappear { cardListViewModel?.stop() }
        .onChange(of: scenePhase) { _, phase in
            switch phase {
            case .active:
                catalogSyncViewModel?.syncIfNeeded()
                Task { await permissionsViewModel.refresh() }
            case .background:
                catalogSyncViewModel?.appDidEnterBackground()
                CardSyncBackgroundScheduler.shared.appDidEnterBackground()
            case .inactive:
                break
            @unknown default:
                break
            }
        }
    }

    private func presentCreateCard() {
        createCardViewModel = makeCreateCardViewModel()
    }

    private func presentSolution(cardUUID: String, type: CardSolutionType) {
        cardSolutionViewModel = makeCardSolutionViewModel(cardUUID, type)
    }
}

struct HomeView: View {
    let user: SessionUser
    @Binding var selectedSiteID: Int64?
    let networkStatus: NetworkConnectionStatus
    let catalogSyncState: CatalogSyncViewState
    let pendingCardCount: Int
    let isCardSyncing: Bool
    let cardSyncCompleted: Int
    let cardSyncTotal: Int
    let onSyncPendingCards: () -> Void
    let onCreateNote: () -> Void
    let onOpenNotes: () -> Void

    init(
        user: SessionUser,
        selectedSiteID: Binding<Int64?>,
        networkStatus: NetworkConnectionStatus,
        catalogSyncState: CatalogSyncViewState,
        pendingCardCount: Int,
        isCardSyncing: Bool,
        cardSyncCompleted: Int,
        cardSyncTotal: Int,
        onSyncPendingCards: @escaping () -> Void,
        onCreateNote: @escaping () -> Void,
        onOpenNotes: @escaping () -> Void
    ) {
        self.user = user
        _selectedSiteID = selectedSiteID
        self.networkStatus = networkStatus
        self.catalogSyncState = catalogSyncState
        self.pendingCardCount = pendingCardCount
        self.isCardSyncing = isCardSyncing
        self.cardSyncCompleted = cardSyncCompleted
        self.cardSyncTotal = cardSyncTotal
        self.onSyncPendingCards = onSyncPendingCards
        self.onCreateNote = onCreateNote
        self.onOpenNotes = onOpenNotes
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                header

                if !user.sites.isEmpty {
                    AnatomyDropdown(
                        options: user.sites.map {
                            AnatomyDropdownOption(id: $0.id, title: $0.name)
                        },
                        selection: $selectedSiteID
                    )
                    .padding(.top, OSMSpacing.md)
                }

                NetworkStatusBadge(status: networkStatus)
                    .padding(.top, OSMSpacing.sm)

                if catalogSyncState != .idle {
                    CatalogSyncStatusCard(state: catalogSyncState)
                        .padding(.top, OSMSpacing.sm)
                }

                if pendingCardCount > 0 {
                    PendingCardsSyncCard(
                        count: pendingCardCount,
                        isSyncing: isCardSyncing,
                        completed: cardSyncCompleted,
                        total: cardSyncTotal,
                        action: onSyncPendingCards
                    )
                    .padding(.top, OSMSpacing.sm)
                }

                AnatomyText(
                    AppStrings.Home.quickActions,
                    font: OSMTypography.headline
                )
                .padding(.top, OSMSpacing.lg)
                .padding(.bottom, OSMSpacing.sm)

                LazyVGrid(
                    columns: [
                        GridItem(.flexible(), spacing: OSMSpacing.sm),
                        GridItem(.flexible(), spacing: OSMSpacing.sm)
                    ],
                    spacing: OSMSpacing.sm
                ) {
                    QuickActionCard(
                        title: AppStrings.Home.newNote,
                        systemImage: "note.text.badge.plus",
                        action: onCreateNote
                    )
                    QuickActionCard(
                        title: AppStrings.Home.scanQR,
                        systemImage: "qrcode.viewfinder",
                        action: {}
                    )
                    QuickActionCard(
                        title: AppStrings.Home.fastPassword,
                        systemImage: "key",
                        action: {}
                    )
                    QuickActionCard(
                        title: AppStrings.Home.notes,
                        systemImage: "doc.text",
                        action: onOpenNotes
                    )
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, OSMSpacing.md)
            .frame(maxWidth: 680)
            .frame(maxWidth: .infinity)
        }
        .background(Color.osmBackground.ignoresSafeArea())
        .onChange(of: user.sites) { _, sites in
            if !sites.contains(where: { $0.id == selectedSiteID }) {
                selectedSiteID = sites.first?.id
            }
        }
    }

    private var selectedSite: SessionSite? {
        user.sites.first { $0.id == selectedSiteID } ?? user.sites.first
    }

    private var header: some View {
        HStack(alignment: .top, spacing: OSMSpacing.sm) {
            VStack(alignment: .leading, spacing: OSMSpacing.xxs) {
                AnatomyText(
                    verbatim: user.companyName,
                    font: OSMTypography.title2,
                    lineLimit: 1
                )
                Text(
                    String(
                        format: String(localized: AppStrings.Home.greeting),
                        locale: .current,
                        firstName
                    )
                )
                .font(OSMTypography.callout)
                .foregroundStyle(Color.osmOnSurfaceVariant)

                if !user.roles.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 6) {
                            ForEach(user.roles, id: \.self) { role in
                                Text(role)
                                    .font(.caption2.weight(.medium))
                                    .foregroundStyle(Color.osmOnSurfaceVariant)
                                    .padding(.horizontal, 9)
                                    .padding(.vertical, 4)
                                    .background(Color.osmSurfaceVariant)
                                    .clipShape(Capsule())
                            }
                        }
                    }
                    .padding(.top, 4)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            AnatomyImage(
                source: .remote(selectedSite?.logoURL ?? user.logoURL),
                accessibilityLabel: selectedSite?.name,
                placeholderSystemImage: "building.2"
            )
            .frame(width: 52, height: 52)
            .clipShape(Circle())
            .overlay { Circle().strokeBorder(Color.osmOutlineVariant, lineWidth: 1) }
        }
    }

    private var firstName: String {
        user.name.split(separator: " ").first.map(String.init) ?? user.name
    }
}

private struct PendingCardsSyncCard: View {
    let count: Int
    let isSyncing: Bool
    let completed: Int
    let total: Int
    let action: () -> Void

    var body: some View {
        AnatomyCard(action: action) {
            HStack(spacing: 12) {
                Image(systemName: "icloud.and.arrow.up")
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(Color.osmPrimary)
                    .frame(width: 42, height: 42)
                    .background(Color.osmPrimaryContainer)
                    .clipShape(Circle())

                VStack(alignment: .leading, spacing: 4) {
                    Text(AppStrings.Home.pendingCardsTitle)
                        .font(.subheadline.weight(.semibold))
                    Text(
                        String(
                            format: String(localized: AppStrings.Home.pendingCardsBody),
                            locale: .current,
                            Int64(count)
                        )
                    )
                    .font(.caption)
                    .foregroundStyle(Color.osmOnSurfaceVariant)

                    Text(statusText)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(Color.osmPrimary)

                    if isSyncing {
                        if total > 0 {
                            ProgressView(value: Double(completed), total: Double(total))
                                .tint(Color.osmPrimary)
                        } else {
                            ProgressView().tint(Color.osmPrimary)
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .allowsHitTesting(!isSyncing)
        .opacity(isSyncing ? 0.9 : 1)
    }

    private var statusText: String {
        if isSyncing {
            return total > 0
                ? String(
                    format: String(localized: AppStrings.CardSync.progress),
                    locale: .current,
                    Int64(completed), Int64(total)
                )
                : String(localized: AppStrings.CardSync.preparing)
        }
        return String(localized: AppStrings.Home.pendingCardsAction)
    }
}

private struct QuickActionCard: View {
    let title: LocalizedStringResource
    let systemImage: String
    let action: () -> Void

    var body: some View {
        AnatomyCard(action: action) {
            VStack(spacing: OSMSpacing.sm) {
                Image(systemName: systemImage)
                    .font(.system(size: 27, weight: .medium))
                    .foregroundStyle(Color.osmPrimary)
                Text(title)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Color.osmOnSurface)
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
            }
            .frame(maxWidth: .infinity, minHeight: 76)
        }
    }
}

private struct NetworkStatusBadge: View {
    let status: NetworkConnectionStatus

    var body: some View {
        HStack(spacing: 7) {
            Image(systemName: icon)
                .font(.caption.weight(.semibold))
            Circle().frame(width: 6, height: 6)
            Text(label)
                .font(.caption.weight(.medium))
        }
        .foregroundStyle(foreground)
        .padding(.horizontal, 11)
        .padding(.vertical, 6)
        .background(background)
        .clipShape(Capsule())
    }

    private var icon: String {
        switch status {
        case .wifiConnected, .wifiNoInternet: "wifi"
        case .cellularConnected, .cellularNoInternet: "antenna.radiowaves.left.and.right"
        case .offline: "wifi.slash"
        }
    }

    private var label: LocalizedStringResource {
        switch status {
        case .wifiConnected: AppStrings.Network.wifiConnected
        case .wifiNoInternet: AppStrings.Network.wifiNoInternet
        case .cellularConnected: AppStrings.Network.mobileConnected
        case .cellularNoInternet: AppStrings.Network.mobileNoInternet
        case .offline: AppStrings.Network.offline
        }
    }

    private var background: Color {
        switch status {
        case .wifiConnected, .cellularConnected: .osmPrimaryContainer
        case .wifiNoInternet, .cellularNoInternet: .osmWarningContainer
        case .offline: .osmErrorContainer
        }
    }

    private var foreground: Color {
        switch status {
        case .wifiConnected, .cellularConnected: .osmOnPrimaryContainer
        case .wifiNoInternet, .cellularNoInternet: .osmOnWarningContainer
        case .offline: .osmOnErrorContainer
        }
    }
}

private struct CatalogSyncStatusCard: View {
    let state: CatalogSyncViewState

    var body: some View {
        AnatomyCard(variant: .filled) {
            HStack(alignment: .top, spacing: OSMSpacing.sm) {
                Image(systemName: isFailure ? "icloud.slash" : "icloud.and.arrow.down")
                    .font(.system(size: 20, weight: .medium))
                    .foregroundStyle(isFailure ? Color.osmError : Color.osmPrimary)
                    .accessibilityHidden(true)

                VStack(alignment: .leading, spacing: OSMSpacing.xs) {
                    Text(AppStrings.CatalogSync.title)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.osmOnSurface)

                    Text(statusText)
                        .font(.caption)
                        .foregroundStyle(isFailure ? Color.osmError : Color.osmOnSurfaceVariant)

                    switch state {
                    case .waitingForNetwork:
                        ProgressView()
                            .controlSize(.small)
                            .tint(Color.osmPrimary)
                    case let .downloading(progress, _):
                        ProgressView(value: progress)
                            .tint(Color.osmPrimary)
                    case .idle, .failed:
                        EmptyView()
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }

    private var isFailure: Bool {
        if case .failed = state { return true }
        return false
    }

    private var statusText: String {
        switch state {
        case .idle:
            return ""
        case .waitingForNetwork:
            return String(localized: AppStrings.CatalogSync.waiting)
        case let .downloading(_, catalogKey):
            return String(localized: catalogLabel(for: catalogKey))
        case let .failed(message):
            return message
        }
    }

    private func catalogLabel(for key: String?) -> LocalizedStringResource {
        switch key {
        case CatalogSyncSelection.cardTypes.rawValue:
            AppStrings.CatalogSync.cardTypes
        case CatalogSyncSelection.preclassifiers.rawValue:
            AppStrings.CatalogSync.preclassifiers
        case CatalogSyncSelection.priorities.rawValue:
            AppStrings.CatalogSync.priorities
        case CatalogSyncSelection.levels.rawValue:
            AppStrings.CatalogSync.levels
        case CatalogSyncSelection.employees.rawValue:
            AppStrings.CatalogSync.employees
        default:
            AppStrings.CatalogSync.starting
        }
    }
}

private let previewUser = SessionUser(
    id: 1,
    name: "Diego López",
    email: "diego@empresa.com",
    companyName: "Industrias Nova",
    roles: ["Supervisor", "Operaciones"],
    sites: [
        SessionSite(id: 1, name: "Planta Norte"),
        SessionSite(id: 2, name: "Planta Sur")
    ]
)

#Preview("Home · Light") {
    OneSmartMateTheme {
        MainTabRoot(user: previewUser)
    }
    .preferredColorScheme(.light)
}

#Preview("Home · Dark") {
    OneSmartMateTheme {
        MainTabRoot(user: previewUser)
    }
    .preferredColorScheme(.dark)
}

#Preview("Home · Landscape", traits: .landscapeLeft) {
    OneSmartMateTheme {
        MainTabRoot(user: previewUser)
    }
}
