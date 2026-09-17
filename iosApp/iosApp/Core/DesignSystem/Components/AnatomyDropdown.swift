import SwiftUI

struct AnatomyDropdownOption: Identifiable, Hashable {
    let id: Int64
    let title: String
}

struct AnatomyDropdown: View {
    let options: [AnatomyDropdownOption]
    @Binding var selection: Int64?
    var systemImage = "building.2"

    var body: some View {
        Group {
            if options.count > 1 {
                Menu {
                    ForEach(options) { option in
                        Button {
                            selection = option.id
                        } label: {
                            Label(
                                option.title,
                                systemImage: option.id == selection ? "checkmark" : systemImage
                            )
                        }
                    }
                } label: {
                    content(showsChevron: true)
                }
            } else {
                content(showsChevron: false)
            }
        }
        .foregroundStyle(Color.osmOnSurface)
    }

    private func content(showsChevron: Bool) -> some View {
        HStack(spacing: OSMSpacing.sm) {
            Image(systemName: systemImage)
                .foregroundStyle(Color.osmOnSurfaceVariant)
            Text(selectedOption?.title ?? options.first?.title ?? "")
                .font(OSMTypography.bodyEmphasized)
                .lineLimit(1)
                .frame(maxWidth: .infinity, alignment: .leading)
            if showsChevron {
                Image(systemName: "chevron.down")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(Color.osmOnSurfaceVariant)
            }
        }
        .padding(.horizontal, 14)
        .frame(height: 44)
        .background(Color.osmSurface)
        .overlay {
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .strokeBorder(Color.osmOutlineVariant, lineWidth: 1)
        }
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .contentShape(Rectangle())
    }

    private var selectedOption: AnatomyDropdownOption? {
        options.first { $0.id == selection }
    }
}

private struct AnatomyDropdownPreview: View {
    @State private var selection: Int64? = 1

    var body: some View {
        AnatomyPreviewCanvas {
            AnatomyDropdown(
                options: [
                    .init(id: 1, title: "Planta Norte"),
                    .init(id: 2, title: "Planta Sur")
                ],
                selection: $selection
            )
        }
    }
}

#Preview("AnatomyDropdown · Light") {
    AnatomyDropdownPreview().preferredColorScheme(.light)
}

#Preview("AnatomyDropdown · Dark") {
    AnatomyDropdownPreview().preferredColorScheme(.dark)
}
