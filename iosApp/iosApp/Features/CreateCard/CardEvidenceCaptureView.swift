import AVFoundation
import OSLog
import SharedLogic
import SwiftUI
import UIKit

private let evidenceCaptureLogger = Logger(
    subsystem: Bundle.main.bundleIdentifier ?? "com.ih.osm",
    category: "EvidenceCamera"
)

private struct CameraPresentation: Identifiable {
    let id = UUID()
    let mediaType: CardEvidenceMediaType
}

struct CardEvidenceCaptureView: View {
    private let evidences: [CreateCardEvidenceDraft]
    private let limits: CardEvidenceCaptureLimits
    private let isProcessingEvidence: Bool
    private let addEvidence: (PreparedCardEvidence) async -> Void
    private let removeEvidence: (String) async -> Void
    private let evidenceImportFailed: () -> Void

    @State private var cameraPresentation: CameraPresentation?
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
                    EvidenceMediaGallery(
                        items: evidences.map(\.mediaGalleryItem),
                        isOnlyRead: false,
                        onDelete: { evidence in
                            Task { await removeEvidence(evidence.id) }
                        }
                    )
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
        .fullScreenCover(item: $cameraPresentation) { presentation in
            ZStack(alignment: .topTrailing) {
                CardCameraPicker(
                    mediaType: presentation.mediaType,
                    maximumVideoDuration: TimeInterval(
                        limits.videoDurationSeconds
                    ),
                    onPicked: { url in
                        evidenceCaptureLogger.notice(
                            "Camera returned media at \(url.lastPathComponent, privacy: .public)"
                        )
                        cameraPresentation = nil
                        Task {
                            await importFile(
                                url,
                                as: presentation.mediaType,
                                removeSource: true
                            )
                        }
                    },
                    onCancel: {
                        evidenceCaptureLogger.notice("Camera picker requested dismissal")
                        cameraPresentation = nil
                    }
                )
                .ignoresSafeArea()

                Button {
                    evidenceCaptureLogger.notice("Camera dismissed with diagnostic close button")
                    cameraPresentation = nil
                } label: {
                    Image(systemName: "xmark")
                        .font(.headline.weight(.semibold))
                        .foregroundStyle(.white)
                        .frame(width: 44, height: 44)
                        .background(.black.opacity(0.72))
                        .clipShape(Circle())
                }
                .padding(.top, 12)
                .padding(.trailing, 12)
                .zIndex(10)
                .accessibilityLabel(AppStrings.Accessibility.dismiss)
            }
            .onAppear {
                evidenceCaptureLogger.notice(
                    "Camera full-screen cover appeared id=\(presentation.id.uuidString, privacy: .public) media=\(String(describing: presentation.mediaType), privacy: .public)"
                )
            }
            .onDisappear {
                evidenceCaptureLogger.notice("Camera full-screen cover disappeared")
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
            if mediaType == .audio {
                showAudioRecorder = true
            } else {
                Task { @MainActor in
                    await presentCamera(for: mediaType)
                }
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

    @MainActor
    private func presentCamera(for mediaType: CardEvidenceMediaType) async {
        let sourceAvailable = UIImagePickerController.isSourceTypeAvailable(.camera)
        let authorization = AVCaptureDevice.authorizationStatus(for: .video)
        let applicationState = UIApplication.shared.applicationState.rawValue
        let availableTypes = UIImagePickerController.availableMediaTypes(for: .camera) ?? []

        evidenceCaptureLogger.notice(
            "Camera requested media=\(String(describing: mediaType), privacy: .public) sourceAvailable=\(sourceAvailable) authorization=\(authorization.rawValue) appState=\(applicationState) availableTypes=\(availableTypes.joined(separator: ","), privacy: .public)"
        )

        guard sourceAvailable else {
            evidenceCaptureLogger.error("Camera source is unavailable")
            evidenceImportFailed()
            return
        }

        let isAuthorized: Bool
        switch authorization {
        case .authorized:
            isAuthorized = true
        case .notDetermined:
            evidenceCaptureLogger.notice("Requesting camera permission before presentation")
            isAuthorized = await AVCaptureDevice.requestAccess(for: .video)
            evidenceCaptureLogger.notice("Camera permission result granted=\(isAuthorized)")
        case .denied, .restricted:
            isAuthorized = false
        @unknown default:
            isAuthorized = false
        }

        guard isAuthorized else {
            evidenceCaptureLogger.error(
                "Camera presentation blocked; authorization=\(AVCaptureDevice.authorizationStatus(for: .video).rawValue)"
            )
            evidenceImportFailed()
            return
        }

        evidenceCaptureLogger.notice("Camera validated; waiting for the initiating gesture to finish")
        do {
            try await Task.sleep(for: .milliseconds(150))
        } catch {
            evidenceCaptureLogger.error("Camera presentation task was cancelled before presentation")
            return
        }

        guard UIApplication.shared.applicationState == .active else {
            evidenceCaptureLogger.error(
                "Camera presentation blocked because appState=\(UIApplication.shared.applicationState.rawValue)"
            )
            evidenceImportFailed()
            return
        }

        let presentation = CameraPresentation(mediaType: mediaType)
        evidenceCaptureLogger.notice(
            "Presenting camera full-screen cover id=\(presentation.id.uuidString, privacy: .public)"
        )
        cameraPresentation = presentation
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
