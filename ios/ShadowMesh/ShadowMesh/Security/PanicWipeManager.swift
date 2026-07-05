import Foundation
import uniffi_shadowmesh

class PanicWipeManager {
    static let shared = PanicWipeManager()
    
    private init() {}
    
    func executePanicWipe() {
        print("CRITICAL: Panic Wipe Initiated")
        
        // 1. Kill Connection immediately
        WireGuardService.shared.vpnManager?.setKillSwitchEnabled(enabled: true)
        Task {
            await WireGuardService.shared.disconnect()
        }
        
        // 2. Wipe Sensitive Data from Keychain
        let keysToWipe = ["auth_token", "private_key", "pin_hash", "isSecurityLockEnabled", "killSwitchEnabled"]
        for key in keysToWipe {
            try? SecureStorage.shared.removeValue(forKey: key)
        }
        
        // 3. Clear application-level cache if necessary (UserDefaults, etc)
        if let bundleID = Bundle.main.bundleIdentifier {
            UserDefaults.standard.removePersistentDomain(forName: bundleID)
        }

        print("Panic wipe completed. All sensitive local data purged.")

        // 4. Decoy Transition
        DispatchQueue.main.async {
            NotificationCenter.default.post(name: NSNotification.Name("TriggerDecoyMode"), object: nil)
        }
    }
}
