import SwiftUI

struct ContentView: View {
    var body: some View {
        LoginView()
    }
}

#Preview("App · Light") {
    OneSmartMateTheme {
        ContentView()
    }
    .preferredColorScheme(.light)
}

#Preview("App · Dark") {
    OneSmartMateTheme {
        ContentView()
    }
    .preferredColorScheme(.dark)
}
