import SwiftUI

enum AnatomyButtonVariant {
    case primary
    case secondary
    case tertiary
    case destructive
}

enum AnatomyButtonSize {
    case small
    case medium
    case large

    var height: CGFloat {
        switch self {
        case .small: 40
        case .medium: 48
        case .large: 56
        }
    }
}

struct AnatomyButton: View {
    let title: LocalizedStringResource
    let action: () -> Void

    var variant: AnatomyButtonVariant = .primary
    var size: AnatomyButtonSize = .medium
    var isLoading = false
    var expandsHorizontally = true
    var leadingSystemImage: String?
    var trailingSystemImage: String?

    var body: some View {
        Button(action: action) {
            ZStack {
                if isLoading {
                    ProgressView()
                        .controlSize(.small)
                        .tint(variant == .primary || variant == .destructive ? .white : .osmPrimary)
                } else {
                    HStack(spacing: OSMSpacing.xs) {
                        if let leadingSystemImage {
                            Image(systemName: leadingSystemImage)
                        }

                        Text(title)
                            .font(OSMTypography.bodyEmphasized)

                        if let trailingSystemImage {
                            Image(systemName: trailingSystemImage)
                        }
                    }
                }
            }
            .frame(maxWidth: expandsHorizontally ? .infinity : nil)
            .frame(height: size.height)
            .contentShape(Rectangle())
        }
        .buttonStyle(AnatomyControlButtonStyle(variant: variant))
        .disabled(isLoading)
        .accessibilityValue(
            isLoading ? Text(AppStrings.Accessibility.loading) : Text("")
        )
    }
}

private struct AnatomyControlButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var isEnabled

    let variant: AnatomyButtonVariant

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .foregroundStyle(foregroundColor)
            .background(backgroundColor)
            .overlay {
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .strokeBorder(borderColor, lineWidth: borderWidth)
            }
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            .scaleEffect(configuration.isPressed ? 0.98 : 1)
            .opacity(configuration.isPressed ? 0.88 : 1)
            .animation(.easeOut(duration: 0.12), value: configuration.isPressed)
    }

    private var backgroundColor: Color {
        guard isEnabled else {
            return .osmOnSurface.opacity(0.12)
        }

        switch variant {
        case .primary: return Color.osmPrimary
        case .secondary: return Color.osmSurface
        case .tertiary: return Color.clear
        case .destructive: return Color.osmError
        }
    }

    private var foregroundColor: Color {
        guard isEnabled else {
            return .osmOnSurface.opacity(0.38)
        }

        switch variant {
        case .primary: return Color.white
        case .secondary, .tertiary: return Color.osmPrimary
        case .destructive: return Color.white
        }
    }

    private var borderColor: Color {
        switch variant {
        case .secondary: return Color.osmOutlineVariant
        case .tertiary: return Color.osmPrimary.opacity(0.45)
        case .primary, .destructive: return Color.clear
        }
    }

    private var borderWidth: CGFloat {
        switch variant {
        case .secondary, .tertiary: 1
        case .primary, .destructive: 0
        }
    }
}

private struct AnatomyButtonPreview: View {
    var body: some View {
        AnatomyPreviewCanvas {
            VStack(spacing: OSMSpacing.sm) {
                AnatomyButton(title: "Acción principal", action: {})
                AnatomyButton(
                    title: "Acción secundaria",
                    action: {},
                    variant: .secondary
                )
                AnatomyButton(
                    title: "Continuar",
                    action: {},
                    variant: .tertiary,
                    trailingSystemImage: "arrow.right"
                )
                AnatomyButton(
                    title: "Eliminar",
                    action: {},
                    variant: .destructive
                )
                AnatomyButton(title: "Cargando", action: {}, isLoading: true)
                AnatomyButton(title: "Deshabilitado", action: {})
                    .disabled(true)
            }
        }
    }
}

#Preview("AnatomyButton · Light") {
    AnatomyButtonPreview()
        .preferredColorScheme(.light)
}

#Preview("AnatomyButton · Dark") {
    AnatomyButtonPreview()
        .preferredColorScheme(.dark)
}
