import SwiftUI

enum MainTab: Hashable {
    case home
    case notes
    case settings
}

struct MainTabRoot: View {
    let user: SessionUser

    @State private var selectedTab: MainTab = .home
    @State private var presentsCreateNote = false
    @State private var networkMonitor = NetworkStatusMonitor()

    var body: some View {
        TabView(selection: $selectedTab) {
            NavigationStack {
                HomeView(
                    user: user,
                    networkStatus: networkMonitor.status,
                    onCreateNote: { presentsCreateNote = true },
                    onOpenNotes: { selectedTab = .notes }
                )
                .navigationDestination(isPresented: $presentsCreateNote) {
                    CreateNoteView()
                }
            }
            .tabItem { Label(AppStrings.Navigation.home, systemImage: "house") }
            .tag(MainTab.home)

            NavigationStack { NotesView() }
                .tabItem { Label(AppStrings.Navigation.notes, systemImage: "doc.text") }
                .tag(MainTab.notes)

            NavigationStack { SettingsView() }
                .tabItem { Label(AppStrings.Navigation.settings, systemImage: "gearshape") }
                .tag(MainTab.settings)
        }
        .tint(.osmPrimary)
    }
}

struct HomeView: View {
    let user: SessionUser
    let networkStatus: NetworkConnectionStatus
    let onCreateNote: () -> Void
    let onOpenNotes: () -> Void

    @State private var selectedSiteID: Int64?

    init(
        user: SessionUser,
        networkStatus: NetworkConnectionStatus,
        onCreateNote: @escaping () -> Void,
        onOpenNotes: @escaping () -> Void
    ) {
        self.user = user
        self.networkStatus = networkStatus
        self.onCreateNote = onCreateNote
        self.onOpenNotes = onOpenNotes
        _selectedSiteID = State(initialValue: user.sites.first?.id)
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
