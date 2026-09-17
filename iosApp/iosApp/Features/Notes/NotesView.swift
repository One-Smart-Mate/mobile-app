import SwiftUI

struct NotesView: View {
    var body: some View {
        Text(AppStrings.Notes.title)
            .font(OSMTypography.title2)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color.osmBackground.ignoresSafeArea())
            .navigationTitle(AppStrings.Notes.title)
    }
}
