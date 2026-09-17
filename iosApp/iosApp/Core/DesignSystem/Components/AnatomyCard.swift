import SwiftUI

enum AnatomyCardVariant {
    case filled
    case outlined
    case elevated
}

struct AnatomyCard<Content: View>: View {
    var variant: AnatomyCardVariant = .outlined
    var isSelected = false
    var action: (() -> Void)?
    @ViewBuilder let content: Content

    var body: some View {
        Group {
            if let action {
                Button(action: action) {
                    cardContent
                }
                .buttonStyle(.plain)
            } else {
                cardContent
            }
        }
    }

    private var cardContent: some View {
        content
            .padding(OSMSpacing.md)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(backgroundColor)
            .overlay {
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .strokeBorder(borderColor, lineWidth: borderWidth)
            }
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            .shadow(
                color: .black.opacity(variant == .elevated ? 0.1 : 0),
                radius: variant == .elevated ? 8 : 0,
                y: variant == .elevated ? 3 : 0
            )
    }

    private var backgroundColor: Color {
        if isSelected {
            return .osmPrimaryContainer
        }
        return variant == .filled ? .osmSurfaceVariant : .osmSurface
    }

    private var borderColor: Color {
        if isSelected {
            return .osmPrimary
        }
        return variant == .outlined ? .osmOutlineVariant : .clear
    }

    private var borderWidth: CGFloat {
        isSelected || variant == .outlined ? 1 : 0
    }
}

private struct AnatomyCardPreview: View {
    var body: some View {
        AnatomyPreviewCanvas {
            VStack(spacing: OSMSpacing.md) {
                AnatomyCard(action: {}) {
                    VStack(alignment: .leading, spacing: OSMSpacing.xs) {
                        AnatomyText("Motor M-204", font: OSMTypography.headline)
                        AnatomyText(
                            "Empacadora · Línea 2",
                            font: OSMTypography.callout,
                            color: .osmOnSurfaceVariant
                        )
                    }
                }

                AnatomyCard(variant: .filled, isSelected: true) {
                    VStack(alignment: .leading, spacing: OSMSpacing.xs) {
                        AnatomyText("Tarjeta seleccionada", font: OSMTypography.headline)
                        AnatomyText("Contenido destacado sin perder contraste.")
                    }
                }
            }
        }
    }
}

#Preview("AnatomyCard · Light") {
    AnatomyCardPreview()
        .preferredColorScheme(.light)
}

#Preview("AnatomyCard · Dark") {
    AnatomyCardPreview()
        .preferredColorScheme(.dark)
}
