import SwiftUI

struct OplListDestination: View {
    let site: SessionSite
    let makeViewModel: @MainActor (SessionSite) -> OplViewModel
    let onOpen: (Int64) -> Void
    @State private var viewModel: OplViewModel?

    var body: some View {
        Group {
            if let viewModel { OplScreen(viewModel: viewModel, onOpen: onOpen) }
            else { ProgressView().tint(.osmPrimary) }
        }
        .task { if viewModel == nil { viewModel = makeViewModel(site) } }
    }
}

struct OplDetailDestination: View {
    let site: SessionSite
    let oplID: Int64
    let makeViewModel: @MainActor (SessionSite, Int64) -> OplDetailViewModel
    @State private var viewModel: OplDetailViewModel?

    var body: some View {
        Group {
            if let viewModel { OplDetailScreen(viewModel: viewModel) }
            else { ProgressView().tint(.osmPrimary) }
        }
        .task { if viewModel == nil { viewModel = makeViewModel(site, oplID) } }
    }
}
