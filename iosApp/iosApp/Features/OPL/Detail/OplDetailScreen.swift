import PDFKit
import SharedLogic
import SwiftUI

struct OplDetailScreen: View {
    @Bindable var viewModel: OplDetailViewModel

    var body: some View {
        ScrollView {
            if viewModel.isLoading {
                ProgressView().tint(.osmPrimary)
                    .frame(maxWidth: .infinity, minHeight: 240)
            } else if let error = viewModel.error {
                AnatomyBanner(message: error, type: .error, actionTitle: AppStrings.Cards.retry, onAction: viewModel.retry)
                    .padding(20)
            } else if let opl = viewModel.opl {
                content(opl)
            }
        }
        .safeAreaInset(edge: .top, spacing: 0) {
            if viewModel.isDownloading { downloadProgress.background(Color.osmBackground) }
            if viewModel.isDeleting {
                VStack(spacing: 8) {
                    ProgressView().tint(.osmPrimary)
                    AnatomyText(OplStrings.deleting, font: OSMTypography.caption)
                }
                .frame(maxWidth: .infinity).padding(8).background(Color.osmBackground)
            }
        }
        .background(Color.osmBackground.ignoresSafeArea())
        .navigationTitle(OplStrings.detailTitle)
        .navigationBarTitleDisplayMode(.large)
        .toolbar(.hidden, for: .tabBar)
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                Button(action: viewModel.requestDownload) { Image(systemName: "arrow.down.to.line") }
                    .disabled(viewModel.opl == nil || viewModel.isBusy)
                    .accessibilityLabel(OplStrings.download)
                if viewModel.isDownloaded {
                    Button(role: .destructive, action: viewModel.requestDelete) { Image(systemName: "trash") }
                        .disabled(viewModel.isBusy)
                        .accessibilityLabel(OplStrings.delete)
                }
            }
        }
        .task { await viewModel.start() }
        .onDisappear {
            if !viewModel.isShowingMediaPreview && viewModel.selectedPDF == nil { viewModel.stop() }
        }
        .alert(viewModel.confirmation?.title ?? OplStrings.downloadTitle,
               isPresented: Binding(get: { viewModel.confirmation != nil }, set: { if !$0 { viewModel.confirmation = nil } }),
               presenting: viewModel.confirmation) { choice in
            Button(choice.action, role: choice == .delete ? .destructive : nil) { viewModel.confirm(choice) }
            Button(AppStrings.Common.cancel, role: .cancel) { viewModel.confirmation = nil }
        } message: { choice in Text(choice.message) }
        .sheet(item: $viewModel.selectedPDF) { pdf in
            OplPDFSheet(title: pdf.oplFileName, path: viewModel.files[pdf.id],
                failed: viewModel.failedIDs.contains(pdf.id), onRetry: { viewModel.resolve(pdf) },
                onClose: { viewModel.selectedPDF = nil })
                .presentationDetents([.large])
                .presentationDragIndicator(.visible)
        }
    }

    private var downloadProgress: some View {
        VStack(alignment: .leading, spacing: 6) {
            ProgressView(value: Double(viewModel.progress?.progress ?? 0)).tint(.osmPrimary)
            AnatomyText(verbatim: progressLabel, font: OSMTypography.caption)
            if let progress = viewModel.progress, progress.downloadedBytes > 0 {
                AnatomyText(verbatim: ByteCountFormatter.string(fromByteCount: progress.downloadedBytes, countStyle: .file),
                            font: OSMTypography.caption, color: .osmOnSurfaceVariant)
            }
        }
        .padding(.horizontal, 20).padding(.vertical, 10)
        .accessibilityElement(children: .combine)
    }

    private var progressLabel: String {
        guard let progress = viewModel.progress else { return String(localized: OplStrings.preparing) }
        switch progress.stage {
        case .media:
            return String(format: String(localized: OplStrings.progress),
                Int64(progress.completedFiles), Int64(progress.totalFiles), Int64(progress.progress * 100))
        case .saving: return String(localized: OplStrings.saving)
        case .complete: return String(localized: OplStrings.downloadComplete)
        default: return String(localized: OplStrings.preparing)
        }
    }

    private func content(_ opl: Opl) -> some View {
        LazyVStack(alignment: .leading, spacing: 16) {
            if let error = viewModel.operationError {
                AnatomyBanner(message: error, type: .error, onDismiss: { viewModel.operationError = nil })
            }
            if let notice = viewModel.notice {
                AnatomyBanner(message: notice, type: .success, onDismiss: { viewModel.notice = nil })
            }
            OplSection(title: viewModel.site.name) {
                AnatomyText(verbatim: opl.title, font: OSMTypography.title2)
                if viewModel.isDownloaded { OplBadge(title: String(localized: OplStrings.downloaded), icon: "checkmark.circle") }
                if let type = opl.typeName, !type.isEmpty { OplBadge(title: type) }
                Divider()
                AnatomyText(OplStrings.objective, font: OSMTypography.headline)
                AnatomyText(verbatim: opl.objective?.isEmpty == false ? opl.objective! : String(localized: OplStrings.noObjective))
                    .textSelection(.enabled)
            }
            OplSection(title: String(localized: OplStrings.locations)) {
                if opl.levels.isEmpty { AnatomyText(OplStrings.noLocations) }
                ForEach(opl.levels, id: \.id) { level in
                    HStack(alignment: .top, spacing: 10) {
                        Image(systemName: "mappin.and.ellipse").foregroundStyle(Color.osmPrimary)
                        VStack(alignment: .leading, spacing: 4) {
                            AnatomyText(verbatim: level.name, font: OSMTypography.bodyEmphasized)
                            if let machine = level.machineId, !machine.isEmpty { AnatomyText(verbatim: machine, font: OSMTypography.caption) }
                            if let description = level.description_, !description.isEmpty { AnatomyText(verbatim: description, font: OSMTypography.caption) }
                        }
                    }
                }
            }
            AnatomyText(OplStrings.content, font: OSMTypography.title2)
            if opl.content.isEmpty { AnatomyBanner(message: String(localized: OplStrings.noContent), type: .info) }
            ForEach(opl.content.sorted { $0.order == $1.order ? $0.id < $1.id : $0.order < $1.order }, id: \.id) { item in
                OplContentCard(content: item, viewModel: viewModel).id("\(item.id)-\(viewModel.mediaEpoch)")
            }
            OplSection(title: String(localized: OplStrings.documentInfo)) {
                OplDetailValue(label: OplStrings.identifier, value: String(opl.id))
                OplDetailValue(label: OplStrings.creator, value: opl.creatorName)
                OplDetailValue(label: OplStrings.reviewer, value: opl.reviewerName)
                OplDetailValue(label: OplStrings.created, value: opl.createdAt.map(oplDisplayDate))
                OplDetailValue(label: OplStrings.updated, value: opl.updatedAt.map(oplDisplayDate))
                if let count = opl.ciltUsageCount { OplDetailValue(label: OplStrings.ciltUsage, value: String(count.int64Value)) }
                if let count = opl.directUsageCount { OplDetailValue(label: OplStrings.directUsage, value: String(count.int64Value)) }
                if let last = opl.lastUsedAt { OplDetailValue(label: OplStrings.lastUsed, value: oplDisplayDate(last)) }
            }
        }
        .padding(20).frame(maxWidth: 720).frame(maxWidth: .infinity)
    }
}

private struct OplContentCard: View {
    let content: OplContent
    @Bindable var viewModel: OplDetailViewModel

    var body: some View {
        AnatomyCard {
            VStack(alignment: .leading, spacing: 12) {
                if content.type == .text {
                    AnatomyText(OplStrings.text, font: OSMTypography.headline)
                    AnatomyText(verbatim: content.text?.isEmpty == false ? content.text! : String(localized: OplStrings.emptyText))
                        .textSelection(.enabled)
                } else {
                    if let text = content.text, !text.isEmpty { AnatomyText(verbatim: text).textSelection(.enabled) }
                    if content.oplGalleryType != nil {
                        if viewModel.isBusy { AnatomyText(OplStrings.mediaWait, font: OSMTypography.caption) }
                        else {
                            EvidenceMediaGallery(items: [viewModel.galleryItem(content)], isOnlyRead: true,
                                                 onRequestSource: { _ in viewModel.resolve(content) },
                                                 onPreviewPresentationChanged: { viewModel.isShowingMediaPreview = $0 })
                        }
                    } else if content.type == .pdf {
                        Label(content.oplFileName, systemImage: "doc.richtext").font(OSMTypography.bodyEmphasized)
                        AnatomyButton(title: OplStrings.openPDF, action: { viewModel.openPDF(content) }, leadingSystemImage: "doc.richtext")
                            .disabled(viewModel.isBusy)
                    } else { AnatomyText(OplStrings.unsupported, font: OSMTypography.caption) }
                }
            }
        }
        .task(id: viewModel.isBusy) { if !viewModel.isBusy { viewModel.prepareImage(content) } }
    }
}

private struct OplSection<Content: View>: View {
    let title: String
    @ViewBuilder let content: Content
    var body: some View {
        AnatomyCard {
            VStack(alignment: .leading, spacing: 12) {
                AnatomyText(verbatim: title, font: OSMTypography.headline)
                content
            }
        }
    }
}

private struct OplDetailValue: View {
    let label: LocalizedStringResource
    let value: String?
    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            AnatomyText(label, font: OSMTypography.caption, color: .osmOnSurfaceVariant)
            AnatomyText(verbatim: value?.isEmpty == false ? value! : String(localized: OplStrings.notAssigned))
        }
    }
}

private struct OplPDFSheet: View {
    let title: String
    let path: String?
    let failed: Bool
    let onRetry: () -> Void
    let onClose: () -> Void
    @State private var document: PDFDocument?
    @State private var invalidDocument = false

    var body: some View {
        NavigationStack {
            Group {
                if let document { OplPDFView(document: document).ignoresSafeArea(edges: .bottom) }
                else if failed || invalidDocument {
                    VStack(spacing: 16) {
                        AnatomyText(OplStrings.pdfFailed)
                        AnatomyButton(title: AppStrings.Cards.retry, action: onRetry)
                    }.padding(24)
                } else { ProgressView().tint(.osmPrimary).frame(maxWidth: .infinity, maxHeight: .infinity) }
            }
            .background(Color.osmBackground)
            .task(id: path) {
                document = path.flatMap { PDFDocument(url: URL(fileURLWithPath: $0)) }
                invalidDocument = path != nil && (document == nil || document?.pageCount == 0)
                if invalidDocument { document = nil }
            }
            .navigationTitle(title).navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(action: onClose) { Image(systemName: "xmark") }.accessibilityLabel(AppStrings.Settings.close)
                }
            }
        }
    }
}

private struct OplPDFView: UIViewRepresentable {
    let document: PDFDocument
    func makeUIView(context: Context) -> PDFView {
        let view = PDFView()
        view.autoScales = true
        view.displayMode = .singlePageContinuous
        view.displayDirection = .vertical
        view.backgroundColor = .systemBackground
        view.document = document
        return view
    }
    func updateUIView(_ view: PDFView, context: Context) {
        if view.document !== document {
            view.document = document
            view.autoScales = true
        }
    }
    static func dismantleUIView(_ view: PDFView, coordinator: ()) { view.document = nil }
}

extension OplContent: @retroactive Identifiable {}
