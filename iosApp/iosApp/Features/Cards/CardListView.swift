import SwiftUI
import SharedLogic

struct CardListView: View {
    let user: SessionUser
    @Bindable var viewModel: CardListViewModel
    let onCreateCard: (() -> Void)?
    let onOpenCard: ((String) -> Void)?
    let onApplyProvisionalSolution: ((String) -> Void)?
    let onApplyDefinitiveSolution: ((String) -> Void)?

    @State private var showsAdvancedFilters = false
    @State private var actionCard: Card?

    var body: some View {
        List {
            Section {
                filterBar
                    .listRowInsets(.init(top: 8, leading: 16, bottom: 8, trailing: 16))
                    .listRowSeparator(.hidden)
                    .listRowBackground(Color.osmBackground)

                if let errorMessage = viewModel.errorMessage {
                    AnatomyBanner(
                        message: errorMessage,
                        type: .error,
                        actionTitle: AppStrings.Cards.retry,
                        onAction: viewModel.requestRefresh,
                        onDismiss: viewModel.dismissError
                    )
                    .listRowInsets(.init(top: 4, leading: 16, bottom: 8, trailing: 16))
                    .listRowSeparator(.hidden)
                    .listRowBackground(Color.osmBackground)
                }
            } header: {
                cardCountHeader
            }

            if viewModel.isInitialLoading && viewModel.cards.isEmpty {
                Section {
                    HStack {
                        Spacer()
                        ProgressView()
                            .tint(Color.osmPrimary)
                            .accessibilityLabel(AppStrings.Accessibility.loading)
                        Spacer()
                    }
                    .frame(minHeight: 220)
                    .listRowSeparator(.hidden)
                    .listRowBackground(Color.osmBackground)
                }
            } else if viewModel.cards.isEmpty {
                Section {
                    ContentUnavailableView(
                        viewModel.query.isEmpty
                            ? AppStrings.Cards.emptyFilter
                            : AppStrings.Cards.emptySearch,
                        systemImage: "list.clipboard",
                        description: Text(AppStrings.Cards.emptySupport)
                    )
                    .frame(maxWidth: .infinity, minHeight: 280)
                    .listRowSeparator(.hidden)
                    .listRowBackground(Color.osmBackground)
                }
            } else {
                Section {
                    ForEach(viewModel.cards, id: \.uuid) { card in
                        CardListRow(
                            card: card,
                            siteName: viewModel.siteNames[card.siteId],
                            onOpen: onOpenCard.map { handler in { handler(card.uuid) } },
                            onActions: hasSolutionNavigation(for: card) ? { actionCard = card } : nil
                        )
                        .listRowInsets(.init(top: 6, leading: 16, bottom: 6, trailing: 16))
                        .listRowSeparator(.hidden)
                        .listRowBackground(Color.osmBackground)
                    }
                }
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .background(Color.osmBackground)
        .navigationTitle(AppStrings.Cards.title)
        .navigationBarTitleDisplayMode(.large)
        .searchable(
            text: $viewModel.query,
            placement: .navigationBarDrawer(displayMode: .always),
            prompt: AppStrings.Cards.searchPrompt
        )
        .refreshable { await viewModel.refresh() }
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                if viewModel.isRefreshing {
                    ProgressView()
                        .controlSize(.small)
                        .tint(Color.osmPrimary)
                }
                Button(action: viewModel.requestRefresh) {
                    Image(systemName: "arrow.clockwise")
                }
                .disabled(viewModel.isRefreshing)
                .accessibilityLabel(AppStrings.Cards.refresh)

                if let onCreateCard {
                    Button(action: onCreateCard) {
                        Image(systemName: "plus")
                    }
                    .accessibilityLabel(AppStrings.Cards.create)
                }
            }
        }
        .sheet(isPresented: $showsAdvancedFilters) {
            AdvancedCardFiltersSheet(
                selection: viewModel.advancedFilter,
                onSelect: { filter in
                    viewModel.selectAdvanced(filter)
                    showsAdvancedFilters = false
                },
                onClear: {
                    viewModel.clearFilters()
                    showsAdvancedFilters = false
                }
            )
            .presentationDetents([.medium, .large])
            .presentationDragIndicator(.visible)
        }
        .confirmationDialog(
            AppStrings.Cards.actionsTitle,
            isPresented: Binding(
                get: { actionCard != nil },
                set: { if !$0 { actionCard = nil } }
            ),
            titleVisibility: .visible,
            presenting: actionCard
        ) { card in
            if card.allowsProvisionalSolution, let onApplyProvisionalSolution {
                Button(AppStrings.Cards.provisionalSolution) {
                    actionCard = nil
                    onApplyProvisionalSolution(card.uuid)
                }
            }
            if card.allowsDefinitiveSolution, let onApplyDefinitiveSolution {
                Button(AppStrings.Cards.definitiveSolution) {
                    actionCard = nil
                    onApplyDefinitiveSolution(card.uuid)
                }
            }
            Button(AppStrings.Common.cancel, role: .cancel) { actionCard = nil }
        } message: { _ in
            Text(AppStrings.Cards.actionsSubtitle)
        }
        .task { viewModel.start(user: user) }
    }

    private func hasSolutionNavigation(for card: Card) -> Bool {
        (card.allowsProvisionalSolution && onApplyProvisionalSolution != nil)
            || (card.allowsDefinitiveSolution && onApplyDefinitiveSolution != nil)
    }

    private var cardCountHeader: some View {
        HStack {
            Text(
                String(
                    format: String(localized: AppStrings.Cards.count),
                    locale: .current,
                    Int64(viewModel.count)
                )
            )
            .font(.subheadline)
            .foregroundStyle(Color.osmOnSurfaceVariant)
            Spacer()
        }
        .textCase(nil)
    }

    private var filterBar: some View {
        HStack(spacing: 8) {
            CardPrimaryFilterButton(
                title: viewModel.isUsingAdvancedFilter ? AppStrings.Cards.custom : AppStrings.Cards.active,
                isSelected: viewModel.primaryFilter == .active,
                action: { viewModel.selectPrimary(.active) }
            )
            CardPrimaryFilterButton(
                title: AppStrings.Cards.assigned,
                isSelected: viewModel.primaryFilter == .assigned && !viewModel.isUsingAdvancedFilter,
                action: { viewModel.selectPrimary(.assigned) }
            )
            CardPrimaryFilterButton(
                title: AppStrings.Cards.overdue,
                isSelected: viewModel.primaryFilter == .overdue && !viewModel.isUsingAdvancedFilter,
                action: { viewModel.selectPrimary(.overdue) }
            )

            Button {
                showsAdvancedFilters = true
            } label: {
                Image(systemName: viewModel.isUsingAdvancedFilter
                    ? "line.3.horizontal.decrease.circle.fill"
                    : "line.3.horizontal.decrease.circle")
                    .font(.title2)
                    .symbolRenderingMode(.hierarchical)
            }
            .buttonStyle(.plain)
            .foregroundStyle(Color.osmPrimary)
            .accessibilityLabel(AppStrings.Cards.filters)
        }
    }
}

private struct CardPrimaryFilterButton: View {
    let title: LocalizedStringResource
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.caption.weight(.semibold))
                .lineLimit(1)
                .minimumScaleFactor(0.8)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 8)
                .foregroundStyle(isSelected ? Color.osmOnPrimary : Color.osmOnSurfaceVariant)
                .background(isSelected ? Color.osmPrimary : Color.osmSurface)
                .clipShape(Capsule())
                .overlay {
                    Capsule()
                        .stroke(isSelected ? Color.osmPrimary : Color.osmOutlineVariant, lineWidth: 1)
                }
        }
        .buttonStyle(.plain)
    }
}

private struct CardListRow: View {
    let card: Card
    let siteName: String?
    let onOpen: (() -> Void)?
    let onActions: (() -> Void)?

    var body: some View {
        Button(action: onOpen ?? {}) {
            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 8) {
                    Text(folioAndSite)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.osmOnSurfaceVariant)
                        .lineLimit(1)
                    Spacer(minLength: 4)
                    CardStatusBadge(card: card)
                    if let onActions {
                        Button(action: onActions) {
                            Image(systemName: "ellipsis.circle")
                                .font(.title3)
                                .foregroundStyle(Color.osmOnSurfaceVariant)
                        }
                        .buttonStyle(.borderless)
                        .accessibilityLabel(AppStrings.Cards.actionsTitle)
                    }
                }

                HStack(spacing: 7) {
                    Circle()
                        .fill(Color(cardHex: card.cardTypeColor) ?? Color.osmPrimary)
                        .frame(width: 8, height: 8)
                    Text(card.cardTypeName?.nonEmpty ?? String(localized: AppStrings.Cards.unknownType))
                        .font(.caption)
                        .foregroundStyle(Color.osmOnSurfaceVariant)
                }

                Text(card.problemDescription.nonEmpty ?? String(localized: AppStrings.Cards.noDescription))
                    .font(.headline)
                    .foregroundStyle(Color.osmOnSurface)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)

                if let location = card.location?.nonEmpty {
                    Label(location, systemImage: "mappin.and.ellipse")
                        .font(.subheadline)
                        .foregroundStyle(Color.osmOnSurfaceVariant)
                        .lineLimit(1)
                }

                HStack(spacing: 8) {
                    if let priority = card.priorityLabel {
                        Text(priority)
                            .font(.caption2.weight(.semibold))
                            .foregroundStyle(card.isCriticalPriority ? Color.osmError : Color.osmWarning)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 5)
                            .background(card.isCriticalPriority ? Color.osmErrorContainer : Color.osmWarningContainer)
                            .clipShape(RoundedRectangle(cornerRadius: 7, style: .continuous))
                    }
                    Text(card.creationDate.shortCardDate)
                        .font(.caption)
                        .foregroundStyle(Color.osmOnSurfaceVariant)
                    Spacer(minLength: 4)
                    if let dueDate = card.dueDate?.shortCardDate.nonEmpty {
                        Text(card.isClosed ? dueDate : dueDate)
                            .font(.caption.weight(card.isOverdue ? .semibold : .regular))
                            .foregroundStyle(card.isOverdue ? Color.osmError : Color.osmOnSurfaceVariant)
                    }
                }

                if card.isLocal || card.hasLocalSolutions {
                    HStack(spacing: 6) {
                        if card.isLocal {
                            LocalStateBadge(title: AppStrings.Cards.pendingSync, systemImage: "arrow.triangle.2.circlepath")
                        }
                        if card.hasLocalSolutions {
                            LocalStateBadge(title: AppStrings.Cards.localSolution, systemImage: "wrench.and.screwdriver")
                        }
                    }
                }

                HStack(spacing: 8) {
                    Image(systemName: "person.crop.circle")
                        .foregroundStyle(Color.osmOnSurfaceVariant)
                    Text(
                        card.mechanicName?.nonEmpty
                            ?? card.responsibleName?.nonEmpty
                            ?? String(localized: AppStrings.Cards.unassigned)
                    )
                    .font(.caption)
                    .foregroundStyle(Color.osmOnSurface)
                    .lineLimit(1)
                    Spacer()
                    EvidenceCounter(systemImage: "camera", value: card.evidenceImageCreation)
                    EvidenceCounter(systemImage: "mic", value: card.evidenceAudioCreation)
                    EvidenceCounter(systemImage: "video", value: card.evidenceVideoCreation)
                }
            }
            .padding(16)
            .background(Color.osmSurface)
            .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .stroke(Color.osmOutlineVariant.opacity(0.7), lineWidth: 0.5)
            }
        }
        .buttonStyle(.plain)
        .disabled(onOpen == nil)
        .accessibilityElement(children: .combine)
    }

    private var folioAndSite: String {
        let folio = card.siteCardId > 0
            ? "#\(card.siteCardId)"
            : String(localized: AppStrings.Cards.localFolio)
        let site = siteName?.nonEmpty ?? card.siteCode?.nonEmpty
        return [folio, site].compactMap { $0 }.joined(separator: " · ")
    }
}

private struct CardStatusBadge: View {
    let card: Card

    var body: some View {
        Text(title)
            .font(.caption2.weight(.bold))
            .foregroundStyle(foreground)
            .padding(.horizontal, 9)
            .padding(.vertical, 5)
            .background(background)
            .clipShape(Capsule())
    }

    private var title: LocalizedStringResource {
        if card.isOverdue { return AppStrings.Cards.statusOverdue }
        if card.isClosed { return AppStrings.Cards.statusClosed }
        return AppStrings.Cards.statusOpen
    }

    private var foreground: Color {
        card.isOverdue ? .osmError : (card.isClosed ? .osmOnSurfaceVariant : .osmPrimary)
    }

    private var background: Color {
        card.isOverdue ? .osmErrorContainer : (card.isClosed ? .osmSurfaceVariant : .osmPrimaryContainer)
    }
}

private struct EvidenceCounter: View {
    let systemImage: String
    let value: Int64

    var body: some View {
        HStack(spacing: 3) {
            Image(systemName: systemImage)
            Text("\(value)")
        }
        .font(.caption2)
        .foregroundStyle(Color.osmOnSurfaceVariant)
    }
}

private struct LocalStateBadge: View {
    let title: LocalizedStringResource
    let systemImage: String

    var body: some View {
        Label(title, systemImage: systemImage)
            .font(.caption2.weight(.semibold))
            .foregroundStyle(Color.osmWarning)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .overlay {
                RoundedRectangle(cornerRadius: 7, style: .continuous)
                    .stroke(Color.osmWarning, lineWidth: 1)
            }
    }
}

private struct AdvancedCardFiltersSheet: View {
    let selection: CardListAdvancedFilter?
    let onSelect: (CardListAdvancedFilter) -> Void
    let onClear: () -> Void

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section {
                    ForEach(CardListAdvancedFilter.allCases) { filter in
                        Button {
                            onSelect(filter)
                        } label: {
                            HStack {
                                Label(filter.title, systemImage: filter.systemImage)
                                    .foregroundStyle(Color.osmOnSurface)
                                Spacer()
                                if selection == filter {
                                    Image(systemName: "checkmark")
                                        .fontWeight(.semibold)
                                        .foregroundStyle(Color.osmPrimary)
                                }
                            }
                        }
                    }
                } footer: {
                    Text(AppStrings.Cards.filtersSubtitle)
                }

                Section {
                    Button(role: .destructive, action: onClear) {
                        Label(AppStrings.Cards.clearFilters, systemImage: "arrow.counterclockwise")
                    }
                }
            }
            .navigationTitle(AppStrings.Cards.filters)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(AppStrings.Common.done) { dismiss() }
                }
            }
        }
    }
}

private extension CardListAdvancedFilter {
    var title: LocalizedStringResource {
        switch self {
        case .allOpen: AppStrings.Cards.allOpen
        case .myOpen: AppStrings.Cards.myOpen
        case .myAssigned: AppStrings.Cards.myAssigned
        case .unassigned: AppStrings.Cards.unassignedCards
        case .due: AppStrings.Cards.dueCards
        case .closed: AppStrings.Cards.closedCards
        }
    }

    var systemImage: String {
        switch self {
        case .allOpen: "tray.full"
        case .myOpen: "person.text.rectangle"
        case .myAssigned: "person.badge.clock"
        case .unassigned: "person.crop.circle.badge.questionmark"
        case .due: "calendar.badge.exclamationmark"
        case .closed: "checkmark.circle"
        }
    }
}

private extension Card {
    var isOverdue: Bool {
        guard isOpen, let due = dueDate?.prefix(10), due.count == 10 else { return false }
        let today = Date.now.formatted(.iso8601.year().month().day().dateSeparator(.dash))
        return String(due) < today
    }

    var priorityLabel: String? {
        let parts = [priorityCode?.nonEmpty, priorityDescription?.nonEmpty].compactMap { $0 }
        return parts.isEmpty ? nil : parts.joined(separator: " · ")
    }

    var isCriticalPriority: Bool {
        priorityCode?.localizedCaseInsensitiveCompare("P1") == .orderedSame
            || priorityDescription?.localizedCaseInsensitiveContains("alta") == true
            || priorityDescription?.localizedCaseInsensitiveContains("crítica") == true
    }

    var allowsProvisionalSolution: Bool {
        isOpen && !provisionalSolutionPending
            && provisionalSolutionDate?.nonEmpty == nil
            && provisionalSolutionUserName?.nonEmpty == nil
    }

    var allowsDefinitiveSolution: Bool {
        isOpen && !definitiveSolutionPending && definitiveSolutionDate?.nonEmpty == nil
    }
}

private extension Optional where Wrapped == String {
    var shortCardDate: String { self?.shortCardDate ?? "" }
}

private extension String {
    var nonEmpty: String? { isEmpty ? nil : self }

    var shortCardDate: String {
        let prefix = String(self.prefix(10))
        guard let date = try? Date(prefix, strategy: .iso8601.year().month().day().dateSeparator(.dash)) else {
            return prefix
        }
        return date.formatted(.dateTime.day().month(.abbreviated))
    }
}

private extension Color {
    init?(cardHex: String?) {
        guard var value = cardHex?.trimmingCharacters(in: .whitespacesAndNewlines), !value.isEmpty else {
            return nil
        }
        value.removeAll(where: { $0 == "#" })
        guard value.count == 6, let rgb = UInt32(value, radix: 16) else { return nil }
        self.init(
            red: Double((rgb >> 16) & 0xFF) / 255,
            green: Double((rgb >> 8) & 0xFF) / 255,
            blue: Double(rgb & 0xFF) / 255
        )
    }
}
