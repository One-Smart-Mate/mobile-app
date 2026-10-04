import Foundation

// Use the same Spanish/English catalog as AppStrings; business rules stay in sharedLogic.
enum OplStrings {
    static let title: LocalizedStringResource = "opl.title"
    static let subtitle: LocalizedStringResource = "opl.subtitle"
    static let searchPlaceholder: LocalizedStringResource = "opl.searchPlaceholder"
    static let search: LocalizedStringResource = "opl.search"
    static let exclusiveFilters: LocalizedStringResource = "opl.exclusiveFilters"
    static let clearLevel: LocalizedStringResource = "opl.clearLevel"
    static let selectCurrent: LocalizedStringResource = "opl.selectCurrent"
    static let searching: LocalizedStringResource = "opl.searching"
    static let loadingLevels: LocalizedStringResource = "opl.loadingLevels"
    static let levelsUnavailable: LocalizedStringResource = "opl.levelsUnavailable"
    static let queryTooLong: LocalizedStringResource = "opl.queryTooLong"
    static let searchFailed: LocalizedStringResource = "opl.searchFailed"
    static let accessDenied: LocalizedStringResource = "opl.accessDenied"
    static let noConnection: LocalizedStringResource = "opl.noConnection"
    static let notFound: LocalizedStringResource = "opl.notFound"
    static let loadFailed: LocalizedStringResource = "opl.loadFailed"
    static let downloaded: LocalizedStringResource = "opl.downloaded"
    static let downloadedList: LocalizedStringResource = "opl.downloadedList"
    static let noResults: LocalizedStringResource = "opl.noResults"
    static let noResultsHelp: LocalizedStringResource = "opl.noResultsHelp"
    static let noContent: LocalizedStringResource = "opl.noContent"
    static let noLocations: LocalizedStringResource = "opl.noLocations"
    static let text: LocalizedStringResource = "opl.text"
    static let files: LocalizedStringResource = "opl.files"
    static let detailTitle: LocalizedStringResource = "opl.detailTitle"
    static let download: LocalizedStringResource = "opl.download"
    static let replace: LocalizedStringResource = "opl.replace"
    static let delete: LocalizedStringResource = "opl.delete"
    static let downloadTitle: LocalizedStringResource = "opl.downloadTitle"
    static let replaceTitle: LocalizedStringResource = "opl.replaceTitle"
    static let deleteTitle: LocalizedStringResource = "opl.deleteTitle"
    static let downloadBody: LocalizedStringResource = "opl.downloadBody"
    static let replaceBody: LocalizedStringResource = "opl.replaceBody"
    static let deleteBody: LocalizedStringResource = "opl.deleteBody"
    static let deleted: LocalizedStringResource = "opl.deleted"
    static let deleteFailed: LocalizedStringResource = "opl.deleteFailed"
    static let downloadComplete: LocalizedStringResource = "opl.downloadComplete"
    static let downloadFailed: LocalizedStringResource = "opl.downloadFailed"
    static let preparing: LocalizedStringResource = "opl.preparing"
    static let saving: LocalizedStringResource = "opl.saving"
    static let progress: LocalizedStringResource = "opl.progress"
    static let deleting: LocalizedStringResource = "opl.deleting"
    static let objective: LocalizedStringResource = "opl.objective"
    static let noObjective: LocalizedStringResource = "opl.noObjective"
    static let locations: LocalizedStringResource = "opl.locations"
    static let content: LocalizedStringResource = "opl.content"
    static let documentInfo: LocalizedStringResource = "opl.documentInfo"
    static let identifier: LocalizedStringResource = "opl.identifier"
    static let creator: LocalizedStringResource = "opl.creator"
    static let reviewer: LocalizedStringResource = "opl.reviewer"
    static let created: LocalizedStringResource = "opl.created"
    static let updated: LocalizedStringResource = "opl.updated"
    static let ciltUsage: LocalizedStringResource = "opl.ciltUsage"
    static let directUsage: LocalizedStringResource = "opl.directUsage"
    static let lastUsed: LocalizedStringResource = "opl.lastUsed"
    static let emptyText: LocalizedStringResource = "opl.emptyText"
    static let mediaWait: LocalizedStringResource = "opl.mediaWait"
    static let openPDF: LocalizedStringResource = "opl.openPDF"
    static let unsupported: LocalizedStringResource = "opl.unsupported"
    static let notAssigned: LocalizedStringResource = "opl.notAssigned"
    static let pdfFailed: LocalizedStringResource = "opl.pdfFailed"

    static func resultCount(_ count: Int) -> LocalizedStringResource {
        LocalizedStringResource("opl.resultCount", defaultValue: "\(count) resultados")
    }

    static func textCount(_ count: Int) -> LocalizedStringResource {
        LocalizedStringResource("opl.textCount", defaultValue: "\(count) textos")
    }

    static func imageCount(_ count: Int) -> LocalizedStringResource {
        LocalizedStringResource("opl.imageCount", defaultValue: "\(count) imágenes")
    }

    static func videoCount(_ count: Int) -> LocalizedStringResource {
        LocalizedStringResource("opl.videoCount", defaultValue: "\(count) videos")
    }

    static func audioCount(_ count: Int) -> LocalizedStringResource {
        LocalizedStringResource("opl.audioCount", defaultValue: "\(count) audios")
    }

    static func pdfCount(_ count: Int) -> LocalizedStringResource {
        LocalizedStringResource("opl.pdfCount", defaultValue: "\(count) PDF")
    }

    static func fileCount(_ count: Int) -> LocalizedStringResource {
        LocalizedStringResource("opl.fileCount", defaultValue: "\(count) archivos")
    }
}
