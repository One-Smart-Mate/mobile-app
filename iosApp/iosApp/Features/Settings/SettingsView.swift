import SwiftUI

struct SettingsView: View {
    var body: some View {
        Text(AppStrings.Settings.title)
            .font(OSMTypography.title2)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color.osmBackground.ignoresSafeArea())
            .navigationTitle(AppStrings.Settings.title)
    }
}
