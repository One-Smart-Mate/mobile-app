import SwiftUI

struct PermissionsSheet: View {
    @Bindable var viewModel: PermissionsViewModel

    var body: some View {
        NavigationStack {
            VStack(spacing: 14) {
                Image(systemName: "checkmark.shield")
                    .font(.system(size: 27, weight: .semibold))
                    .foregroundStyle(Color.osmPrimary)
                    .frame(width: 58, height: 58)
                    .background(Color.osmPrimaryContainer)
                    .clipShape(Circle())

                VStack(spacing: 5) {
                    Text(AppStrings.Permissions.title)
                        .font(OSMTypography.title2)
                    Text(AppStrings.Permissions.subtitle)
                        .font(.subheadline)
                        .foregroundStyle(Color.osmOnSurfaceVariant)
                        .multilineTextAlignment(.center)
                }

                ScrollView {
                    LazyVStack(spacing: 9) {
                        ForEach(viewModel.items) { item in
                            PermissionRow(item: item)
                        }
                    }
                    .padding(.vertical, 4)
                }

                AnatomyButton(
                    title: viewModel.hasDeniedPermission
                        ? AppStrings.Permissions.openSettings
                        : AppStrings.Permissions.continueAction,
                    action: { Task { await viewModel.requestPermissions() } },
                    isLoading: viewModel.isRequesting
                )
                AnatomyButton(
                    title: AppStrings.Permissions.notNow,
                    action: viewModel.dismiss,
                    variant: .secondary
                )
                .disabled(viewModel.isRequesting)
            }
            .padding(.horizontal, 20)
            .padding(.top, 22)
            .padding(.bottom, 12)
            .background(Color.osmSurface.ignoresSafeArea())
        }
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
    }
}

private struct PermissionRow: View {
    let item: AppPermissionItem

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: visual.icon)
                .font(.system(size: 19, weight: .medium))
                .foregroundStyle(Color.osmPrimary)
                .frame(width: 42, height: 42)
                .background(Color.osmPrimaryContainer)
                .clipShape(Circle())

            VStack(alignment: .leading, spacing: 2) {
                Text(visual.title)
                    .font(.subheadline.weight(.semibold))
                Text(visual.description)
                    .font(.caption)
                    .foregroundStyle(Color.osmOnSurfaceVariant)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            VStack(spacing: 2) {
                Image(systemName: statusIcon)
                    .font(.caption.weight(.semibold))
                Text(statusLabel)
                    .font(.caption2.weight(.medium))
            }
            .foregroundStyle(statusColor)
        }
        .padding(12)
        .background(Color.osmSurface)
        .clipShape(RoundedRectangle(cornerRadius: 15, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 15, style: .continuous)
                .stroke(Color.osmOutlineVariant, lineWidth: 1)
        }
    }

    private var visual: PermissionVisual {
        switch item.kind {
        case .notifications:
            PermissionVisual("bell", AppStrings.Permissions.notificationsTitle, AppStrings.Permissions.notificationsDescription)
        case .camera:
            PermissionVisual("camera", AppStrings.Permissions.cameraTitle, AppStrings.Permissions.cameraDescription)
        case .microphone:
            PermissionVisual("mic", AppStrings.Permissions.microphoneTitle, AppStrings.Permissions.microphoneDescription)
        case .photoLibrary:
            PermissionVisual("photo.on.rectangle", AppStrings.Permissions.galleryTitle, AppStrings.Permissions.galleryDescription)
        case .backgroundTasks:
            PermissionVisual("arrow.triangle.2.circlepath.icloud", AppStrings.Permissions.backgroundTitle, AppStrings.Permissions.backgroundDescription)
        }
    }

    private var statusLabel: LocalizedStringResource {
        switch item.status {
        case .granted: AppStrings.Permissions.granted
        case .partial: AppStrings.Permissions.partial
        case .missing: AppStrings.Permissions.pending
        case .denied: AppStrings.Permissions.denied
        case .systemManaged: AppStrings.Permissions.ready
        }
    }

    private var statusIcon: String {
        switch item.status {
        case .granted, .systemManaged: "checkmark.circle.fill"
        case .partial: "exclamationmark.triangle.fill"
        case .missing: "shield"
        case .denied: "xmark.circle.fill"
        }
    }

    private var statusColor: Color {
        switch item.status {
        case .granted, .systemManaged: .osmPrimary
        case .partial: .osmWarning
        case .missing: .osmOnSurfaceVariant
        case .denied: .osmError
        }
    }
}

private struct PermissionVisual {
    let icon: String
    let title: LocalizedStringResource
    let description: LocalizedStringResource

    init(_ icon: String, _ title: LocalizedStringResource, _ description: LocalizedStringResource) {
        self.icon = icon
        self.title = title
        self.description = description
    }
}
