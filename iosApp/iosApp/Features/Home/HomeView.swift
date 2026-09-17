import SwiftUI

struct HomeView: View {
    let user: SessionUser

    var body: some View {
        VStack(spacing: OSMSpacing.sm) {
            AnatomyText(
                AppStrings.Home.title,
                font: OSMTypography.title,
                alignment: .center
            )
            Text(
                String(
                    format: String(localized: AppStrings.Home.welcome),
                    locale: .current,
                    user.name
                )
            )
            .font(OSMTypography.body)
            .foregroundStyle(Color.osmOnSurfaceVariant)
            .multilineTextAlignment(.center)
        }
        .padding(OSMSpacing.lg)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.osmBackground.ignoresSafeArea())
        .navigationTitle(AppStrings.Home.title)
    }
}

#Preview("Home · Light") {
    OneSmartMateTheme {
        NavigationStack {
            HomeView(
                user: SessionUser(
                    id: 1,
                    name: "Diego",
                    email: "diego@empresa.com",
                    companyName: "Empresa"
                )
            )
        }
    }
    .preferredColorScheme(.light)
}
