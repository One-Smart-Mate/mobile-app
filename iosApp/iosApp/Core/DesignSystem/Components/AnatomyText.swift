import SwiftUI

struct AnatomyText: View {
    private let content: Text
    private let font: Font
    private let color: Color
    private let alignment: TextAlignment
    private let lineLimit: Int?

    init(
        _ text: LocalizedStringResource,
        font: Font = OSMTypography.body,
        color: Color = .osmOnSurface,
        alignment: TextAlignment = .leading,
        lineLimit: Int? = nil
    ) {
        content = Text(text)
        self.font = font
        self.color = color
        self.alignment = alignment
        self.lineLimit = lineLimit
    }

    init(
        verbatim text: String,
        font: Font = OSMTypography.body,
        color: Color = .osmOnSurface,
        alignment: TextAlignment = .leading,
        lineLimit: Int? = nil
    ) {
        content = Text(verbatim: text)
        self.font = font
        self.color = color
        self.alignment = alignment
        self.lineLimit = lineLimit
    }

    var body: some View {
        content
            .font(font)
            .foregroundStyle(color)
            .multilineTextAlignment(alignment)
            .lineLimit(lineLimit)
    }
}

#Preview("AnatomyText · Light") {
    AnatomyPreviewCanvas {
        VStack(alignment: .leading, spacing: OSMSpacing.sm) {
            AnatomyText("Título principal", font: OSMTypography.title)
            AnatomyText("Texto de contenido claro y fácil de leer.")
            AnatomyText(
                "Información secundaria",
                font: OSMTypography.caption,
                color: .osmOnSurfaceVariant
            )
        }
    }
    .preferredColorScheme(.light)
}

#Preview("AnatomyText · Dark") {
    AnatomyPreviewCanvas {
        VStack(alignment: .leading, spacing: OSMSpacing.sm) {
            AnatomyText("Título principal", font: OSMTypography.title)
            AnatomyText("Texto de contenido claro y fácil de leer.")
            AnatomyText(
                "Información secundaria",
                font: OSMTypography.caption,
                color: .osmOnSurfaceVariant
            )
        }
    }
    .preferredColorScheme(.dark)
}
