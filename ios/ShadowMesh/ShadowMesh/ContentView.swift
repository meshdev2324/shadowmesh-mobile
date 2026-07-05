import SwiftUI

struct ContentView: View {
    @StateObject private var viewModel = VPNManagerViewModel()
    @Environment(\.scenePhase) var scenePhase
    @State private var isUnlocked = false
    @State private var isCamouflageMode = false
    @State private var showBlur = false
    
    let pubDecoyTrigger = NotificationCenter.default.publisher(for: NSNotification.Name("TriggerDecoyMode"))
    let pubDecoyExit = NotificationCenter.default.publisher(for: NSNotification.Name("ExitDecoyMode"))

    var body: some View {
        ZStack {
            if isCamouflageMode {
                NotesDecoyView()
            } else if viewModel.showActivationScreen {
                ActivationView(viewModel: viewModel)
            } else {
                TabView {
                    StatusView(viewModel: viewModel)
                        .tabItem {
                            Label("Home", systemImage: "house.fill")
                        }
                    SettingsView(viewModel: viewModel)
                        .tabItem {
                            Label("Settings", systemImage: "gearshape.fill")
                        }
                }
            }
            
            if !isUnlocked && !isCamouflageMode {
                SecurityLockView(isUnlocked: $isUnlocked)
                    .transition(.move(edge: .bottom))
            }
            
            if showBlur {
                VisualEffectBlur(blurStyle: .systemUltraThinMaterialDark)
                    .edgesIgnoringSafeArea(.all)
            }
        }
        .onReceive(pubDecoyTrigger) { _ in
            isCamouflageMode = true
            isUnlocked = false
        }
        .onReceive(pubDecoyExit) { _ in
            isCamouflageMode = false
            isUnlocked = false
        }
        .onChange(of: scenePhase) { newPhase in
            switch newPhase {
            case .background, .inactive:
                showBlur = true
            case .active:
                showBlur = false
                // Require unlock on active
                if !isCamouflageMode {
                    isUnlocked = false
                }
            @unknown default:
                break
            }
        }
    }
}

struct VisualEffectBlur: UIViewRepresentable {
    var blurStyle: UIBlurEffect.Style
    
    func makeUIView(context: Context) -> UIVisualEffectView {
        return UIVisualEffectView(effect: UIBlurEffect(style: blurStyle))
    }
    
    func updateUIView(_ uiView: UIVisualEffectView, context: Context) {}
}

#Preview {
    ContentView()
}
