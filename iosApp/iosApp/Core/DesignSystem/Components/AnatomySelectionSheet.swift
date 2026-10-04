import SwiftUI

struct AnatomySelectionItem: Identifiable {
    let id: String
    let title: String
    var subtitle: String?
    var hasChildren = false
}

struct AnatomySelectionSheet: View {
    let title: LocalizedStringResource
    @Binding var query: String
    let items: [AnatomySelectionItem]
    var breadcrumbs: [AnatomySelectionItem] = []
    var onNavigate: ((String?) -> Void)?
    var onSelectCurrent: (() -> Void)?
    var currentSelectionTitle: LocalizedStringResource = AppStrings.CreateCard.selectLevel
    let onSelect: (String) -> Void
    let onDismiss: () -> Void

    var body: some View {
        NavigationStack {
            VStack(spacing: 8) {
                AnatomySearch(text: $query, placeholder: AppStrings.CreateCard.searchOptions)
                    .padding(.horizontal, 16)
                List {
                    if !breadcrumbs.isEmpty, let onNavigate {
                        Section {
                            Button { onNavigate(nil) } label: {
                                Label(AppStrings.CreateCard.levelRoot, systemImage: "house")
                            }
                            ForEach(breadcrumbs) { level in
                                Button(level.title) { onNavigate(level.id) }
                            }
                        }
                    }
                    Section {
                        ForEach(items) { item in
                            Button { onSelect(item.id) } label: {
                                HStack {
                                    VStack(alignment: .leading, spacing: 3) {
                                        AnatomyText(verbatim: item.title)
                                        if let subtitle = item.subtitle, !subtitle.isEmpty {
                                            AnatomyText(verbatim: subtitle, font: OSMTypography.caption,
                                                        color: .osmOnSurfaceVariant)
                                        }
                                    }
                                    Spacer()
                                    if item.hasChildren {
                                        Image(systemName: "chevron.right").foregroundStyle(Color.osmOnSurfaceVariant)
                                    }
                                }
                                .frame(minHeight: 44)
                            }
                        }
                    }
                }
                .scrollContentBackground(.hidden)
                .overlay {
                    if items.isEmpty { ContentUnavailableView.search(text: query) }
                }
                if let onSelectCurrent, !breadcrumbs.isEmpty, query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    AnatomyButton(title: currentSelectionTitle, action: onSelectCurrent)
                        .padding(16)
                }
            }
            .background(Color.osmBackground)
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(AppStrings.Common.cancel, action: onDismiss)
                }
            }
        }
    }
}

struct AnatomySelectionRow: View {
    let title: LocalizedStringResource
    let value: String?
    let placeholder: LocalizedStringResource
    let systemImage: String
    var supportingText: String?
    var enabled = true
    let action: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            AnatomyText(title, font: .subheadline.weight(.semibold))
            Button(action: action) {
                HStack(spacing: 12) {
                    Image(systemName: systemImage).foregroundStyle(Color.osmPrimary)
                    AnatomyText(verbatim: value ?? String(localized: placeholder),
                                color: value == nil ? .osmOnSurfaceVariant : .osmOnSurface)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Image(systemName: "chevron.right")
                        .font(.caption.weight(.semibold)).foregroundStyle(Color.osmOnSurfaceVariant)
                }
                .padding(14)
                .frame(minHeight: 60)
                .background(enabled ? Color.osmSurface : Color.osmSurfaceVariant)
                .clipShape(RoundedRectangle(cornerRadius: 14))
                .overlay { RoundedRectangle(cornerRadius: 14).stroke(Color.osmOutlineVariant, lineWidth: 1) }
            }
            .buttonStyle(.plain)
            .disabled(!enabled)
            if let supportingText, !supportingText.isEmpty {
                AnatomyText(verbatim: supportingText, font: OSMTypography.caption, color: .osmOnSurfaceVariant)
            }
        }
    }
}
