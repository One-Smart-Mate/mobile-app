import CoreTransferable
import PhotosUI
import SharedLogic
import SwiftUI
import UniformTypeIdentifiers
import UIKit

struct CardEvidenceCaptureView: View {
    let state: CreateCardState
    @Bindable var viewModel: CreateCardViewModel

    @State private var requestedMedia: CardEvidenceMediaType?
    @State private var showSourceOptions = false
    @State private var showCamera = false
    @State private var showAudioRecorder = false
    @State private var showPhotoPicker = false
    @State private var pickedItem: PhotosPickerItem?
    @State private var isImporting = false

    private let storage = CardEvidenceStorage.shared

    var body: some View {
        VStack(spacing: 12) {
            evidenceAction(
                title: AppStrings.CreateCard.photos,
                subtitle: limitText(
                    current: Int64(state.imageEvidenceCount),
                    maximum: limit(state.selectedCardType?.quantityImagesCreate)
                ),
                systemImage: "camera",
                mediaType: .image,
                enabled: canAdd(.image)
            )
            evidenceAction(
                title: AppStrings.CreateCard.videos,
                subtitle: timedLimitText(
                    current: Int64(state.videoEvidenceCount),
                    maximum: limit(state.selectedCardType?.quantityVideosCreate),
                    duration: limit(state.selectedCardType?.videosDurationCreate)
                ),
                systemImage: "video",
                mediaType: .video,
                enabled: canAdd(.video)
            )
            evidenceAction(
                title: AppStrings.CreateCard.audio,
                subtitle: timedLimitText(
                    current: Int64(state.audioEvidenceCount),
                    maximum: limit(state.selectedCardType?.quantityAudiosCreate),
                    duration: limit(state.selectedCardType?.audiosDurationCreate)
                ),
                systemImage: "waveform",
                mediaType: .audio,
                enabled: canAdd(.audio)
            )

            if !state.evidences.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    Text(AppStrings.CreateCard.attachedEvidence)
                        .font(.subheadline.weight(.semibold))
                    ForEach(state.evidences, id: \.id) { evidence in
                        evidenceRow(evidence)
                    }
                }
                .padding(.top, 4)
            }

            if isImporting || state.isProcessingEvidence {
                HStack(spacing: 10) {
                    ProgressView().tint(Color.osmPrimary)
                    Text(AppStrings.CreateCard.processingEvidence)
                        .font(.footnote)
                        .foregroundStyle(Color.osmOnSurfaceVariant)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.top, 4)
            }
        }
        .confirmationDialog(
            AppStrings.CreateCard.evidenceSource,
            isPresented: $showSourceOptions,
            titleVisibility: .visible
        ) {
            if UIImagePickerController.isSourceTypeAvailable(.camera) {
                Button(AppStrings.CreateCard.camera) { showCamera = true }
            }
            Button(AppStrings.CreateCard.photoLibrary) { showPhotoPicker = true }
            Button(AppStrings.Common.cancel, role: .cancel) {}
        }
        .photosPicker(
            isPresented: $showPhotoPicker,
            selection: $pickedItem,
            matching: requestedMedia == .video ? .videos : .images,
            preferredItemEncoding: .current
        )
        .onChange(of: pickedItem) { _, item in
            guard let item, let requestedMedia else { return }
            Task { await importPickerItem(item, as: requestedMedia) }
        }
        .fullScreenCover(isPresented: $showCamera) {
            if let requestedMedia {
                CardCameraPicker(
                    mediaType: requestedMedia,
                    maximumVideoDuration: TimeInterval(
                        limit(state.selectedCardType?.videosDurationCreate)
                    ),
                    onPicked: { url in
                        showCamera = false
                        Task { await importFile(url, as: requestedMedia, removeSource: true) }
                    },
                    onCancel: { showCamera = false }
                )
                .ignoresSafeArea()
            }
        }
        .sheet(isPresented: $showAudioRecorder) {
            CardAudioRecorderSheet(
                maximumSeconds: limit(state.selectedCardType?.audiosDurationCreate),
                onRecorded: { url in
                    showAudioRecorder = false
                    Task { await importFile(url, as: .audio, removeSource: true) }
                },
                onCancel: { showAudioRecorder = false }
            )
            .presentationDetents([.medium])
            .presentationDragIndicator(.visible)
        }
    }

    private func evidenceAction(
        title: LocalizedStringResource,
        subtitle: String,
        systemImage: String,
        mediaType: CardEvidenceMediaType,
        enabled: Bool
    ) -> some View {
        Button {
            requestedMedia = mediaType
            if mediaType == .audio {
                showAudioRecorder = true
            } else {
                showSourceOptions = true
            }
        } label: {
            HStack(spacing: 14) {
                Image(systemName: systemImage)
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(Color.osmPrimary)
                    .frame(width: 42, height: 42)
                    .background(Color.osmPrimaryContainer)
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                VStack(alignment: .leading, spacing: 3) {
                    Text(title)
                        .font(.body.weight(.semibold))
                        .foregroundStyle(Color.osmOnSurface)
                    Text(subtitle)
                        .font(.caption)
                        .foregroundStyle(Color.osmOnSurfaceVariant)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: "plus.circle.fill")
                    .font(.title3)
                    .foregroundStyle(Color.osmPrimary)
            }
            .padding(12)
            .background(Color.osmSurface)
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .stroke(Color.osmOutlineVariant, lineWidth: 1)
            }
        }
        .buttonStyle(.plain)
        .opacity(enabled ? 1 : 0.55)
        .disabled(!enabled || isImporting || state.isProcessingEvidence)
    }

    private func evidenceRow(_ evidence: CreateCardEvidenceDraft) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon(for: evidence.mediaType))
                .foregroundStyle(Color.osmPrimary)
                .frame(width: 34, height: 34)
                .background(Color.osmPrimaryContainer.opacity(0.7))
                .clipShape(RoundedRectangle(cornerRadius: 9, style: .continuous))
            VStack(alignment: .leading, spacing: 2) {
                Text(evidence.displayName)
                    .font(.subheadline.weight(.medium))
                    .lineLimit(1)
                Text(fileDetail(evidence))
                    .font(.caption)
                    .foregroundStyle(Color.osmOnSurfaceVariant)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Button(role: .destructive) {
                Task { await viewModel.removeEvidence(evidence.id) }
            } label: {
                Image(systemName: "trash")
                    .frame(width: 44, height: 44)
            }
            .accessibilityLabel(AppStrings.CreateCard.removeEvidence)
        }
        .padding(.leading, 10)
        .background(Color.osmSurface)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    private func importPickerItem(_ item: PhotosPickerItem, as mediaType: CardEvidenceMediaType) async {
        await MainActor.run { isImporting = true }
        defer {
            Task { @MainActor in
                isImporting = false
                pickedItem = nil
            }
        }
        do {
            if mediaType == .video {
                guard let transferred = try await item.loadTransferable(type: PickedVideoFile.self) else {
                    throw EvidenceImportError.missingData
                }
                defer { try? FileManager.default.removeItem(at: transferred.url) }
                let prepared = try await storage.importFile(from: transferred.url, mediaType: .video)
                await viewModel.addEvidence(prepared)
                return
            }
            guard let data = try await item.loadTransferable(type: Data.self) else {
                throw EvidenceImportError.missingData
            }
            let type = preferredType(from: item.supportedContentTypes, mediaType: mediaType)
            let ext = type.preferredFilenameExtension ?? defaultExtension(for: mediaType)
            let prepared = try await storage.persistData(
                data,
                fileName: "evidence-\(UUID().uuidString).\(ext)",
                mediaType: mediaType,
                mimeType: type.preferredMIMEType ?? defaultMimeType(for: mediaType)
            )
            await viewModel.addEvidence(prepared)
        } catch {
            viewModel.evidenceImportFailed()
        }
    }

    private func importFile(_ url: URL, as mediaType: CardEvidenceMediaType, removeSource: Bool) async {
        await MainActor.run { isImporting = true }
        defer {
            if removeSource { try? FileManager.default.removeItem(at: url) }
            Task { @MainActor in isImporting = false }
        }
        do {
            let prepared = try await storage.importFile(from: url, mediaType: mediaType)
            await viewModel.addEvidence(prepared)
        } catch {
            viewModel.evidenceImportFailed()
        }
    }

    private func limit(_ value: KotlinLong?) -> Int64 { value?.int64Value ?? 0 }

    private func canAdd(_ mediaType: CardEvidenceMediaType) -> Bool {
        guard let type = state.selectedCardType else { return false }
        switch mediaType {
        case .image:
            return limit(type.quantityImagesCreate) > Int64(state.imageEvidenceCount)
        case .video:
            return limit(type.quantityVideosCreate) > Int64(state.videoEvidenceCount)
                && limit(type.videosDurationCreate) > 0
        case .audio:
            return limit(type.quantityAudiosCreate) > Int64(state.audioEvidenceCount)
                && limit(type.audiosDurationCreate) > 0
        default:
            return false
        }
    }

    private func limitText(current: Int64, maximum: Int64) -> String {
        String(
            format: String(localized: AppStrings.CreateCard.countLimit),
            locale: .current,
            current,
            maximum
        )
    }

    private func timedLimitText(current: Int64, maximum: Int64, duration: Int64) -> String {
        String(
            format: String(localized: AppStrings.CreateCard.timedCountLimit),
            locale: .current,
            current,
            maximum,
            duration
        )
    }

    private func icon(for mediaType: CardEvidenceMediaType) -> String {
        switch mediaType {
        case .image: "photo"
        case .video: "video"
        case .audio: "waveform"
        default: "paperclip"
        }
    }

    private func fileDetail(_ evidence: CreateCardEvidenceDraft) -> String {
        let size = ByteCountFormatter.string(fromByteCount: evidence.sizeBytes, countStyle: .file)
        guard evidence.durationMillis > 0 else { return size }
        let duration = Duration.seconds(Double(evidence.durationMillis) / 1_000)
            .formatted(.time(pattern: .minuteSecond))
        return "\(duration) · \(size)"
    }

    private func preferredType(from types: [UTType], mediaType: CardEvidenceMediaType) -> UTType {
        let expected: UTType = mediaType == .video ? .movie : .image
        return types.first(where: { $0.conforms(to: expected) }) ?? expected
    }

    private func defaultExtension(for mediaType: CardEvidenceMediaType) -> String {
        mediaType == .video ? "mov" : "jpg"
    }

    private func defaultMimeType(for mediaType: CardEvidenceMediaType) -> String {
        mediaType == .video ? "video/quicktime" : "image/jpeg"
    }

    private enum EvidenceImportError: Error { case missingData }
}

private struct PickedVideoFile: Transferable {
    let url: URL

    static var transferRepresentation: some TransferRepresentation {
        FileRepresentation(contentType: .movie) { video in
            SentTransferredFile(video.url)
        } importing: { received in
            let destination = FileManager.default.temporaryDirectory
                .appendingPathComponent("picked-\(UUID().uuidString).\(received.file.pathExtension.nonEmpty ?? "mov")")
            try FileManager.default.copyItem(at: received.file, to: destination)
            return PickedVideoFile(url: destination)
        }
    }
}

private extension String {
    var nonEmpty: String? { isEmpty ? nil : self }
}

private struct CardAudioRecorderSheet: View {
    let maximumSeconds: Int64
    let onRecorded: (URL) -> Void
    let onCancel: () -> Void

    @State private var recorder = CardAudioRecorder()
    @State private var errorMessage: String?

    var body: some View {
        NavigationStack {
            VStack(spacing: 24) {
                Image(systemName: recorder.isRecording ? "waveform.circle.fill" : "mic.circle.fill")
                    .font(.system(size: 72))
                    .foregroundStyle(Color.osmPrimary)
                    .symbolEffect(.pulse, isActive: recorder.isRecording)
                Text(timeText)
                    .font(.system(.title, design: .monospaced).weight(.semibold))
                Text(
                    String(
                        format: String(localized: AppStrings.CreateCard.audioMaximum),
                        locale: .current,
                        maximumSeconds
                    )
                )
                .font(.footnote)
                .foregroundStyle(Color.osmOnSurfaceVariant)

                if let errorMessage {
                    Text(errorMessage)
                        .font(.footnote)
                        .foregroundStyle(Color.osmError)
                }

                Button {
                    if recorder.isRecording || recorder.hasRecording {
                        if let url = recorder.stop() { onRecorded(url) }
                    } else {
                        Task {
                            do { try await recorder.start(maxSeconds: maximumSeconds) }
                            catch { errorMessage = error.localizedDescription }
                        }
                    }
                } label: {
                    Label(
                        recorder.isRecording
                            ? AppStrings.CreateCard.stopRecording
                            : recorder.hasRecording
                                ? AppStrings.CreateCard.attachRecording
                                : AppStrings.CreateCard.startRecording,
                        systemImage: recorder.isRecording ? "stop.fill" : recorder.hasRecording ? "paperclip" : "mic.fill"
                    )
                    .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
            }
            .padding(24)
            .navigationTitle(AppStrings.CreateCard.recordAudio)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(AppStrings.Common.cancel) {
                        recorder.cancel()
                        onCancel()
                    }
                }
            }
        }
        .interactiveDismissDisabled(recorder.isRecording)
    }

    private var timeText: String {
        let minutes = recorder.elapsedSeconds / 60
        let seconds = recorder.elapsedSeconds % 60
        return String(format: "%02lld:%02lld", minutes, seconds)
    }
}
