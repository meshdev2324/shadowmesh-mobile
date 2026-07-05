
import Security
import Foundation
import CryptoKit

class SecureStorage {
    static let shared = SecureStorage()
    
    private init() {}
    
    private let KEY_PIN_SALT = "pin_salt"
    private let KEY_PANIC_PIN_SALT = "panic_pin_salt"
    private let KEY_DEVICE_ID = "device_id"

    // MARK: - Hashing with Salt
    func hashPin(_ pin: String, salt: String) -> String {
        let combined = (pin + salt).data(using: .utf8)!
        let hash = SHA256.hash(data: combined)
        return hash.compactMap { String(format: "%02x", $0) }.joined()
    }
    
    func generateSalt() -> String {
        var bytes = [UInt8](repeating: 0, count: 32)
        _ = SecRandomCopyBytes(kSecRandomDefault, 32, &bytes)
        return bytes.map { String(format: "%02x", $0) }.joined()
    }
    
    // MARK: - Device ID
    func getPersistentDeviceId() -> String {
        if let existingId = try? getString(forKey: KEY_DEVICE_ID) {
            return existingId
        }
        
        let uuid = UUID().uuidString
        let data = (uuid + String(ProcessInfo.processInfo.globallyUniqueString)).data(using: .utf8)!
        let hash = SHA256.hash(data: data)
        let deviceId = hash.compactMap { String(format: "%02x", $0) }.joined()
        
        try? setString(deviceId, forKey: KEY_DEVICE_ID)
        return deviceId
    }

    // MARK: - String Storage
    func setString(_ value: String, forKey key: String) throws {
        guard let data = value.data(using: .utf8) else {
            throw NSError(domain: "ShadowMesh", code: -1, userInfo: [NSLocalizedDescriptionKey: "Failed to encode string"])
        }
        
        let query: [CFString: Any] = [
            kSecClass: kSecClassGenericPassword,
            kSecAttrAccount: key,
            kSecValueData: data,
            kSecAttrAccessible: kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        ]
        
        SecItemDelete(query as CFDictionary)
        
        let status = SecItemAdd(query as CFDictionary, nil)
        if status != errSecSuccess {
            throw NSError(domain: "ShadowMesh", code: Int(status), userInfo: [NSLocalizedDescriptionKey: "Failed to save to Keychain"])
        }
    }
    
    func getString(forKey key: String) throws -> String? {
        let query: [CFString: Any] = [
            kSecClass: kSecClassGenericPassword,
            kSecAttrAccount: key,
            kSecReturnData: kCFBooleanTrue!,
            kSecMatchLimit: kSecMatchLimitOne
        ]
        
        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        
        if status == errSecItemNotFound {
            return nil
        } else if status != errSecSuccess {
            throw NSError(domain: "ShadowMesh", code: Int(status), userInfo: [NSLocalizedDescriptionKey: "Failed to retrieve from Keychain"])
        }
        
        guard let data = result as? Data,
              let value = String(data: data, encoding: .utf8) else {
            return nil
        }
        
        return value
    }
    
    // MARK: - Bool Storage
    func setBool(_ value: Bool, forKey key: String) {
        try? setString(value ? "true" : "false", forKey: key)
    }
    
    func getBool(forKey key: String) -> Bool? {
        guard let stringValue = try? getString(forKey: key) else { return nil }
        return stringValue == "true"
    }
    
    // MARK: - Removal
    func removeValue(forKey key: String) throws {
        let query: [CFString: Any] = [
            kSecClass: kSecClassGenericPassword,
            kSecAttrAccount: key
        ]
        
        let status = SecItemDelete(query as CFDictionary)
        if status != errSecSuccess && status != errSecItemNotFound {
            throw NSError(domain: "ShadowMesh", code: Int(status), userInfo: [NSLocalizedDescriptionKey: "Failed to remove from Keychain"])
        }
    }
}
