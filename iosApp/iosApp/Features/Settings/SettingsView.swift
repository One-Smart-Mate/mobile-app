import SharedLogic
import SwiftUI

struct SettingsView: View {
    let user: SessionUser
    @Bindable var viewModel: SettingsViewModel
    @Bindable var permissionsViewModel: PermissionsViewModel
    let catalogSyncState: CatalogSyncViewState
    let catalogSyncNetworkError: CatalogSyncNetworkBlockReason?
    let onDismissCatalogSyncError: () -> Void
    let onSyncCatalogs: () -> Void

    @State private var showsTransientCatalogSyncState = false
    @State private var showsCatalogSyncConfirmation = false

    var body: some View {
        List {
            accountSummary

            if let error = viewModel.logoutError {
                AnatomyBanner(
                    message: error,
                    type: .error,
                    onDismiss: viewModel.dismissError
                )
                .listRowInsets(.init(top: 6, leading: 16, bottom: 6, trailing: 16))
                .listRowBackground(Color.osmBackground)
            }

            if let catalogSyncNetworkError {
                AnatomyBanner(
                    message: String(localized: catalogSyncNetworkError.message),
                    type: .error,
                    onDismiss: onDismissCatalogSyncError
                )
                .listRowInsets(.init(top: 6, leading: 16, bottom: 6, trailing: 16))
                .listRowBackground(Color.osmBackground)
            }

            Section(AppStrings.Settings.accountSection) {
                Button {
                    viewModel.showsAccountInformation = true
                } label: {
                    SettingsRow(
                        icon: "person.crop.circle",
                        title: AppStrings.Settings.information,
                        subtitle: AppStrings.Settings.informationDescription,
                        trailing: .chevron
                    )
                }
                .buttonStyle(.plain)
            }

            Section(AppStrings.Settings.applicationSection) {
                Button {
                    Task {
                        await permissionsViewModel.refresh()
                        permissionsViewModel.showsSheet = true
                    }
                } label: {
                    SettingsRow(
                        icon: "checkmark.shield",
                        title: AppStrings.Settings.permissions,
                        subtitle: AppStrings.Settings.permissionsDescription,
                        trailing: missingPermissions > 0
                            ? .pending(missingPermissions)
                            : .chevron
                    )
                }
                .buttonStyle(.plain)

                ManualCatalogSyncCard(
                    state: displayedCatalogSyncState,
                    isBusy: isCatalogSyncBusy,
                    action: { showsCatalogSyncConfirmation = true }
                )
                .listRowInsets(.init(top: 8, leading: 0, bottom: 8, trailing: 0))
                .listRowBackground(Color.osmBackground)

                Toggle(
                    isOn: Binding(
                        get: { viewModel.allowMobileData },
                        set: viewModel.setAllowMobileData
                    )
                ) {
                    SettingsRow(
                        icon: "antenna.radiowaves.left.and.right",
                        title: AppStrings.Settings.mobileData,
                        subtitle: AppStrings.Settings.mobileDataDescription
                    )
                }
                .tint(Color.osmPrimary)

                SettingsRow(
                    icon: "info.circle",
                    title: AppStrings.Settings.version,
                    subtitle: LocalizedStringResource(stringLiteral: appVersion)
                )
            }

            Section {
                Button(role: .destructive, action: viewModel.requestLogout) {
                    SettingsRow(
                        icon: "rectangle.portrait.and.arrow.right",
                        title: AppStrings.Settings.logout,
                        trailing: viewModel.isLoggingOut ? .progress : .chevron,
                        tint: .osmError
                    )
                }
                .buttonStyle(.plain)
                .disabled(viewModel.isLoggingOut)
            }
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
        .background(Color.osmBackground)
        .navigationTitle(AppStrings.Settings.title)
        .navigationBarTitleDisplayMode(.large)
        .sheet(isPresented: $viewModel.showsAccountInformation) {
            AccountInformationSheet(user: user)
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
                .presentationBackground(Color.osmSurface)
        }
        .alert(
            AppStrings.Settings.logoutWarningTitle,
            isPresented: $viewModel.showsLogoutWarning
        ) {
            Button(AppStrings.Common.cancel, role: .cancel) {}
            Button(AppStrings.Settings.logout, role: .destructive) {
                viewModel.confirmLogout()
            }
        } message: {
            Text(logoutWarningMessage)
        }
        .alert(
            AppStrings.CatalogSync.manualConfirmTitle,
            isPresented: $showsCatalogSyncConfirmation
        ) {
            Button(AppStrings.Common.cancel, role: .cancel) {}
            Button(AppStrings.CatalogSync.manualConfirmAction) {
                onSyncCatalogs()
            }
        } message: {
            Text(AppStrings.CatalogSync.manualConfirmBody)
        }
        .task {
            viewModel.start()
            await permissionsViewModel.refresh()
        }
        .task(id: isTransientCatalogSyncState) {
            showsTransientCatalogSyncState = false
            guard isTransientCatalogSyncState else { return }

            do {
                try await Task.sleep(for: .milliseconds(500))
            } catch {
                return
            }

            guard !Task.isCancelled else { return }
            showsTransientCatalogSyncState = true
        }
    }

    private var accountSummary: some View {
        HStack(spacing: 12) {
            AnatomyImage(source: .remote(user.logoURL), contentMode: .fill)
                .frame(width: 52, height: 52)
                .clipShape(Circle())

            VStack(alignment: .leading, spacing: 3) {
                Text(user.name)
                    .font(.headline)
                    .foregroundStyle(Color.osmOnSurface)
                    .lineLimit(1)
                Text(user.email)
                    .font(.subheadline)
                    .foregroundStyle(Color.osmOnSurfaceVariant)
                    .lineLimit(1)
                Text(accountScope)
                    .font(.caption)
                    .foregroundStyle(Color.osmOnSurfaceVariant)
                    .lineLimit(1)
            }
        }
        .padding(.vertical, 4)
        .listRowBackground(Color.osmSurface)
    }

    private var missingPermissions: Int {
        permissionsViewModel.items.count {
            $0.status == .missing || $0.status == .partial || $0.status == .denied
        }
    }

    private var isTransientCatalogSyncState: Bool {
        switch catalogSyncState {
        case .waitingForNetwork, .downloading:
            true
        case .idle, .failed:
            false
        }
    }

    private var displayedCatalogSyncState: CatalogSyncViewState {
        switch catalogSyncState {
        case .failed:
            catalogSyncState
        case .waitingForNetwork, .downloading:
            showsTransientCatalogSyncState ? catalogSyncState : .idle
        case .idle:
            catalogSyncState
        }
    }

    private var isCatalogSyncBusy: Bool {
        switch catalogSyncState {
        case .waitingForNetwork, .downloading:
            true
        case .idle, .failed:
            false
        }
    }

    private var appVersion: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "—"
    }

    private var accountScope: String {
        ([user.companyName] + user.sites.map(\.name))
            .filter { !$0.isEmpty }
            .joined(separator: " · ")
    }

    private var logoutWarningMessage: String {
        String(
            format: String(localized: AppStrings.Settings.logoutWarningBody),
            locale: .current,
            Int64(viewModel.pendingCardCount)
        )
    }
}

private struct AccountInformationSheet: View {
    let user: SessionUser
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                informationRow(AppStrings.Settings.name, user.name)
                informationRow(AppStrings.Settings.email, user.email)
                informationRow(AppStrings.Settings.company, user.companyName)
                informationRow(
                    AppStrings.Settings.sites,
                    user.sites.map(\.name).joined(separator: " · ").nonEmpty
                        ?? String(localized: AppStrings.Settings.noSites)
                )
                informationRow(
                    AppStrings.Settings.roles,
                    user.roles.joined(separator: " · ").nonEmpty
                        ?? String(localized: AppStrings.Settings.noRoles)
                )
            }
            .listStyle(.insetGrouped)
            .scrollContentBackground(.hidden)
            .background(Color.osmBackground)
            .navigationTitle(AppStrings.Settings.accountInformationTitle)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(AppStrings.Settings.close) { dismiss() }
                }
            }
        }
    }

    private func informationRow(_ title: LocalizedStringResource, _ value: String) -> some View {
        LabeledContent {
            Text(value)
                .foregroundStyle(Color.osmOnSurface)
                .multilineTextAlignment(.trailing)
        } label: {
            Text(title)
                .foregroundStyle(Color.osmOnSurfaceVariant)
        }
    }
}

private struct SettingsRow: View {
    enum Trailing {
        case none
        case chevron
        case pending(Int)
        case progress
    }

    let icon: String
    let title: LocalizedStringResource
    var subtitle: LocalizedStringResource?
    var trailing: Trailing = .none
    var tint: Color = .osmOnSurface

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 17, weight: .medium))
                .foregroundStyle(tint == .osmError ? tint : Color.osmPrimary)
                .frame(width: 30)

            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.body)
                    .foregroundStyle(tint)
                if let subtitle {
                    Text(subtitle)
                        .font(.caption)
                        .foregroundStyle(Color.osmOnSurfaceVariant)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            trailingContent
        }
        .contentShape(Rectangle())
    }

    @ViewBuilder
    private var trailingContent: some View {
        switch trailing {
        case .none:
            EmptyView()
        case .chevron:
            Image(systemName: "chevron.right")
                .font(.caption.weight(.semibold))
                .foregroundStyle(tint == .osmError ? tint : Color.osmOnSurfaceVariant)
        case let .pending(count):
            HStack(spacing: 5) {
                Text(
                    String(
                        format: String(localized: AppStrings.Settings.permissionsPending),
                        locale: .current,
                        Int64(count)
                    )
                )
                .font(.caption2.weight(.semibold))
                .foregroundStyle(Color.osmOnWarningContainer)
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(Color.osmWarningContainer)
                .clipShape(Capsule())
                Image(systemName: "chevron.right")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(Color.osmOnSurfaceVariant)
            }
        case .progress:
            ProgressView()
                .controlSize(.small)
                .tint(Color.osmError)
        }
    }
}

private extension String {
    var nonEmpty: String? { isEmpty ? nil : self }
}
