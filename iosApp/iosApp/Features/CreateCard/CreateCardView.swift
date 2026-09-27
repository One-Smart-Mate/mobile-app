import PhotosUI
import SharedLogic
import SwiftUI
import UniformTypeIdentifiers

struct CreateCardView: View {
    @Bindable var viewModel: CreateCardViewModel
    let siteID: Int64?
    let onClose: () -> Void

    var body: some View {
        Group {
            if viewModel.isLoading || viewModel.state == nil {
                ProgressView()
                    .tint(Color.osmPrimary)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if let state = viewModel.state {
                content(state)
            }
        }
        .background(Color.osmBackground.ignoresSafeArea())
        .navigationTitle(AppStrings.CreateCard.title)
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(true)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button(action: viewModel.back) {
                    Image(systemName: "chevron.left")
                }
                .disabled(viewModel.state?.isSaving == true)
                .accessibilityLabel(AppStrings.CreateCard.back)
            }
        }
        .task { await viewModel.start(siteID: siteID) }
        .onChange(of: viewModel.shouldClose) { _, close in
            if close { onClose() }
        }
    }

    private func content(_ state: CreateCardState) -> some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 18) {
                CreateCardStepIndicator(current: state.step)

                if let error = state.validationError {
                    AnatomyBanner(
                        message: validationMessage(error),
                        type: .error,
                        onDismiss: viewModel.dismissError
                    )
                }
                if let error = state.evidenceError {
                    AnatomyBanner(
                        message: evidenceMessage(error),
                        type: .error,
                        onDismiss: viewModel.dismissError
                    )
                }

                switch state.step {
                case .classification: classification(state)
                case .location: location(state)
                case .evidence: evidence(state)
                case .details: details(state)
                case .review: review(state)
                default: EmptyView()
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 8)
            .padding(.bottom, 120)
            .frame(maxWidth: 700)
            .frame(maxWidth: .infinity)
        }
        .safeAreaInset(edge: .bottom) {
            bottomActions(state)
        }
        .sheet(
            isPresented: Binding(
                get: { state.activeSheet != nil },
                set: { if !$0 { viewModel.dismissSheet() } }
            )
        ) {
            CreateCardSelectionSheet(state: state, viewModel: viewModel)
                .presentationDetents([.large])
                .presentationDragIndicator(.visible)
        }
    }

    @ViewBuilder
    private func classification(_ state: CreateCardState) -> some View {
        CreateCardSectionHeader(
            title: AppStrings.CreateCard.classificationTitle,
            subtitle: AppStrings.CreateCard.classificationSubtitle
        )
        CreateCardSelectionRow(
            title: AppStrings.CreateCard.typeLabel,
            value: state.selectedCardType?.name,
            placeholder: AppStrings.CreateCard.typePlaceholder,
            systemImage: "square.grid.2x2",
            action: { viewModel.open(.cardType) }
        )
        if state.requiresCardTypeValue {
            CreateCardSelectionRow(
                title: AppStrings.CreateCard.conditionLabel,
                value: conditionName(state.selectedCardTypeValue),
                placeholder: AppStrings.CreateCard.conditionPlaceholder,
                systemImage: "shield",
                action: { viewModel.open(.cardTypeValue) }
            )
        }
        CreateCardSelectionRow(
            title: AppStrings.CreateCard.preclassifierLabel,
            value: state.selectedPreclassifier.map { "\($0.code) · \($0.description_)" },
            placeholder: AppStrings.CreateCard.preclassifierPlaceholder,
            systemImage: "folder",
            enabled: state.selectedCardType != nil,
            action: { viewModel.open(.preclassifier) }
        )
        CreateCardSelectionRow(
            title: AppStrings.CreateCard.priorityLabel,
            value: state.selectedPriority.map { "\($0.code) · \($0.description_)" },
            placeholder: AppStrings.CreateCard.priorityPlaceholder,
            systemImage: "flag",
            enabled: state.selectedPreclassifier != nil,
            action: { viewModel.open(.priority) }
        )
        if state.requiresCustomDueDate {
            CreateCardSelectionRow(
                title: AppStrings.CreateCard.dueDateLabel,
                value: state.customDueDate,
                placeholder: AppStrings.CreateCard.dueDatePlaceholder,
                systemImage: "calendar",
                action: { viewModel.open(.customDueDate) }
            )
        }
    }

    @ViewBuilder
    private func location(_ state: CreateCardState) -> some View {
        CreateCardSectionHeader(
            title: AppStrings.CreateCard.locationTitle,
            subtitle: AppStrings.CreateCard.locationSubtitle
        )
        CreateCardSelectionRow(
            title: AppStrings.CreateCard.levelLabel,
            value: state.selectedLocation.isEmpty ? nil : state.selectedLocation,
            placeholder: AppStrings.CreateCard.levelPlaceholder,
            systemImage: "mappin.and.ellipse",
            supportingText: state.responsibleName,
            action: { viewModel.open(.level) }
        )
        CreateCardInfoBox(text: AppStrings.CreateCard.levelHelper)
    }

    @ViewBuilder
    private func evidence(_ state: CreateCardState) -> some View {
        CreateCardSectionHeader(
            title: AppStrings.CreateCard.evidenceTitle,
            subtitle: AppStrings.CreateCard.evidenceSubtitle
        )
        CardEvidenceCaptureView(state: state, viewModel: viewModel)
    }

    @ViewBuilder
    private func details(_ state: CreateCardState) -> some View {
        CreateCardSectionHeader(
            title: AppStrings.CreateCard.detailsTitle,
            subtitle: AppStrings.CreateCard.detailsSubtitle
        )
        VStack(alignment: .trailing, spacing: 6) {
            TextEditor(
                text: Binding(
                    get: { state.description_ },
                    set: viewModel.setDescription
                )
            )
            .font(.body)
            .scrollContentBackground(.hidden)
            .padding(12)
            .frame(minHeight: 150, maxHeight: 190)
            .background(Color.osmSurface)
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .stroke(Color.osmOutlineVariant, lineWidth: 1)
            }
            .overlay(alignment: .topLeading) {
                if state.description_.isEmpty {
                    Text(AppStrings.CreateCard.descriptionPlaceholder)
                        .foregroundStyle(Color.osmOnSurfaceVariant)
                        .padding(.horizontal, 17)
                        .padding(.vertical, 20)
                        .allowsHitTesting(false)
                }
            }
            Text("\(state.description_.count)/200")
                .font(.caption)
                .foregroundStyle(Color.osmOnSurfaceVariant)
        }
    }

    @ViewBuilder
    private func review(_ state: CreateCardState) -> some View {
        CreateCardSectionHeader(
            title: AppStrings.CreateCard.reviewTitle,
            subtitle: AppStrings.CreateCard.reviewSubtitle
        )
        VStack(spacing: 0) {
            ReviewRow(label: AppStrings.CreateCard.typeLabel, value: state.selectedCardType?.name ?? "")
            ReviewRow(label: AppStrings.CreateCard.preclassifierLabel, value: state.selectedPreclassifier?.description_ ?? "")
            ReviewRow(label: AppStrings.CreateCard.priorityLabel, value: state.selectedPriority?.description_ ?? "")
            ReviewRow(label: AppStrings.CreateCard.levelLabel, value: state.selectedLocation)
            ReviewRow(label: AppStrings.CreateCard.descriptionLabel, value: state.description_)
            ReviewRow(
                label: AppStrings.CreateCard.evidenceLabel,
                value: state.evidences.isEmpty
                    ? String(localized: AppStrings.CreateCard.noEvidence)
                    : String(
                        format: String(localized: AppStrings.CreateCard.evidenceCount),
                        locale: .current,
                        Int64(state.evidences.count)
                    )
            )
        }
        .padding(.horizontal, 16)
        .background(Color.osmSurface)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(Color.osmOutlineVariant, lineWidth: 1)
        }
        CreateCardInfoBox(text: AppStrings.CreateCard.offlineNotice)
    }

    private func bottomActions(_ state: CreateCardState) -> some View {
        HStack(spacing: 12) {
            if state.step != .classification {
                Button(AppStrings.CreateCard.previous, action: viewModel.back)
                    .buttonStyle(.bordered)
                    .controlSize(.large)
                    .disabled(state.isSaving)
            }
            Button {
                if state.step == .review {
                    Task { await viewModel.save() }
                } else {
                    viewModel.next()
                }
            } label: {
                HStack {
                    if state.isSaving { ProgressView().tint(Color.osmOnPrimary) }
                    Text(state.step == .review ? AppStrings.CreateCard.save : AppStrings.CreateCard.continueAction)
                        .frame(maxWidth: .infinity)
                }
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
            .disabled(!state.initialized || state.isSaving || state.isProcessingEvidence)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 12)
        .background(.regularMaterial)
    }

    private func conditionName(_ value: String?) -> String? {
        switch value {
        case "safe": String(localized: AppStrings.CreateCard.conditionSafe)
        case "unsafe": String(localized: AppStrings.CreateCard.conditionUnsafe)
        default: nil
        }
    }

    private func validationMessage(_ error: CreateCardValidationError) -> String {
        let key: LocalizedStringResource = switch error {
        case .siteRequired: AppStrings.CreateCard.errorSite
        case .catalogsUnavailable: AppStrings.CreateCard.errorCatalogs
        case .cardTypeRequired: AppStrings.CreateCard.errorType
        case .cardTypeValueRequired: AppStrings.CreateCard.errorCondition
        case .preclassifierRequired: AppStrings.CreateCard.errorPreclassifier
        case .priorityRequired: AppStrings.CreateCard.errorPriority
        case .levelRequired: AppStrings.CreateCard.errorLevel
        case .levelMustBeFinal: AppStrings.CreateCard.errorFinalLevel
        case .descriptionRequired: AppStrings.CreateCard.errorDescription
        case .descriptionTooLong: AppStrings.CreateCard.errorDescriptionLength
        case .customDueDateRequired: AppStrings.CreateCard.errorDueDate
        case .customDueDateInvalid: AppStrings.CreateCard.errorDueDateInvalid
        case .invalidCatalogSelection: AppStrings.CreateCard.errorSelection
        case .saveFailed: AppStrings.CreateCard.errorSave
        default: AppStrings.CreateCard.errorSave
        }
        return String(localized: key)
    }

    private func evidenceMessage(_ error: CreateCardEvidenceError) -> String {
        let key: LocalizedStringResource = switch error.reason {
        case .imageLimitReached: AppStrings.CreateCard.errorImageLimit
        case .videoLimitReached: AppStrings.CreateCard.errorVideoLimit
        case .audioLimitReached: AppStrings.CreateCard.errorAudioLimit
        case .videoDurationExceeded: AppStrings.CreateCard.errorVideoDuration
        case .audioDurationExceeded: AppStrings.CreateCard.errorAudioDuration
        case .fileTooLarge: AppStrings.CreateCard.errorFileSize
        case .totalLimitReached: AppStrings.CreateCard.errorTotalEvidence
        case .invalidMedia: AppStrings.CreateCard.errorInvalidMedia
        case .importFailed: AppStrings.CreateCard.evidenceImportError
        default: AppStrings.CreateCard.evidenceImportError
        }
        guard let limit = error.limit else { return String(localized: key) }
        return String(format: String(localized: key), locale: .current, limit.int64Value)
    }
}

private struct CreateCardStepIndicator: View {
    let current: CreateCardStep

    private let steps: [(CreateCardStep, LocalizedStringResource)] = [
        (.classification, AppStrings.CreateCard.stepClassify),
        (.location, AppStrings.CreateCard.stepLocation),
        (.evidence, AppStrings.CreateCard.stepEvidence),
        (.details, AppStrings.CreateCard.stepDetails),
        (.review, AppStrings.CreateCard.stepReview)
    ]

    var body: some View {
        HStack(spacing: 4) {
            ForEach(Array(steps.enumerated()), id: \.offset) { index, item in
                let completed = index < current.ordinal
                let selected = item.0 == current
                VStack(spacing: 4) {
                    ZStack {
                        Circle()
                            .fill(completed || selected ? Color.osmPrimary : Color.osmSurface)
                            .overlay { Circle().stroke(Color.osmOutlineVariant, lineWidth: completed || selected ? 0 : 1) }
                        if completed {
                            Image(systemName: "checkmark")
                                .font(.caption.weight(.bold))
                                .foregroundStyle(Color.osmOnPrimary)
                        } else {
                            Text("\(index + 1)")
                                .font(.caption.weight(.bold))
                                .foregroundStyle(selected ? Color.osmOnPrimary : Color.osmOnSurface)
                        }
                    }
                    .frame(width: 30, height: 30)
                    Text(item.1)
                        .font(.caption2.weight(selected ? .semibold : .regular))
                        .foregroundStyle(selected ? Color.osmPrimary : Color.osmOnSurfaceVariant)
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)
                }
                .frame(maxWidth: .infinity)
            }
        }
    }
}

private struct CreateCardSectionHeader: View {
    let title: LocalizedStringResource
    let subtitle: LocalizedStringResource

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title).font(OSMTypography.title2)
            Text(subtitle)
                .font(.subheadline)
                .foregroundStyle(Color.osmOnSurfaceVariant)
        }
    }
}

private struct CreateCardSelectionRow: View {
    let title: LocalizedStringResource
    let value: String?
    let placeholder: LocalizedStringResource
    let systemImage: String
    var supportingText: String?
    var enabled = true
    let action: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.subheadline.weight(.semibold))
            Button(action: action) {
                HStack(spacing: 12) {
                    Image(systemName: systemImage)
                        .foregroundStyle(Color.osmPrimary)
                    Text(value ?? String(localized: placeholder))
                        .foregroundStyle(value == nil ? Color.osmOnSurfaceVariant : Color.osmOnSurface)
                        .multilineTextAlignment(.leading)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Image(systemName: "chevron.right")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(Color.osmOnSurfaceVariant)
                }
                .padding(14)
                .frame(minHeight: 60)
                .background(enabled ? Color.osmSurface : Color.osmSurfaceVariant)
                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                .overlay {
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .stroke(Color.osmOutlineVariant, lineWidth: 1)
                }
            }
            .buttonStyle(.plain)
            .disabled(!enabled)
            if let supportingText, !supportingText.isEmpty {
                Text(supportingText)
                    .font(.caption)
                    .foregroundStyle(Color.osmOnSurfaceVariant)
            }
        }
    }
}

private struct CreateCardInfoBox: View {
    let text: LocalizedStringResource

    var body: some View {
        Label(text, systemImage: "info.circle")
            .font(.caption)
            .foregroundStyle(Color.osmOnPrimaryContainer)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(12)
            .background(Color.osmPrimaryContainer.opacity(0.55))
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}

private struct ReviewRow: View {
    let label: LocalizedStringResource
    let value: String

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Text(label)
                .font(.caption.weight(.semibold))
                .foregroundStyle(Color.osmOnSurfaceVariant)
                .frame(width: 100, alignment: .leading)
            Text(value)
                .font(.subheadline)
                .foregroundStyle(Color.osmOnSurface)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.vertical, 11)
        .overlay(alignment: .bottom) { Divider() }
    }
}

private struct CreateCardSelectionSheet: View {
    let state: CreateCardState
    @Bindable var viewModel: CreateCardViewModel
    @State private var selectedDate = Date.now

    var body: some View {
        NavigationStack {
            Group {
                if state.activeSheet == .customDueDate {
                    VStack(spacing: 24) {
                        DatePicker(
                            AppStrings.CreateCard.dueDateLabel,
                            selection: $selectedDate,
                            in: Date.now...,
                            displayedComponents: .date
                        )
                        .datePickerStyle(.graphical)
                        Button(AppStrings.CreateCard.useDate) {
                            let value = selectedDate.formatted(
                                .iso8601.year().month().day().dateSeparator(.dash)
                            )
                            viewModel.setDueDate(value)
                        }
                        .buttonStyle(.borderedProminent)
                        .controlSize(.large)
                        Spacer()
                    }
                    .padding()
                } else {
                    List {
                        if state.activeSheet == .level, !state.levelNavigationPath.isEmpty {
                            Section {
                                Button {
                                    viewModel.navigateLevel(nil)
                                } label: {
                                    Label(AppStrings.CreateCard.levelRoot, systemImage: "house")
                                }
                                ForEach(state.levelNavigationPath, id: \.id) { level in
                                    Button(level.name) { viewModel.navigateLevel(level.id) }
                                }
                            }
                        }
                        Section {
                            ForEach(state.sheetItems, id: \.id) { item in
                                Button {
                                    viewModel.select(item.id)
                                } label: {
                                    HStack {
                                        VStack(alignment: .leading, spacing: 3) {
                                            Text(localizedSelectionTitle(item.title, sheet: state.activeSheet))
                                                .foregroundStyle(Color.osmOnSurface)
                                            if let subtitle = item.subtitle, !subtitle.isEmpty {
                                                Text(subtitle)
                                                    .font(.caption)
                                                    .foregroundStyle(Color.osmOnSurfaceVariant)
                                            }
                                        }
                                        Spacer()
                                        if item.hasChildren {
                                            Image(systemName: "chevron.right")
                                                .foregroundStyle(Color.osmOnSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    .searchable(
                        text: Binding(get: { state.sheetQuery }, set: viewModel.search),
                        prompt: AppStrings.CreateCard.searchOptions
                    )
                    .overlay {
                        if state.sheetItems.isEmpty {
                            ContentUnavailableView.search(text: state.sheetQuery)
                        }
                    }
                }
            }
            .navigationTitle(sheetTitle(state.activeSheet))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(AppStrings.Common.cancel, action: viewModel.dismissSheet)
                }
            }
        }
    }

    private func sheetTitle(_ sheet: CreateCardSheet?) -> LocalizedStringResource {
        switch sheet {
        case .cardType: AppStrings.CreateCard.selectType
        case .cardTypeValue: AppStrings.CreateCard.selectCondition
        case .preclassifier: AppStrings.CreateCard.selectPreclassifier
        case .priority: AppStrings.CreateCard.selectPriority
        case .level: AppStrings.CreateCard.selectLevel
        case .customDueDate: AppStrings.CreateCard.selectDueDate
        default: AppStrings.CreateCard.selectOption
        }
    }

    private func localizedSelectionTitle(_ title: String, sheet: CreateCardSheet?) -> String {
        guard sheet == .cardTypeValue else { return title }
        return title == "safe"
            ? String(localized: AppStrings.CreateCard.conditionSafe)
            : String(localized: AppStrings.CreateCard.conditionUnsafe)
    }
}
