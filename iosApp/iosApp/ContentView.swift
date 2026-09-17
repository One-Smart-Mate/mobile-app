import SwiftUI

struct ContentView: View {
    let dependencies: AppDependencies

    var body: some View {
        OneSmartMateRoot(dependencies: dependencies)
    }
}

#Preview("App · Light") {
    OneSmartMateTheme {
        ContentView(dependencies: .preview)
    }
    .preferredColorScheme(.light)
}

#Preview("App · Dark") {
    OneSmartMateTheme {
        ContentView(dependencies: .preview)
    }
    .preferredColorScheme(.dark)
}
