import SharedLogic
import SwiftUI

struct OplScreen: View {
    @Bindable var viewModel: OplViewModel
    let onOpen: (Int64) -> Void

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 16) {
                VStack(alignment: .leading, spacing: 4) {
                    AnatomyText(OplStrings.subtitle, font: OSMTypography.title2)
                    Label(viewModel.site.name, systemImage: "building.2")
                        .font(OSMTypography.callout).foregroundStyle(Color.osmOnSurfaceVariant)
                }
                AnatomySearch(text: Binding(get: { viewModel.query }, set: viewModel.setQuery),
                              placeholder: OplStrings.searchPlaceholder, onSubmit: viewModel.search)
                    .disabled(viewModel.isBusy || viewModel.selectedLevel != nil)
                AnatomySelectionRow(
                    title: AppStrings.CreateCard.levelLabel,
                    value: viewModel.selectedPath.isEmpty ? nil : viewModel.selectedLocation,
                    placeholder: AppStrings.CreateCard.levelPlaceholder,
                    systemImage: "mappin.and.ellipse",
                    enabled: !viewModel.isBusy && viewModel.query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                    action: viewModel.openLevels
                )
                if viewModel.selectedLevel != nil {
                    AnatomyButton(title: OplStrings.clearLevel, action: viewModel.clearLevel,
                                  variant: .tertiary, size: .small)
                        .disabled(viewModel.isBusy)
                }
                AnatomyText(OplStrings.exclusiveFilters, font: OSMTypography.caption, color: .osmOnSurfaceVariant)
                AnatomyButton(title: OplStrings.search, action: viewModel.search, leadingSystemImage: "magnifyingglass")
                    .disabled(!viewModel.canSearch)
                if viewModel.isBusy {
                    ProgressView().tint(.osmPrimary).frame(maxWidth: .infinity)
                    AnatomyText(viewModel.isSearching ? OplStrings.searching : OplStrings.loadingLevels,
                                font: OSMTypography.caption, color: .osmOnSurfaceVariant)
                }
                if let error = viewModel.error {
                    AnatomyBanner(message: error, type: .error, onDismiss: { viewModel.error = nil })
                }
                if viewModel.hasSearched, !viewModel.isSearching {
                    AnatomyText(viewModel.showingDownloads
                        ? OplStrings.downloadedList
                        : OplStrings.resultCount(viewModel.opls.count),
                        font: OSMTypography.headline)
                    if viewModel.opls.isEmpty {
                        ContentUnavailableView(OplStrings.noResults, systemImage: "doc.text.magnifyingglass",
                                               description: Text(OplStrings.noResultsHelp))
                    }
                    ForEach(viewModel.opls, id: \.id) { opl in
                        OplCard(opl: opl, isDownloaded: viewModel.downloadedIDs.contains(opl.id)) { onOpen(opl.id) }
                    }
                }
            }
            .padding(20)
            .frame(maxWidth: 720)
            .frame(maxWidth: .infinity)
        }
        .background(Color.osmBackground.ignoresSafeArea())
        .navigationTitle(OplStrings.title)
        .navigationBarTitleDisplayMode(.large)
        .toolbar(.hidden, for: .tabBar)
        .scrollDismissesKeyboard(.interactively)
        .task { await viewModel.start() }
        .onDisappear { viewModel.stop() }
        .sheet(isPresented: $viewModel.showsLevelSheet) {
            AnatomySelectionSheet(
                title: AppStrings.CreateCard.selectLevel, query: $viewModel.levelQuery,
                items: viewModel.choices,
                breadcrumbs: viewModel.navigationPath.map { AnatomySelectionItem(id: $0.id, title: $0.name) },
                onNavigate: viewModel.navigate,
                onSelectCurrent: viewModel.selectCurrent,
                currentSelectionTitle: OplStrings.selectCurrent,
                onSelect: { viewModel.select($0) },
                onDismiss: { viewModel.showsLevelSheet = false }
            )
            .presentationDetents([.large])
            .presentationDragIndicator(.visible)
        }
    }
}
