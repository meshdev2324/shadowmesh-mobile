import SwiftUI

@main
struct ShadowMeshApp: App {
    init() {
        performJailbreakCheck()
    }
    
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
    
    func performJailbreakCheck() {
        let jailbreakDetection = JailbreakDetection.shared
        if jailbreakDetection.isJailbroken() {
            print("⚠️ Jailbreak detected! Showing security warning.")
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                if let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
                   let rootViewController = windowScene.windows.first?.rootViewController {
                    let alert = UIAlertController(
                        title: "Security Warning",
                        message: "Your device appears to be jailbroken, which may compromise security. Some features will be disabled.",
                        preferredStyle: .alert
                    )
                    alert.addAction(UIAlertAction(title: "OK", style: .default, handler: nil))
                    rootViewController.present(alert, animated: true, completion: nil)
                }
            }
        }
    }
}
