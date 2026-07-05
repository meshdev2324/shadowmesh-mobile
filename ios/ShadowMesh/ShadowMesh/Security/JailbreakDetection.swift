
import Foundation

class JailbreakDetection {
    static let shared = JailbreakDetection()
    
    func isJailbroken() -> Bool {
        // Check 1: Check for jailbreak-specific paths
        let jailbreakPaths = [
            "/Applications/Cydia.app",
            "/Applications/Sileo.app",
            "/Applications/Zebra.app",
            "/private/var/lib/apt/",
            "/private/var/stash",
            "/private/var/mobile/Library/SBSettings/Themes",
            "/private/var/lib/cydia/",
            "/private/var/db/stash",
            "/private/var/mobile/Library/Preferences/ABPattern",
            "/usr/libexec/sftp-server",
            "/usr/sbin/sshd",
            "/etc/apt/",
            "/private/var/cache/apt/",
            "/private/var/lib/dpkg/",
            "/private/var/log/apt/",
            "/private/var/tmp/cydia.log",
            "/private/var/mobile/Library/SpringBoard/CoverFlowDisable"
        ]
        
        for path in jailbreakPaths {
            if FileManager.default.fileExists(atPath: path) {
                return true
            }
        }
        
        // Check 2: Try to write to a restricted directory
        let testPath = "/private/jailbreak_test.txt"
        do {
            try "test".write(toFile: testPath, atomically: true, encoding: .utf8)
            try FileManager.default.removeItem(atPath: testPath)
            return true
        } catch {
            // Expected failure
        }
        
        // Check 3: Check environment variables
        let envVars = ["DYLD_INSERT_LIBRARIES", "DYLD_LIBRARY_PATH"]
        for varName in envVars {
            if getenv(varName) != nil {
                return true
            }
        }
        
        // Check 4: Check for openable paths
        if canOpen(path: "/") {
            return true
        }
        
        // Check 5: Check for specific dylibs
        if checkForDylibs() {
            return true
        }
        
        return false
    }
    
    private func canOpen(path: String) -> Bool {
        let file = fopen(path, "r")
        if file != nil {
            fclose(file)
            return true
        }
        return false
    }
    
    private func checkForDylibs() -> Bool {
        let suspectDylibs = [
            "Substrate",
            "CydiaSubstrate",
            "MobileSubstrate",
            "Jailbreak",
            "libcycript",
            "libsubstrate",
            "substrate",
            "FridaGadget"
        ]
        
        var count: UInt32 = 0
        let images = objc_copyImageNames(&count)
        guard let imageNames = images else { return false }
        
        for i in 0..<Int(count) {
            guard let name = imageNames[i], let str = String(validatingUTF8: name) else { continue }
            for suspect in suspectDylibs {
                if str.contains(suspect) {
                    return true
                }
            }
        }
        
        free(images)
        return false
    }
    
    func getDetectionDetails() -> String {
        if !isJailbroken() {
            return "Device not jailbroken"
        }
        
        var details: [String] = []
        
        // Just a summary
        details.append("Jailbreak detected")
        
        return details.joined(separator: ", ")
    }
}
