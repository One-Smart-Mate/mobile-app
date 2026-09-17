import SwiftUI

enum OSMSpacing {
    static let xxs: CGFloat = 4
    static let xs: CGFloat = 8
    static let sm: CGFloat = 12
    static let md: CGFloat = 16
    static let lg: CGFloat = 24
    static let xl: CGFloat = 32
    static let xxl: CGFloat = 40
    static let xxxl: CGFloat = 48
}

enum OSMTypography {
    static let display = Font.system(.largeTitle, design: .rounded, weight: .bold)
    static let title = Font.system(.title, design: .rounded, weight: .bold)
    static let title2 = Font.system(.title2, design: .rounded, weight: .bold)
    static let headline = Font.system(.headline, design: .rounded, weight: .semibold)
    static let body = Font.system(.body, design: .default, weight: .regular)
    static let bodyEmphasized = Font.system(.body, design: .default, weight: .semibold)
    static let callout = Font.system(.callout, design: .default, weight: .regular)
    static let caption = Font.system(.caption, design: .default, weight: .regular)
}

extension Color {
    static let osmPrimary = adaptive(light: 0x3B8D2F, dark: 0x82D477)
    static let osmOnPrimary = adaptive(light: 0xFFFFFF, dark: 0x0C3A10)
    static let osmPrimaryContainer = adaptive(light: 0xDDF4D8, dark: 0x245D28)
    static let osmOnPrimaryContainer = adaptive(light: 0x123A15, dark: 0xDDF4D8)

    static let osmBackground = adaptive(light: 0xF8FAF7, dark: 0x101410)
    static let osmOnBackground = adaptive(light: 0x191C18, dark: 0xE1E4DE)
    static let osmSurface = adaptive(light: 0xFFFFFF, dark: 0x181C18)
    static let osmOnSurface = adaptive(light: 0x191C18, dark: 0xE1E4DE)
    static let osmSurfaceVariant = adaptive(light: 0xF0F2EE, dark: 0x232823)
    static let osmOnSurfaceVariant = adaptive(light: 0x444842, dark: 0xC4C8C0)
    static let osmOutline = adaptive(light: 0x747970, dark: 0x8E938A)
    static let osmOutlineVariant = adaptive(light: 0xE1E4DE, dark: 0x414740)

    static let osmError = adaptive(light: 0xBA1A1A, dark: 0xFFB4AB)
    static let osmErrorContainer = adaptive(light: 0xFFFFDAD6, dark: 0x4D2020)
    static let osmOnErrorContainer = adaptive(light: 0x410002, dark: 0xFFDAD6)
    static let osmWarning = adaptive(light: 0xB87500, dark: 0xFFC857)
    static let osmWarningContainer = adaptive(light: 0xFFF3D6, dark: 0x3A301A)
    static let osmOnWarningContainer = adaptive(light: 0x574100, dark: 0xFFE2A3)
    static let osmInfo = adaptive(light: 0x2F6FD6, dark: 0x8AB4F8)
    static let osmInfoContainer = adaptive(light: 0xE8F1FF, dark: 0x1D3049)
    static let osmOnInfoContainer = adaptive(light: 0x173E73, dark: 0xD7E7FF)

    private static func adaptive(light: UInt32, dark: UInt32) -> Color {
        Color(
            uiColor: UIColor { traits in
                UIColor(rgb: traits.userInterfaceStyle == .dark ? dark : light)
            }
        )
    }
}

private extension UIColor {
    convenience init(rgb: UInt32) {
        self.init(
            red: CGFloat((rgb >> 16) & 0xFF) / 255,
            green: CGFloat((rgb >> 8) & 0xFF) / 255,
            blue: CGFloat(rgb & 0xFF) / 255,
            alpha: 1
        )
    }
}

struct OneSmartMateTheme<Content: View>: View {
    private let content: Content

    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    var body: some View {
        content
            .tint(.osmPrimary)
            .foregroundStyle(Color.osmOnBackground)
    }
}
