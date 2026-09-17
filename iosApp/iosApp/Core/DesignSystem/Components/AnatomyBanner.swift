import SwiftUI
import UIKit

enum AnatomyBannerType {
    case error
    case warning
    case info
    case success

    var accessibilityName: LocalizedStringResource {
        switch self {
        case .error: AppStrings.Accessibility.bannerError
        case .warning: AppStrings.Accessibility.bannerWarning
        case .info: AppStrings.Accessibility.bannerInfo
        case .success: AppStrings.Accessibility.bannerSuccess
        }
    }
}

struct AnatomyBanner: View {
    let message: String
    let type: AnatomyBannerType

    var title: String?
    var actionTitle: LocalizedStringResource?
    var onAction: (() -> Void)?
    var onDismiss: (() -> Void)?

    var body: some View {
        HStack(spacing: OSMSpacing.sm) {
            icon

            VStack(alignment: .leading, spacing: 2) {
                if let title {
                    Text(verbatim: title)
                        .font(OSMTypography.headline)
                }
                Text(verbatim: message)
                    .font(OSMTypography.callout)
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            if let actionTitle, let onAction {
                Button(action: onAction) {
                    Text(actionTitle)
                        .font(.caption.weight(.semibold))
                        .lineLimit(1)
                }
                .buttonStyle(.plain)
                .foregroundStyle(palette.accent)
                .padding(.horizontal, OSMSpacing.xs)
                .frame(minHeight: 44)
            }

            if let onDismiss {
                Button(action: onDismiss) {
                    Image(systemName: "xmark")
                        .font(.caption.weight(.bold))
                        .frame(width: 44, height: 44)
                }
                .buttonStyle(.plain)
                .foregroundStyle(palette.content.opacity(0.72))
                .accessibilityLabel(Text(AppStrings.Accessibility.dismiss))
            }
        }
        .foregroundStyle(palette.content)
        .padding(.leading, OSMSpacing.md)
        .padding(.trailing, onDismiss == nil ? OSMSpacing.md : OSMSpacing.xxs)
        .padding(.vertical, OSMSpacing.sm)
        .frame(maxWidth: .infinity, minHeight: 64)
        .background(palette.container)
        .overlay(alignment: .leading) {
            Rectangle()
                .fill(palette.accent)
                .frame(width: 4)
        }
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .accessibilityElement(children: .contain)
        .onAppear {
            UIAccessibility.post(
                notification: .announcement,
                argument: "\(String(localized: type.accessibilityName)). \(title ?? "") \(message)"
            )
        }
    }

    private var icon: some View {
        Image(systemName: palette.icon)
            .font(.system(size: 18, weight: .semibold))
            .foregroundStyle(palette.accent)
            .frame(width: 36, height: 36)
            .background(palette.accent.opacity(0.14))
            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            .accessibilityHidden(true)
    }

    private var palette: BannerPalette {
        switch type {
        case .error:
            BannerPalette(
                container: .osmErrorContainer,
                content: .osmOnErrorContainer,
                accent: .osmError,
                icon: "exclamationmark.circle"
            )
        case .warning:
            BannerPalette(
                container: .osmWarningContainer,
                content: .osmOnWarningContainer,
                accent: .osmWarning,
                icon: "exclamationmark.triangle"
            )
        case .info:
            BannerPalette(
                container: .osmInfoContainer,
                content: .osmOnInfoContainer,
                accent: .osmInfo,
                icon: "info.circle"
            )
        case .success:
            BannerPalette(
                container: .osmPrimaryContainer,
                content: .osmOnPrimaryContainer,
                accent: .osmPrimary,
                icon: "checkmark.circle"
            )
        }
    }
}

private struct BannerPalette {
    let container: Color
    let content: Color
    let accent: Color
    let icon: String
}

private struct AnatomyBannerPreview: View {
    var body: some View {
        AnatomyPreviewCanvas {
            VStack(spacing: OSMSpacing.sm) {
                AnatomyBanner(
                    message: "Revisa tu conexión e inténtalo nuevamente.",
                    type: .error,
                    title: "No se guardaron los cambios",
                    actionTitle: "Reintentar",
                    onAction: {},
                    onDismiss: {}
                )
                AnatomyBanner(
                    message: "Revisa la información ingresada.",
                    type: .warning,
                    onDismiss: {}
                )
                AnatomyBanner(
                    message: "Nueva actualización disponible.",
                    type: .info,
                    onDismiss: {}
                )
                AnatomyBanner(
                    message: "Cambios guardados correctamente.",
                    type: .success
                )
            }
        }
    }
}

#Preview("AnatomyBanner · Light") {
    AnatomyBannerPreview()
        .preferredColorScheme(.light)
}

#Preview("AnatomyBanner · Dark") {
    AnatomyBannerPreview()
        .preferredColorScheme(.dark)
}
