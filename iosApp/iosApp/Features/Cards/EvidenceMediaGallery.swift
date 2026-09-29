import AVFoundation
import AVKit
import Observation
import SharedLogic
import SwiftUI
import UIKit

struct EvidenceMediaItem: Identifiable {
    let id: String
    let path: String?
    let displayName: String
    let mediaType: CardEvidenceMediaType
    var sizeBytes: Int64 = 0
    var durationMillis: Int64 = 0
    var isLoading = false
    var hasFailed = false
}

extension CreateCardEvidenceDraft {
    var mediaGalleryItem: EvidenceMediaItem {
        EvidenceMediaItem(
            id: id,
            path: localPath,
            displayName: displayName,
            mediaType: mediaType,
            sizeBytes: sizeBytes,
            durationMillis: durationMillis
        )
    }
}

struct EvidenceMediaGallery: View {
    let items: [EvidenceMediaItem]
    let isOnlyRead: Bool
    var onDelete: ((EvidenceMediaItem) -> Void)?
    var onRequestSource: ((EvidenceMediaItem) -> Void)?

    @State private var preview: EvidenceMediaPreview?
    @State private var pendingPreview: PendingEvidencePreview?

    private var images: [EvidenceMediaItem] { items.filter { $0.mediaType == .image } }
    private var videos: [EvidenceMediaItem] { items.filter { $0.mediaType == .video } }
    private var audios: [EvidenceMediaItem] { items.filter { $0.mediaType == .audio } }

    var body: some View {
        VStack(alignment: .leading, spacing: OSMSpacing.xs) {
            if !images.isEmpty {
                mediaHeading(systemImage: "photo", title: AppStrings.CardDetail.images, count: images.count)
                mediaGrid(items: images, kind: .image)
            }

            if !videos.isEmpty {
                mediaHeading(systemImage: "video", title: AppStrings.CardDetail.videos, count: videos.count)
                    .padding(.top, images.isEmpty ? 0 : OSMSpacing.xs)
                mediaGrid(items: videos, kind: .video)
            }

            if !audios.isEmpty {
                mediaHeading(systemImage: "waveform", title: AppStrings.CardDetail.audios, count: audios.count)
                    .padding(.top, images.isEmpty && videos.isEmpty ? 0 : OSMSpacing.xs)
                ForEach(audios) { item in
                    EvidenceAudioPlayerRow(
                        item: item,
                        isOnlyRead: isOnlyRead,
                        onDelete: { onDelete?(item) },
                        onRequestSource: { onRequestSource?(item) }
                    )
                }
            }
        }
        .onChange(of: availablePaths) { _, paths in
            guard let pendingPreview,
                  let path = paths[pendingPreview.id] else { return }
            self.pendingPreview = nil
            preview = EvidenceMediaPreview(kind: pendingPreview.kind, path: path)
        }
        .fullScreenCover(item: $preview) { preview in
            switch preview.kind {
            case .image:
                FullScreenEvidenceImage(path: preview.path) { self.preview = nil }
            case .video:
                FullScreenEvidenceVideo(path: preview.path) { self.preview = nil }
            }
        }
    }

    private var availablePaths: [String: String] {
        Dictionary(uniqueKeysWithValues: items.compactMap { item in
            item.path.map { (item.id, $0) }
        })
    }

    private func mediaHeading(
        systemImage: String,
        title: LocalizedStringResource,
        count: Int
    ) -> some View {
        Label {
            Text(title) + Text(" · \(count)")
        } icon: {
            Image(systemName: systemImage)
        }
        .font(.subheadline.weight(.semibold))
        .foregroundStyle(Color.osmPrimary)
    }

    private func mediaGrid(
        items: [EvidenceMediaItem],
        kind: EvidenceMediaPreview.Kind
    ) -> some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 120), spacing: 8)], spacing: 8) {
            ForEach(items) { item in
                EvidenceVisualThumbnail(
                    item: item,
                    isOnlyRead: isOnlyRead,
                    onOpen: { open(item, as: kind) },
                    onDelete: { onDelete?(item) }
                )
            }
        }
    }

    private func open(_ item: EvidenceMediaItem, as kind: EvidenceMediaPreview.Kind) {
        if let path = item.path {
            preview = EvidenceMediaPreview(kind: kind, path: path)
        } else {
            pendingPreview = PendingEvidencePreview(id: item.id, kind: kind)
            onRequestSource?(item)
        }
    }
}

private struct EvidenceMediaPreview: Identifiable {
    enum Kind {
        case image
        case video
    }

    let id = UUID()
    let kind: Kind
    let path: String
}

private struct PendingEvidencePreview {
    let id: String
    let kind: EvidenceMediaPreview.Kind
}

private struct EvidenceVisualThumbnail: View {
    let item: EvidenceMediaItem
    let isOnlyRead: Bool
    let onOpen: () -> Void
    let onDelete: () -> Void

    var body: some View {
        ZStack(alignment: .topLeading) {
            Button(action: onOpen) {
                ZStack {
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(Color.osmSurfaceVariant)

                    if item.mediaType == .image,
                       let path = item.path,
                       let image = UIImage(contentsOfFile: path) {
                        Image(uiImage: image)
                            .resizable()
                            .scaledToFill()
                    } else if item.mediaType == .video, let path = item.path {
                        EvidenceVideoFrame(path: path)
                    } else {
                        Image(systemName: item.mediaType == .video ? "video" : "photo")
                            .font(.title2)
                            .foregroundStyle(Color.osmOnSurfaceVariant)
                    }

                    if item.mediaType == .video, !item.isLoading, !item.hasFailed {
                        Image(systemName: "play.fill")
                            .font(.headline)
                            .foregroundStyle(Color.osmOnPrimary)
                            .padding(12)
                            .background(Color.osmPrimary)
                            .clipShape(Circle())
                    }

                    if item.isLoading {
                        ProgressView().tint(Color.osmPrimary)
                    } else if item.hasFailed {
                        Text(AppStrings.CardDetail.retryEvidence)
                            .font(.caption2.weight(.semibold))
                            .foregroundStyle(Color.osmError)
                            .padding(6)
                            .background(.ultraThinMaterial)
                            .clipShape(Capsule())
                    }
                }
                .aspectRatio(item.mediaType == .video ? 1.5 : 1.35, contentMode: .fit)
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            .buttonStyle(.plain)
            .accessibilityLabel(
                item.mediaType == .video
                    ? AppStrings.CardDetail.playVideo
                    : AppStrings.CardDetail.imageDescription
            )

            if !isOnlyRead {
                deleteButton
                    .padding(7)
            }
        }
    }

    private var deleteButton: some View {
        Button(role: .destructive, action: onDelete) {
            Image(systemName: "trash.fill")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.white)
                .frame(width: 34, height: 34)
                .background(Color.osmError.opacity(0.92))
                .clipShape(Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(AppStrings.CreateCard.removeEvidence)
    }
}

private struct EvidenceVideoFrame: UIViewRepresentable {
    let path: String

    func makeUIView(context: Context) -> EvidenceVideoFrameView {
        EvidenceVideoFrameView()
    }

    func updateUIView(_ view: EvidenceVideoFrameView, context: Context) {
        view.load(path: path)
    }

    static func dismantleUIView(_ view: EvidenceVideoFrameView, coordinator: Void) {
        view.stop()
    }
}

private final class EvidenceVideoFrameView: UIView {
    override class var layerClass: AnyClass { AVPlayerLayer.self }

    private var loadedPath: String?
    private var playerLayer: AVPlayerLayer { layer as! AVPlayerLayer }

    override init(frame: CGRect) {
        super.init(frame: frame)
        playerLayer.videoGravity = .resizeAspectFill
        clipsToBounds = true
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        playerLayer.videoGravity = .resizeAspectFill
        clipsToBounds = true
    }

    func load(path: String) {
        guard loadedPath != path else { return }
        loadedPath = path
        let player = AVPlayer(url: URL(fileURLWithPath: path))
        player.isMuted = true
        playerLayer.player = player
        player.seek(to: CMTime(seconds: 0.1, preferredTimescale: 600))
        player.pause()
    }

    func stop() {
        playerLayer.player?.pause()
        playerLayer.player = nil
        loadedPath = nil
    }
}

private struct EvidenceAudioPlayerRow: View {
    let item: EvidenceMediaItem
    let isOnlyRead: Bool
    let onDelete: () -> Void
    let onRequestSource: () -> Void

    @State private var player = EvidenceAudioPlayer()
    @State private var shouldPlayWhenAvailable = false

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: OSMSpacing.xs) {
                Button(action: togglePlayback) {
                    Image(systemName: player.isPlaying ? "pause.fill" : "play.fill")
                        .frame(width: 36, height: 36)
                        .foregroundStyle(Color.osmOnPrimary)
                        .background(Color.osmPrimary)
                        .clipShape(Circle())
                }
                .buttonStyle(.plain)
                .disabled(item.isLoading)

                VStack(alignment: .leading, spacing: 2) {
                    Text(item.displayName.isEmpty ? "—" : item.displayName)
                        .font(.caption.weight(.medium))
                        .foregroundStyle(Color.osmOnSurface)
                        .lineLimit(1)

                    Slider(
                        value: Binding(
                            get: { player.currentTime },
                            set: { player.seek(to: $0) }
                        ),
                        in: 0...max(player.duration, 1)
                    )
                    .disabled(item.path == nil || player.duration <= 0)
                    .tint(Color.osmPrimary)
                }

                Text("\(player.currentTime.durationLabel) / \(player.duration.durationLabel)")
                    .font(.caption2.monospacedDigit())
                    .foregroundStyle(Color.osmOnSurfaceVariant)

                if !isOnlyRead {
                    Button(role: .destructive, action: onDelete) {
                        Image(systemName: "trash")
                            .frame(width: 36, height: 36)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(AppStrings.CreateCard.removeEvidence)
                }
            }

            if item.isLoading {
                ProgressView().tint(Color.osmPrimary).frame(maxWidth: .infinity)
            } else if item.hasFailed || player.hasFailed {
                Button(AppStrings.CardDetail.audioError, action: onRequestSource)
                    .font(.caption)
                    .foregroundStyle(Color.osmError)
            }
        }
        .padding(10)
        .background(Color.osmSurface)
        .overlay {
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(Color.osmOutlineVariant, lineWidth: 1)
        }
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .onChange(of: item.path) { _, newPath in
            guard let newPath else { return }
            player.prepareIfNeeded(path: newPath)
            if shouldPlayWhenAvailable {
                shouldPlayWhenAvailable = false
                player.togglePlayback()
            }
        }
        .onDisappear { player.stop() }
    }

    private func togglePlayback() {
        guard let path = item.path else {
            shouldPlayWhenAvailable = true
            onRequestSource()
            return
        }
        player.prepareIfNeeded(path: path)
        player.togglePlayback()
    }
}

@MainActor
@Observable
private final class EvidenceAudioPlayer {
    private(set) var isPlaying = false
    private(set) var currentTime: Double = 0
    private(set) var duration: Double = 0
    private(set) var hasFailed = false

    private var player: AVPlayer?
    private var loadedPath: String?
    private var timeObserver: Any?
    private var endObserver: NSObjectProtocol?

    func prepareIfNeeded(path: String) {
        guard loadedPath != path else { return }
        stop()
        loadedPath = path
        hasFailed = false
        let item = AVPlayerItem(url: URL(fileURLWithPath: path))
        let player = AVPlayer(playerItem: item)
        self.player = player
        duration = 0
        Task { [weak self] in
            let loadedDuration = try? await item.asset.load(.duration)
            let seconds = loadedDuration?.seconds ?? 0
            if seconds.isFinite, seconds > 0 { self?.duration = seconds }
        }
        timeObserver = player.addPeriodicTimeObserver(
            forInterval: CMTime(seconds: 0.25, preferredTimescale: 600),
            queue: .main
        ) { [weak self, weak item] time in
            Task { @MainActor in
                guard let self else { return }
                self.currentTime = time.seconds.isFinite ? time.seconds : 0
                let itemDuration = item?.duration.seconds ?? 0
                if itemDuration.isFinite, itemDuration > 0 { self.duration = itemDuration }
            }
        }
        endObserver = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime,
            object: item,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor in
                self?.isPlaying = false
                self?.currentTime = 0
                self?.player?.seek(to: .zero)
            }
        }
    }

    func togglePlayback() {
        guard let player else {
            hasFailed = true
            return
        }
        if isPlaying {
            player.pause()
        } else {
            player.play()
        }
        isPlaying.toggle()
    }

    func seek(to seconds: Double) {
        currentTime = seconds
        player?.seek(to: CMTime(seconds: seconds, preferredTimescale: 600))
    }

    func stop() {
        player?.pause()
        if let timeObserver, let player { player.removeTimeObserver(timeObserver) }
        if let endObserver { NotificationCenter.default.removeObserver(endObserver) }
        player = nil
        timeObserver = nil
        endObserver = nil
        loadedPath = nil
        isPlaying = false
        currentTime = 0
        duration = 0
    }
}

private struct FullScreenEvidenceImage: View {
    let path: String
    let onClose: () -> Void

    @State private var scale: CGFloat = 1
    @State private var lastScale: CGFloat = 1
    @State private var offset: CGSize = .zero
    @State private var lastOffset: CGSize = .zero

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            if let image = UIImage(contentsOfFile: path) {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFit()
                    .scaleEffect(scale)
                    .offset(offset)
                    .gesture(
                        MagnifyGesture()
                            .onChanged { value in scale = min(max(lastScale * value.magnification, 1), 5) }
                            .onEnded { _ in
                                lastScale = scale
                                if scale == 1 { offset = .zero; lastOffset = .zero }
                            }
                            .simultaneously(with:
                                DragGesture()
                                    .onChanged { value in
                                        guard scale > 1 else { return }
                                        offset = CGSize(
                                            width: lastOffset.width + value.translation.width,
                                            height: lastOffset.height + value.translation.height
                                        )
                                    }
                                    .onEnded { _ in lastOffset = offset }
                            )
                    )
            } else {
                ContentUnavailableView(
                    AppStrings.CardDetail.evidenceUnavailable,
                    systemImage: "photo.badge.exclamationmark"
                )
                .foregroundStyle(.white)
            }
        }
        .overlay(alignment: .topTrailing) {
            EvidenceCloseButton(action: onClose)
                .safeAreaPadding(.top, 8)
                .safeAreaPadding(.trailing, 12)
        }
    }
}

private struct FullScreenEvidenceVideo: View {
    let path: String
    let onClose: () -> Void

    @State private var player: AVPlayer?

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            if let player {
                VideoPlayer(player: player)
                    .ignoresSafeArea(edges: .horizontal)
            } else {
                ProgressView().tint(.white)
            }
        }
        .overlay(alignment: .topTrailing) {
            EvidenceCloseButton(action: onClose)
                .safeAreaPadding(.top, 8)
                .safeAreaPadding(.trailing, 12)
        }
        .onAppear {
            let player = AVPlayer(url: URL(fileURLWithPath: path))
            self.player = player
            player.play()
        }
        .onDisappear {
            player?.pause()
            player = nil
        }
    }
}

private struct EvidenceCloseButton: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: "xmark")
                .font(.headline.weight(.semibold))
                .foregroundStyle(.white)
                .frame(width: 44, height: 44)
                .background(.black.opacity(0.65))
                .clipShape(Circle())
        }
        .accessibilityLabel(AppStrings.Accessibility.dismiss)
    }
}

private extension Double {
    var durationLabel: String {
        guard isFinite, self > 0 else { return "00:00" }
        let total = Int(self)
        return String(format: "%02d:%02d", total / 60, total % 60)
    }
}
