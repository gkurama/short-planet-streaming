import SwiftUI
import GoogleSignIn

@main
struct ShortPlanetStreamApp: App {
    var body: some Scene {
        WindowGroup {
            AppRootView()
                .preferredColorScheme(.dark)
                .onOpenURL { url in
                    GIDSignIn.sharedInstance.handle(url)
                }
        }
    }
}
