import SharedLogic
import SwiftUI

struct OplCard: View {
    let opl: Opl
    let isDownloaded: Bool
    let onOpen: () -> Void

    var body: some View {
        AnatomyCard(variant: .elevated, action: onOpen) {
            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 12) {
                    Image(systemName: "book.closed")
                        .font(.title3).foregroundStyle(Color.osmOnPrimaryContainer)
                        .frame(width: 44, height: 44)
                        .background(Color.osmPrimaryContainer).clipShape(RoundedRectangle(cornerRadius: 12))
                    VStack(alignment: .leading, spacing: 4) {
                        AnatomyText(verbatim: opl.title, font: OSMTypography.headline, lineLimit: 2)
                        if let type = opl.typeName, !type.isEmpty { OplBadge(title: type) }
                    }
                    Spacer(minLength: 0)
                    Image(systemName: "chevron.right").foregroundStyle(Color.osmOnSurfaceVariant)
                }
                if isDownloaded { OplBadge(title: String(localized: OplStrings.downloaded), icon: "checkmark.circle") }
                if let objective = opl.objective, !objective.isEmpty {
                    AnatomyText(verbatim: objective, lineLimit: 3)
                }
                Label(locationSummary, systemImage: "mappin.and.ellipse")
                    .font(OSMTypography.caption).foregroundStyle(Color.osmOnSurfaceVariant).lineLimit(2)
                ViewThatFits(in: .horizontal) {
                    HStack(spacing: 6) { mediaCounts }
                    VStack(alignment: .leading, spacing: 6) { mediaCounts }
                }
                if opl.content.isEmpty {
                    AnatomyText(OplStrings.noContent, font: OSMTypography.caption, color: .osmOnSurfaceVariant)
                }
                if opl.creatorName != nil || opl.createdAt != nil || opl.updatedAt != nil {
                    Divider()
                    if let creator = opl.creatorName, !creator.isEmpty {
                        Label(creator, systemImage: "person").font(OSMTypography.caption)
                    }
                    if let date = opl.updatedAt ?? opl.createdAt {
                        Label(oplDisplayDate(date), systemImage: "calendar").font(OSMTypography.caption)
                    }
                }
            }
        }
    }

    private var locationSummary: String {
        var seen = Set<String>()
        let names = opl.levels.filter { seen.insert($0.id).inserted }.map(\.name)
        if names.isEmpty { return String(localized: OplStrings.noLocations) }
        let summary = names.prefix(2).joined(separator: " · ")
        return names.count > 2 ? summary + " · +\(names.count - 2)" : summary
    }

    @ViewBuilder
    private var mediaCounts: some View {
        let groups: [(String, LocalizedStringResource, Int)] = [
            ("text.alignleft", OplStrings.textCount(opl.content.filter { $0.type == .text }.count), opl.content.filter { $0.type == .text }.count),
            ("photo", OplStrings.imageCount(opl.content.filter { $0.type == .image }.count), opl.content.filter { $0.type == .image }.count),
            ("video", OplStrings.videoCount(opl.content.filter { $0.type == .video }.count), opl.content.filter { $0.type == .video }.count),
            ("doc.richtext", OplStrings.pdfCount(opl.content.filter { $0.type == .pdf }.count), opl.content.filter { $0.type == .pdf }.count),
            ("waveform", OplStrings.audioCount(opl.content.filter { $0.oplGalleryType == .audio }.count), opl.content.filter { $0.oplGalleryType == .audio }.count),
            ("doc", OplStrings.fileCount(opl.content.filter { $0.type == .unknown && $0.oplGalleryType == nil }.count), opl.content.filter { $0.type == .unknown && $0.oplGalleryType == nil }.count)
        ]
        ForEach(groups.indices, id: \.self) { index in
            let group = groups[index]
            if group.2 > 0 {
                OplBadge(title: String(localized: group.1), icon: group.0, primary: false)
            }
        }
    }
}

struct OplBadge: View {
    let title: String
    var icon: String?
    var primary = true

    var body: some View {
        HStack(spacing: 4) {
            if let icon { Image(systemName: icon) }
            AnatomyText(verbatim: title, font: .caption.weight(.semibold),
                        color: primary ? .osmOnPrimaryContainer : .osmOnSurfaceVariant)
        }
        .foregroundStyle(primary ? Color.osmOnPrimaryContainer : .osmOnSurfaceVariant)
        .padding(.horizontal, 8).padding(.vertical, 5)
        .background(primary ? Color.osmPrimaryContainer : .osmSurfaceVariant)
        .clipShape(Capsule())
    }
}

func oplDisplayDate(_ raw: String) -> String {
    // Use the resolved catalog language, not empty .lproj folders from an incremental build.
    let catalogFolder = Bundle.main.url(forResource: "Localizable", withExtension: "strings")?
        .deletingLastPathComponent().lastPathComponent
    let catalogLanguage = catalogFolder.flatMap { $0.hasSuffix(".lproj") ? String($0.dropLast(6)) : nil }
    let displayLocale = Locale(identifier: catalogLanguage
        ?? Bundle.main.developmentLocalization ?? Locale.current.identifier)
    let dateTimeStyle = Date.FormatStyle(date: .abbreviated, time: .shortened).locale(displayLocale)
    let dateStyle = Date.FormatStyle(date: .abbreviated, time: .omitted).locale(displayLocale)
    let parser = ISO8601DateFormatter()
    parser.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    if let date = parser.date(from: raw) { return date.formatted(dateTimeStyle) }
    parser.formatOptions = [.withInternetDateTime]
    if let date = parser.date(from: raw) { return date.formatted(dateTimeStyle) }
    let fallback = DateFormatter()
    fallback.locale = Locale(identifier: "en_US_POSIX")
    fallback.dateFormat = "yyyy-MM-dd"
    if let date = fallback.date(from: String(raw.prefix(10))) { return date.formatted(dateStyle) }
    return raw
}

extension OplContent {
    var oplGalleryType: CardEvidenceMediaType? {
        switch type {
        case .image: .image
        case .video: .video
        case .unknown: ["audio", "sonido"].contains(typeCode.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()) ? .audio : nil
        default: nil
        }
    }
    var oplFileName: String {
        guard let mediaUrl, let name = URL(string: mediaUrl)?.lastPathComponent.removingPercentEncoding,
              !name.isEmpty else { return "\(typeCode)-\(id)" }
        return name.components(separatedBy: "/").last ?? name
    }
}
