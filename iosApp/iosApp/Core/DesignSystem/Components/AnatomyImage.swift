import SwiftUI

enum AnatomyImageSource {
    case asset(String)
    case system(String)
    case remote(URL?)
}

struct AnatomyImage: View {
    let source: AnatomyImageSource
    var accessibilityLabel: String?
    var contentMode: ContentMode = .fill
    var placeholderSystemImage = "photo"

    var body: some View {
        ZStack {
            Color.osmSurfaceVariant

            Image(systemName: placeholderSystemImage)
                .font(.system(size: 28, weight: .medium))
                .foregroundStyle(Color.osmOnSurfaceVariant.opacity(0.45))

            imageContent
        }
        .clipped()
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(accessibilityLabel ?? "")
        .accessibilityHidden(accessibilityLabel == nil)
    }

    @ViewBuilder
    private var imageContent: some View {
        switch source {
        case let .asset(name):
            configured(Image(name))

        case let .system(name):
            configured(Image(systemName: name))
                .padding(OSMSpacing.xl)

        case let .remote(url):
            AsyncImage(
                url: url,
                transaction: Transaction(animation: .easeInOut(duration: 0.2))
            ) { phase in
                switch phase {
                case let .success(image):
                    configured(image)
                        .transition(.opacity)
                case .empty:
                    ProgressView()
                        .tint(.osmPrimary)
                case .failure:
                    EmptyView()
                @unknown default:
                    EmptyView()
                }
            }
        }
    }

    private func configured(_ image: Image) -> some View {
        image
            .resizable()
            .aspectRatio(contentMode: contentMode)
    }
}

#Preview("AnatomyImage · Light") {
    AnatomyPreviewCanvas {
        AnatomyImage(
            source: .system("shippingbox.fill"),
            accessibilityLabel: "Equipo"
        )
        .frame(height: 180)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
    .preferredColorScheme(.light)
}

#Preview("AnatomyImage · Dark") {
    AnatomyPreviewCanvas {
        AnatomyImage(
            source: .system("shippingbox.fill"),
            accessibilityLabel: "Equipo"
        )
        .frame(height: 180)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
    .preferredColorScheme(.dark)
}
