import SwiftUI

struct AnatomyPreviewCanvas<Content: View>: View {
    private let content: Content

    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    var body: some View {
        OneSmartMateTheme {
            ScrollView {
                content
                    .padding(OSMSpacing.md)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .background(Color.osmBackground.ignoresSafeArea())
        }
    }
}
