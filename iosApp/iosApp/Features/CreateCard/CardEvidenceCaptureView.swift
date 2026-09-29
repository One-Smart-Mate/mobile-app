import SharedLogic
import SwiftUI
import UIKit

struct CardEvidenceCaptureView: View {
    private let evidences: [CreateCardEvidenceDraft]
    private let limits: CardEvidenceCaptureLimits
    private let isProcessingEvidence: Bool
    private let addEvidence: (PreparedCardEvidence) async -> Void
    private let removeEvidence: (String) async -> Void
    private let evidenceImportFailed: () -> Void

    @State private var requestedMedia: CardEvidenceMediaType?
    @State private var showCamera = false
    @State private var showAudioRecorder = false
    @State private var isImporting = false

    private let storage = CardEvidenceStorage.shared

    init(state: CreateCardState, viewModel: CreateCardViewModel) {
        evidences = state.evidences
        limits = CardEvidenceCaptureLimits(
            images: state.selectedCardType?.quantityImagesCreate?.int64Value ?? 0,
            videos: state.selectedCardType?.quantityVideosCreate?.int64Value ?? 0,
            audios: state.selectedCardType?.quantityAudiosCreate?.int64Value ?? 0,
            videoDurationSeconds: state.selectedCardType?.videosDurationCreate?.int64Value ?? 0,
            audioDurationSeconds: state.selectedCardType?.audiosDurationCreate?.int64Value ?? 0
        )
        isProcessingEvidence = state.isProcessingEvidence
        addEvidence = { await viewModel.addEvidence($0) }
        removeEvidence = { await viewModel.removeEvidence($0) }
        evidenceImportFailed = viewModel.evidenceImportFailed
    }

    init(state: CardSolutionState, viewModel: CardSolutionViewModel) {
        evidences = state.evidences
        limits = CardEvidenceCaptureLimits(
            images: state.evidenceLimits.images,
            videos: state.evidenceLimits.videos,
            audios: state.evidenceLimits.audios,
            videoDurationSeconds: state.evidenceLimits.videoDurationSeconds,
            audioDurationSeconds: state.evidenceLimits.audioDurationSeconds
        )
        isProcessingEvidence = state.isProcessingEvidence
        addEvidence = { await viewModel.addEvidence($0) }
        removeEvidence = { await viewModel.removeEvidence($0) }
        evidenceImportFailed = viewModel.evidenceImportFailed
    }

    var body: some View {
        VStack(spacing: 12) {
            evidenceAction(
                title: AppStrings.CreateCard.photos,
                subtitle: limitText(
                    current: evidenceCount(.image),
                    maximum: limits.images
                ),
                systemImage: "camera",
                mediaType: .image,
                enabled: canAdd(.image)
            )
            evidenceAction(
                title: AppStrings.CreateCard.videos,
                subtitle: timedLimitText(
                    current: evidenceCount(.video),
                    maximum: limits.videos,
                    duration: limits.videoDurationSeconds
                ),
                systemImage: "video",
                mediaType: .video,
                enabled: canAdd(.video)
            )
            evidenceAction(
                title: AppStrings.CreateCard.audio,
                subtitle: timedLimitText(
                    current: evidenceCount(.audio),
                    maximum: limits.audios,
                    duration: limits.audioDurationSeconds
                ),
                systemImage: "waveform",
                mediaType: .audio,
                enabled: canAdd(.audio)
            )

            if !evidences.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    Text(AppStrings.CreateCard.attachedEvidence)
                        .font(.subheadline.weight(.semibold))
                    ForEach(evidences, id: \.id) { evidence in
                        evidenceRow(evidence)
                    }
                }
                .padding(.top, 4)
            }

            if isImporting || isProcessingEvidence {
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
        .fullScreenCover(isPresented: $showCamera) {
            if let requestedMedia {
                CardCameraPicker(
                    mediaType: requestedMedia,
                    maximumVideoDuration: TimeInterval(
                        limits.videoDurationSeconds
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
                maximumSeconds: limits.audioDurationSeconds,
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
            } else if UIImagePickerController.isSourceTypeAvailable(.camera) {
                showCamera = true
            } else {
                evidenceImportFailed()
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
        .disabled(!enabled || isImporting || isProcessingEvidence)
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
                Task { await removeEvidence(evidence.id) }
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

    private func importFile(_ url: URL, as mediaType: CardEvidenceMediaType, removeSource: Bool) async {
        await MainActor.run { isImporting = true }
        defer {
            if removeSource { try? FileManager.default.removeItem(at: url) }
            Task { @MainActor in isImporting = false }
        }
        do {
            let prepared = try await storage.importFile(from: url, mediaType: mediaType)
            await addEvidence(prepared)
        } catch {
            evidenceImportFailed()
        }
    }

    private func canAdd(_ mediaType: CardEvidenceMediaType) -> Bool {
        switch mediaType {
        case .image:
            return limits.images > evidenceCount(.image)
        case .video:
            return limits.videos > evidenceCount(.video)
                && limits.videoDurationSeconds > 0
        case .audio:
            return limits.audios > evidenceCount(.audio)
                && limits.audioDurationSeconds > 0
        default:
            return false
        }
    }

    private func evidenceCount(_ mediaType: CardEvidenceMediaType) -> Int64 {
        Int64(evidences.filter { $0.mediaType == mediaType }.count)
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

}

private struct CardEvidenceCaptureLimits {
    let images: Int64
    let videos: Int64
    let audios: Int64
    let videoDurationSeconds: Int64
    let audioDurationSeconds: Int64
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
