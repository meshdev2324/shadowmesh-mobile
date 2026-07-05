import Foundation

struct Config {
    static let DEFAULT_API_URL = "https://api.shadowmesh.org/api/v1"
    static let DEFAULT_MTU: UInt32 = 1420
    static let CHINA_IRAN_MTU: UInt32 = 1280

    static let DEFAULT_DNS_SERVERS = ["1.1.1.1", "8.8.8.8"]

    // Keys for SecureStorage/UserDefaults
    static let KEY_PIN_HASH = "pin_hash"
    static let KEY_PIN_SALT = "pin_salt"
    static let KEY_PANIC_PIN_HASH = "panic_pin_hash"
    static let KEY_PANIC_PIN_SALT = "panic_pin_salt"
    static let KEY_ACTIVATION_CODE = "activation_code"
    static let KEY_DEVICE_ID = "device_id"
    static let KEY_KILL_SWITCH_ENABLED = "killSwitchEnabled"
    static let KEY_SECURITY_LOCK_ENABLED = "isSecurityLockEnabled"
}
