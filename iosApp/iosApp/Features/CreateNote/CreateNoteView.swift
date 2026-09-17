import SwiftUI

struct CreateNoteView: View {
    var body: some View {
        Text(AppStrings.CreateNote.title)
            .font(OSMTypography.title2)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color.osmBackground.ignoresSafeArea())
            .navigationTitle(AppStrings.CreateNote.title)
            .navigationBarTitleDisplayMode(.inline)
    }
}
