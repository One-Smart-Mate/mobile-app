import SharedLogic
import SwiftUI

struct CardSolutionView: View {
    @Bindable var viewModel: CardSolutionViewModel
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
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(true)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button(action: viewModel.close) {
                    Image(systemName: "chevron.left")
                }
                .disabled(viewModel.state?.isSaving == true)
                .accessibilityLabel(AppStrings.CreateCard.back)
            }
        }
        .task { await viewModel.start() }
        .onChange(of: viewModel.shouldClose) { _, shouldClose in
            if shouldClose { onClose() }
        }
    }

    private func content(_ state: CardSolutionState) -> some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 18) {
                VStack(alignment: .leading, spacing: 5) {
                    Text(heading)
                        .font(.title2.bold())
                        .foregroundStyle(Color.osmOnSurface)
                    Text(subtitle)
                        .font(.subheadline)
                        .foregroundStyle(Color.osmOnSurfaceVariant)
                }

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

                if let card = state.card { CardSolutionSummary(card: card) }

                Button(action: viewModel.openEmployeeSheet) {
                    VStack(alignment: .leading, spacing: 7) {
                        Text(AppStrings.CardSolution.employee)
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(Color.osmOnSurfaceVariant)
                        HStack(spacing: 12) {
                            Image(systemName: "person")
                                .foregroundStyle(Color.osmPrimary)
                            Text(state.selectedEmployee?.name ?? String(localized: AppStrings.CardSolution.employeePlaceholder))
                                .foregroundStyle(state.selectedEmployee == nil ? Color.osmOnSurfaceVariant : Color.osmOnSurface)
                                .frame(maxWidth: .infinity, alignment: .leading)
                            Image(systemName: "chevron.right")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(Color.osmOnSurfaceVariant)
                        }
                        .padding(14)
                        .background(Color.osmSurface)
                        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                        .overlay {
                            RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .stroke(Color.osmOutlineVariant, lineWidth: 1)
                        }
                    }
                }
                .buttonStyle(.plain)

                VStack(alignment: .trailing, spacing: 6) {
                    TextEditor(
                        text: Binding(
                            get: { state.comments },
                            set: viewModel.updateComments
                        )
                    )
                    .font(.body)
                    .scrollContentBackground(.hidden)
                    .padding(10)
                    .frame(minHeight: 130, maxHeight: 180)
                    .background(Color.osmSurface)
                    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                    .overlay {
                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .stroke(Color.osmOutlineVariant, lineWidth: 1)
                    }
                    .overlay(alignment: .topLeading) {
                        if state.comments.isEmpty {
                            Text(AppStrings.CardSolution.commentsPlaceholder)
                                .foregroundStyle(Color.osmOnSurfaceVariant)
                                .padding(.horizontal, 16)
                                .padding(.vertical, 18)
                                .allowsHitTesting(false)
                        }
                    }
                    Text("\(state.comments.count)/500")
                        .font(.caption)
                        .foregroundStyle(Color.osmOnSurfaceVariant)
                }

                VStack(alignment: .leading, spacing: 5) {
                    Text(AppStrings.CardSolution.evidenceTitle)
                        .font(.title3.bold())
                    Text(AppStrings.CardSolution.evidenceSubtitle)
                        .font(.subheadline)
                        .foregroundStyle(Color.osmOnSurfaceVariant)
                }
                CardEvidenceCaptureView(state: state, viewModel: viewModel)

                AnatomyBanner(
                    message: String(localized: AppStrings.CardSolution.offlineNotice),
                    type: .info
                )
            }
            .padding(.horizontal, 20)
            .padding(.top, 10)
            .padding(.bottom, 110)
            .frame(maxWidth: 700)
            .frame(maxWidth: .infinity)
        }
        .safeAreaInset(edge: .bottom) {
            Button {
                Task { await viewModel.save() }
            } label: {
                HStack(spacing: 10) {
                    if state.isSaving { ProgressView().tint(Color.osmOnPrimary) }
                    Text(saveTitle).frame(maxWidth: .infinity)
                }
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
            .disabled(!canSave(state))
            .padding(.horizontal, 20)
            .padding(.top, 12)
            .padding(.bottom, 8)
            .background(.bar)
        }
        .sheet(
            isPresented: Binding(
                get: { state.showEmployeeSheet },
                set: { if !$0 { viewModel.dismissEmployeeSheet() } }
            )
        ) {
            CardSolutionEmployeeSheet(state: state, viewModel: viewModel)
                .presentationDetents([.large])
                .presentationDragIndicator(.visible)
        }
    }

    private var isProvisional: Bool { viewModel.type == .provisional }
    private var title: LocalizedStringResource { isProvisional ? AppStrings.CardSolution.provisionalTitle : AppStrings.CardSolution.definitiveTitle }
    private var heading: LocalizedStringResource { isProvisional ? AppStrings.CardSolution.provisionalHeading : AppStrings.CardSolution.definitiveHeading }
    private var subtitle: LocalizedStringResource { isProvisional ? AppStrings.CardSolution.provisionalSubtitle : AppStrings.CardSolution.definitiveSubtitle }
    private var saveTitle: LocalizedStringResource { isProvisional ? AppStrings.CardSolution.saveProvisional : AppStrings.CardSolution.saveDefinitive }

    private func canSave(_ state: CardSolutionState) -> Bool {
        guard state.initialized, !state.isSaving, !state.isProcessingEvidence else { return false }
        switch state.validationError {
        case .cardNotFound, .cardAlreadyClosed, .solutionAlreadyApplied, .cardTypeUnavailable:
            return false
        default:
            return true
        }
    }

    private func validationMessage(_ error: CardSolutionValidationError) -> String {
        let resource: LocalizedStringResource = switch error {
        case .cardNotFound: AppStrings.CardSolution.errorNotFound
        case .cardAlreadyClosed: AppStrings.CardSolution.errorClosed
        case .solutionAlreadyApplied: AppStrings.CardSolution.errorApplied
        case .cardTypeUnavailable: AppStrings.CardSolution.errorCardType
        case .employeeRequired: AppStrings.CardSolution.errorEmployee
        case .commentsRequired: AppStrings.CardSolution.errorComments
        case .commentsTooLong: AppStrings.CardSolution.errorCommentsLength
        case .evidenceProcessing: AppStrings.CardSolution.errorEvidenceProcessing
        case .saveFailed: AppStrings.CardSolution.errorSave
        default: AppStrings.CardSolution.errorSave
        }
        return String(localized: resource)
    }

    private func evidenceMessage(_ error: CreateCardEvidenceError) -> String {
        let resource: LocalizedStringResource = switch error.reason {
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
        return String(localized: resource)
    }
}

private struct CardSolutionSummary: View {
    let card: Card

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(card.siteCardId > 0 ? "#\(card.siteCardId)" : String(localized: AppStrings.Cards.localFolio))
                .font(.caption.bold())
                .foregroundStyle(Color.osmPrimary)
            Text(card.problemDescription)
                .font(.headline)
                .foregroundStyle(Color.osmOnSurface)
                .lineLimit(2)
            if let location = card.location, !location.isEmpty {
                Label(location, systemImage: "mappin.and.ellipse")
                    .font(.caption)
                    .foregroundStyle(Color.osmOnSurfaceVariant)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(Color.osmSurface)
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .stroke(Color.osmOutlineVariant, lineWidth: 1)
        }
    }
}

private struct CardSolutionEmployeeSheet: View {
    let state: CardSolutionState
    @Bindable var viewModel: CardSolutionViewModel

    var body: some View {
        NavigationStack {
            List(state.filteredEmployees, id: \.id) { employee in
                Button {
                    viewModel.selectEmployee(employee.id)
                } label: {
                    HStack(spacing: 12) {
                        Image(systemName: "person.crop.circle.fill")
                            .font(.title2)
                            .foregroundStyle(Color.osmPrimary)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(employee.name).font(.body.weight(.semibold))
                            Text(employee.email)
                                .font(.caption)
                                .foregroundStyle(Color.osmOnSurfaceVariant)
                        }
                        Spacer()
                        if employee.id == state.selectedEmployee?.id {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundStyle(Color.osmPrimary)
                        }
                    }
                }
                .buttonStyle(.plain)
            }
            .overlay {
                if state.filteredEmployees.isEmpty {
                    ContentUnavailableView.search(text: state.employeeQuery)
                }
            }
            .searchable(
                text: Binding(
                    get: { state.employeeQuery },
                    set: viewModel.searchEmployees
                ),
                prompt: AppStrings.CardSolution.employeeSearch
            )
            .navigationTitle(AppStrings.CardSolution.employeeSheetTitle)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(AppStrings.Common.done, action: viewModel.dismissEmployeeSheet)
                }
            }
        }
    }
}
