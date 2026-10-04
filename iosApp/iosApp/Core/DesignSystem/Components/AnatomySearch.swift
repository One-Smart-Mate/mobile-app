import SwiftUI

struct AnatomySearch: View {
    @Binding var text: String
    let placeholder: LocalizedStringResource
    var onSubmit: () -> Void = {}

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "magnifyingglass").foregroundStyle(Color.osmOnSurfaceVariant)
            TextField(String(localized: placeholder), text: $text)
                .textFieldStyle(.plain)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .submitLabel(.search)
                .onSubmit(onSubmit)
                .accessibilityLabel(Text(placeholder))
            if !text.isEmpty {
                Button { text = "" } label: {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundStyle(Color.osmOnSurfaceVariant)
                        .frame(width: 32, height: 32)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(AppStrings.Accessibility.dismiss)
            }
        }
        .padding(.horizontal, 14)
        .frame(minHeight: 50)
        .background(Color.osmSurfaceVariant)
        .clipShape(RoundedRectangle(cornerRadius: 25))
    }
}
