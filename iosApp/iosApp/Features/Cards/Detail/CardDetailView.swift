import AVFoundation
import AVKit
import Observation
import SharedLogic
import SwiftUI
import UIKit

private enum CardDetailTab: Hashable {
    case information
    case evidence
}

private struct EvidencePreview: Identifiable {
    enum Kind {
        case image
        case video
    }

    let id = UUID()
    let kind: Kind
    let path: String
}

struct CardDetailView: View {
    let uuid: String
    let siteNames: [Int64: String]
    @Bindable var viewModel: CardDetailViewModel

    @State private var selectedTab: CardDetailTab = .information
    @State private var preview: EvidencePreview?
    @State private var pendingVideoEvidenceID: String?

    var body: some View {
        Group {
            if viewModel.isLoading, viewModel.card == nil {
                ProgressView()
                    .tint(Color.osmPrimary)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(Color.osmBackground)
                    .accessibilityLabel(AppStrings.Accessibility.loading)
            } else if let card = viewModel.card {
                detailContent(card)
            } else {
                ContentUnavailableView(
                    AppStrings.CardDetail.notFound,
                    systemImage: "doc.text.magnifyingglass"
                )
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color.osmBackground)
            }
        }
        .navigationTitle(AppStrings.CardDetail.title)
        .navigationBarTitleDisplayMode(.inline)
        .task(id: uuid) { viewModel.start(uuid: uuid) }
        .onDisappear { viewModel.stop() }
        .onChange(of: viewModel.evidenceFiles.count) { _, _ in
            presentPendingVideoIfAvailable()
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

    private func detailContent(_ card: Card) -> some View {
        VStack(spacing: 0) {
            CardDetailIdentityHeader(card: card)

            Picker(AppStrings.CardDetail.title, selection: $selectedTab) {
                Text(AppStrings.CardDetail.informationTab).tag(CardDetailTab.information)
                Text(AppStrings.CardDetail.evidenceTab).tag(CardDetailTab.evidence)
            }
            .pickerStyle(.segmented)
            .padding(.horizontal, OSMSpacing.md)
            .padding(.bottom, OSMSpacing.sm)
            .onChange(of: selectedTab) { _, tab in
                if tab == .evidence { viewModel.prepareImageEvidence() }
            }

            switch selectedTab {
            case .information:
                CardInformationView(card: card, siteName: siteNames[card.siteId])
            case .evidence:
                CardEvidenceView(
                    evidences: card.evidences,
                    viewModel: viewModel,
                    onOpenImage: { evidence in open(evidence, as: .image) },
                    onOpenVideo: { evidence in open(evidence, as: .video) }
                )
            }
        }
        .background(Color.osmBackground)
    }

    private func open(_ evidence: CardEvidence, as kind: EvidencePreview.Kind) {
        if let path = viewModel.evidenceFiles[evidence.id] {
            preview = EvidencePreview(kind: kind, path: path)
            return
        }
        if kind == .video { pendingVideoEvidenceID = evidence.id }
        viewModel.resolve(evidence)
    }

    private func presentPendingVideoIfAvailable() {
        guard let id = pendingVideoEvidenceID,
              let path = viewModel.evidenceFiles[id] else { return }
        pendingVideoEvidenceID = nil
        preview = EvidencePreview(kind: .video, path: path)
    }
}

private struct CardDetailIdentityHeader: View {
    let card: Card

    var body: some View {
        VStack(alignment: .leading, spacing: OSMSpacing.xs) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: OSMSpacing.xs) {
                    CardDetailPill(
                        title: String(localized: card.isClosed
                            ? AppStrings.Cards.statusClosed
                            : AppStrings.Cards.statusOpen),
                        foreground: card.isClosed ? .osmOnSurfaceVariant : .osmPrimary,
                        background: card.isClosed ? .osmSurfaceVariant : .osmPrimaryContainer
                    )
                    if let priority = card.priorityDescription.detailNonEmpty {
                        CardDetailPill(
                            title: priority,
                            foreground: .osmWarning,
                            background: .osmWarningContainer
                        )
                    }
                    if card.isLocal || card.syncState != .synced {
                        CardDetailPill(
                            title: String(localized: AppStrings.Cards.pendingSync),
                            foreground: .osmError,
                            background: .osmErrorContainer
                        )
                    }
                }
            }

            Text(card.siteCardId > 0 ? "#\(card.siteCardId)" : String(localized: AppStrings.Cards.localFolio))
                .font(.title2.weight(.bold))
                .foregroundStyle(Color.osmOnSurface)

            Text(
                [card.cardTypeName.detailNonEmpty, card.nodeName.detailNonEmpty]
                    .compactMap { $0 }
                    .joined(separator: " · ")
            )
            .font(.subheadline)
            .foregroundStyle(Color.osmOnSurfaceVariant)
            .lineLimit(2)
        }
        .padding(.horizontal, OSMSpacing.md)
        .padding(.vertical, OSMSpacing.sm)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.osmBackground)
    }
}

private struct CardDetailPill: View {
    let title: String
    let foreground: Color
    let background: Color

    var body: some View {
        Text(title)
            .font(.caption2.weight(.semibold))
            .foregroundStyle(foreground)
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(background)
            .clipShape(Capsule())
    }
}

private struct CardInformationView: View {
    let card: Card
    let siteName: String?

    var body: some View {
        ScrollView {
            LazyVStack(spacing: OSMSpacing.sm) {
                ReadOnlyDetailSection(title: AppStrings.CardDetail.generalInformation) {
                    DetailValueRow(icon: "building.2", label: AppStrings.CardDetail.site, value: siteName ?? card.siteCode.detailValue)
                    DetailValueRow(icon: "mappin.and.ellipse", label: AppStrings.CardDetail.location, value: card.location.detailValue)
                    DetailValueRow(icon: "doc.text", label: AppStrings.CardDetail.type, value: card.cardTypeName.detailValue)
                    if let classification = card.cardTypeValue.detailNonEmpty {
                        DetailValueRow(label: AppStrings.CardDetail.classification, value: classification)
                    }
                    DetailValueRow(
                        icon: "tray.full",
                        label: AppStrings.CardDetail.preclassifier,
                        value: [card.preclassifierCode.detailNonEmpty, card.preclassifierDescription.detailNonEmpty]
                            .compactMap { $0 }.joined(separator: " · ").detailValue
                    )
                    DetailValueRow(
                        icon: "exclamationmark.circle",
                        label: AppStrings.CardDetail.priority,
                        value: [card.priorityCode.detailNonEmpty, card.priorityDescription.detailNonEmpty]
                            .compactMap { $0 }.joined(separator: " · ").detailValue
                    )
                    DetailValueRow(icon: "calendar", label: AppStrings.CardDetail.created, value: card.creationDate.detailDate)
                    DetailValueRow(icon: "calendar.badge.clock", label: AppStrings.CardDetail.due, value: card.dueDate.detailDate)
                    DetailValueRow(icon: "person", label: AppStrings.CardDetail.createdBy, value: card.creatorName.detailValue)
                    DetailValueRow(icon: "person.badge.clock", label: AppStrings.CardDetail.responsible, value: card.responsibleName.detailValue)
                    DetailValueRow(icon: "wrench.and.screwdriver", label: AppStrings.CardDetail.mechanic, value: card.mechanicName.detailValue)
                }

                ReadOnlyDetailSection(title: AppStrings.CardDetail.problemDescription) {
                    Text(card.comments.detailValue)
                        .font(.body)
                        .foregroundStyle(Color.osmOnSurfaceVariant)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }

                SolutionDetailSection(
                    title: AppStrings.CardDetail.provisionalSolution,
                    date: card.provisionalSolutionDate,
                    user: card.provisionalSolutionUserName,
                    comments: card.provisionalSolutionComments
                )

                SolutionDetailSection(
                    title: AppStrings.CardDetail.definitiveSolution,
                    date: card.definitiveSolutionDate,
                    user: card.definitiveSolutionUserName,
                    comments: card.definitiveSolutionComments
                )

                if card.managerName.detailNonEmpty != nil || card.managerComments.detailNonEmpty != nil {
                    SolutionDetailSection(
                        title: AppStrings.CardDetail.managerClose,
                        date: card.managerCloseDate,
                        user: card.managerName,
                        comments: card.managerComments
                    )
                }

                ReadOnlyDetailSection(title: AppStrings.CardDetail.deviceInformation) {
                    DetailValueRow(
                        icon: card.syncState == .synced ? "checkmark.icloud" : "icloud.slash",
                        label: AppStrings.CardDetail.syncStatus,
                        value: card.syncState.localizedTitle
                    )
                    DetailValueRow(
                        label: AppStrings.CardDetail.app,
                        value: [card.appSo.detailNonEmpty, card.appVersion.detailNonEmpty]
                            .compactMap { $0 }.joined(separator: " · ").detailValue
                    )
                    if let error = card.syncError.detailNonEmpty {
                        DetailValueRow(label: AppStrings.CardDetail.syncError, value: error, valueColor: .osmError)
                    }
                }
            }
            .padding(OSMSpacing.md)
        }
        .background(Color.osmBackground)
    }
}

private struct SolutionDetailSection: View {
    let title: LocalizedStringResource
    let date: String?
    let user: String?
    let comments: String?

    var body: some View {
        ReadOnlyDetailSection(title: title) {
            if date.detailNonEmpty == nil, user.detailNonEmpty == nil, comments.detailNonEmpty == nil {
                Text(AppStrings.CardDetail.noSolution)
                    .font(.body)
                    .foregroundStyle(Color.osmOnSurfaceVariant)
            } else {
                DetailValueRow(icon: "calendar", label: AppStrings.CardDetail.date, value: date.detailDate)
                DetailValueRow(icon: "person", label: AppStrings.CardDetail.user, value: user.detailValue)
                DetailValueRow(label: AppStrings.CardDetail.comments, value: comments.detailValue)
            }
        }
    }
}

private struct ReadOnlyDetailSection<Content: View>: View {
    let title: LocalizedStringResource
    @ViewBuilder let content: Content

    var body: some View {
        AnatomyCard(variant: .outlined) {
            VStack(alignment: .leading, spacing: OSMSpacing.xs) {
                Text(title)
                    .font(.headline)
                    .foregroundStyle(Color.osmOnSurface)
                content
            }
        }
    }
}

private struct DetailValueRow: View {
    var icon: String?
    let label: LocalizedStringResource
    let value: String
    var valueColor: Color = .osmOnSurface

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Group {
                if let icon {
                    Image(systemName: icon)
                        .foregroundStyle(Color.osmPrimary)
                } else {
                    Color.clear
                }
            }
            .frame(width: 18, height: 18)

            Text(label)
                .font(.caption)
                .foregroundStyle(Color.osmOnSurfaceVariant)
                .frame(maxWidth: .infinity, alignment: .leading)

            Text(value)
                .font(.caption.weight(.medium))
                .foregroundStyle(valueColor)
                .multilineTextAlignment(.trailing)
                .frame(maxWidth: .infinity, alignment: .trailing)
        }
        .padding(.vertical, 5)
        .overlay(alignment: .bottom) {
            Divider().opacity(0.55)
        }
    }
}

private struct CardEvidenceView: View {
    let evidences: [CardEvidence]
    @Bindable var viewModel: CardDetailViewModel
    let onOpenImage: (CardEvidence) -> Void
    let onOpenVideo: (CardEvidence) -> Void

    var body: some View {
        ScrollView {
            LazyVStack(spacing: OSMSpacing.sm) {
                ForEach(CardEvidenceStage.detailStages, id: \.self) { stage in
                    EvidenceStageSection(
                        stage: stage,
                        evidences: evidences.filter { $0.stage == stage },
                        viewModel: viewModel,
                        onOpenImage: onOpenImage,
                        onOpenVideo: onOpenVideo
                    )
                }
            }
            .padding(OSMSpacing.md)
        }
        .background(Color.osmBackground)
        .task { viewModel.prepareImageEvidence() }
    }
}

private struct EvidenceStageSection: View {
    let stage: CardEvidenceStage
    let evidences: [CardEvidence]
    @Bindable var viewModel: CardDetailViewModel
    let onOpenImage: (CardEvidence) -> Void
    let onOpenVideo: (CardEvidence) -> Void

    private var images: [CardEvidence] { evidences.filter { $0.mediaType == .image } }
    private var videos: [CardEvidence] { evidences.filter { $0.mediaType == .video } }
    private var audios: [CardEvidence] { evidences.filter { $0.mediaType == .audio } }

    var body: some View {
        ReadOnlyDetailSection(title: stage.localizedTitle) {
            if evidences.isEmpty {
                Text(AppStrings.CardDetail.noEvidence)
                    .font(.body)
                    .foregroundStyle(Color.osmOnSurfaceVariant)
            } else {
                if !images.isEmpty {
                    MediaHeading(systemImage: "photo", title: AppStrings.CardDetail.images, count: images.count)
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 120), spacing: 8)], spacing: 8) {
                        ForEach(images, id: \.id) { evidence in
                            EvidenceImageThumbnail(
                                evidence: evidence,
                                path: viewModel.evidenceFiles[evidence.id],
                                isLoading: viewModel.loadingEvidenceIDs.contains(evidence.id),
                                hasFailed: viewModel.failedEvidenceIDs.contains(evidence.id),
                                action: { onOpenImage(evidence) }
                            )
                        }
                    }
                }

                if !videos.isEmpty {
                    MediaHeading(systemImage: "video", title: AppStrings.CardDetail.videos, count: videos.count)
                        .padding(.top, OSMSpacing.xs)
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 120), spacing: 8)], spacing: 8) {
                        ForEach(videos, id: \.id) { evidence in
                            EvidenceVideoThumbnail(
                                isLoading: viewModel.loadingEvidenceIDs.contains(evidence.id),
                                hasFailed: viewModel.failedEvidenceIDs.contains(evidence.id),
                                action: { onOpenVideo(evidence) }
                            )
                        }
                    }
                }

                if !audios.isEmpty {
                    MediaHeading(systemImage: "waveform", title: AppStrings.CardDetail.audios, count: audios.count)
                        .padding(.top, OSMSpacing.xs)
                    ForEach(audios, id: \.id) { evidence in
                        EvidenceAudioRow(
                            evidence: evidence,
                            path: viewModel.evidenceFiles[evidence.id],
                            isLoading: viewModel.loadingEvidenceIDs.contains(evidence.id),
                            hasFailed: viewModel.failedEvidenceIDs.contains(evidence.id),
                            onResolve: { viewModel.resolve(evidence) }
                        )
                    }
                }
            }
        }
    }
}

private struct MediaHeading: View {
    let systemImage: String
    let title: LocalizedStringResource
    let count: Int

    var body: some View {
        Label {
            Text(title) + Text(" · \(count)")
        } icon: {
            Image(systemName: systemImage)
        }
        .font(.subheadline.weight(.semibold))
        .foregroundStyle(Color.osmPrimary)
    }
}

private struct EvidenceImageThumbnail: View {
    let evidence: CardEvidence
    let path: String?
    let isLoading: Bool
    let hasFailed: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            ZStack {
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(Color.osmSurfaceVariant)
                if let path, let image = UIImage(contentsOfFile: path) {
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFill()
                } else {
                    Image(systemName: "photo")
                        .font(.title2)
                        .foregroundStyle(Color.osmOnSurfaceVariant)
                }
                EvidenceLoadingOverlay(isLoading: isLoading, hasFailed: hasFailed)
            }
            .aspectRatio(1.35, contentMode: .fit)
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(AppStrings.CardDetail.imageDescription)
    }
}

private struct EvidenceVideoThumbnail: View {
    let isLoading: Bool
    let hasFailed: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            ZStack {
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(Color.osmSurfaceVariant)
                Image(systemName: "video")
                    .font(.title)
                    .foregroundStyle(Color.osmOnSurfaceVariant)
                if !isLoading, !hasFailed {
                    Image(systemName: "play.fill")
                        .font(.headline)
                        .foregroundStyle(Color.osmOnPrimary)
                        .padding(12)
                        .background(Color.osmPrimary)
                        .clipShape(Circle())
                }
                EvidenceLoadingOverlay(isLoading: isLoading, hasFailed: hasFailed)
            }
            .aspectRatio(1.5, contentMode: .fit)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(AppStrings.CardDetail.playVideo)
    }
}

private struct EvidenceLoadingOverlay: View {
    let isLoading: Bool
    let hasFailed: Bool

    var body: some View {
        Group {
            if isLoading {
                ProgressView().tint(Color.osmPrimary)
            } else if hasFailed {
                Text(AppStrings.CardDetail.retryEvidence)
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(Color.osmError)
                    .padding(6)
                    .background(.ultraThinMaterial)
                    .clipShape(Capsule())
            }
        }
    }
}

private struct EvidenceAudioRow: View {
    let evidence: CardEvidence
    let path: String?
    let isLoading: Bool
    let hasFailed: Bool
    let onResolve: () -> Void

    @State private var player = EvidenceAudioPlayer()

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: OSMSpacing.xs) {
                Button {
                    guard let path else {
                        onResolve()
                        return
                    }
                    player.prepareIfNeeded(path: path)
                    player.togglePlayback()
                } label: {
                    Image(systemName: player.isPlaying ? "pause.fill" : "play.fill")
                        .frame(width: 36, height: 36)
                        .foregroundStyle(Color.osmOnPrimary)
                        .background(Color.osmPrimary)
                        .clipShape(Circle())
                }
                .buttonStyle(.plain)
                .disabled(isLoading)

                VStack(alignment: .leading, spacing: 2) {
                    Text(URL(fileURLWithPath: evidence.url).lastPathComponent.detailValue)
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
                    .disabled(path == nil || player.duration <= 0)
                    .tint(Color.osmPrimary)
                }

                Text("\(player.currentTime.durationLabel) / \(player.duration.durationLabel)")
                    .font(.caption2.monospacedDigit())
                    .foregroundStyle(Color.osmOnSurfaceVariant)
            }

            if isLoading {
                ProgressView().tint(Color.osmPrimary).frame(maxWidth: .infinity)
            } else if hasFailed || player.hasFailed {
                Button(AppStrings.CardDetail.audioError, action: onResolve)
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
        .onChange(of: path) { _, newPath in
            if let newPath { player.prepareIfNeeded(path: newPath) }
        }
        .onDisappear { player.stop() }
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
                ContentUnavailableView(AppStrings.CardDetail.evidenceUnavailable, systemImage: "photo.badge.exclamationmark")
                    .foregroundStyle(.white)
            }
        }
        .overlay(alignment: .topTrailing) {
            EvidenceCloseButton(action: onClose)
                .safeAreaPadding(.top, 8)
                .safeAreaPadding(.trailing, 12)
        }
        .statusBarHidden(false)
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
        .statusBarHidden(false)
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

private extension CardEvidenceStage {
    static let detailStages: [CardEvidenceStage] = [.creation, .provisionalSolution, .definitiveSolution]

    var localizedTitle: LocalizedStringResource {
        switch self {
        case .creation: AppStrings.CardDetail.creationEvidence
        case .provisionalSolution: AppStrings.CardDetail.provisionalEvidence
        case .definitiveSolution: AppStrings.CardDetail.definitiveEvidence
        default: AppStrings.CardDetail.evidenceTab
        }
    }
}

private extension CardSyncState {
    var localizedTitle: String {
        switch self {
        case .pending: String(localized: AppStrings.CardDetail.syncPending)
        case .syncing: String(localized: AppStrings.CardDetail.syncing)
        case .failed: String(localized: AppStrings.CardDetail.syncFailed)
        case .synced: String(localized: AppStrings.CardDetail.synced)
        default: String(localized: AppStrings.CardDetail.syncPending)
        }
    }
}

private extension Optional where Wrapped == String {
    var detailNonEmpty: String? {
        self?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false ? self : nil
    }

    var detailValue: String { detailNonEmpty ?? "—" }

    var detailDate: String { detailNonEmpty?.detailDate ?? "—" }
}

private extension String {
    var detailNonEmpty: String? {
        trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : self
    }

    var detailValue: String { detailNonEmpty ?? "—" }

    var detailDate: String {
        guard let value = detailNonEmpty else { return "—" }
        return String(value.replacingOccurrences(of: "T", with: " ").replacingOccurrences(of: "Z", with: "").prefix(16))
    }
}

private extension Double {
    var durationLabel: String {
        guard isFinite, self > 0 else { return "00:00" }
        let total = Int(self)
        return String(format: "%02d:%02d", total / 60, total % 60)
    }
}
