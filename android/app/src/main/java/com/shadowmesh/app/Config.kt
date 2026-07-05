package com.shadowmesh.app

object Config {
    const val DEFAULT_API_URL = "https://api.shadowmesh.org/api/v1"
    const val DEFAULT_MTU = 1420u
    const val CHINA_IRAN_MTU = 1280u
    
    val DEFAULT_DNS_SERVERS = listOf("1.1.1.1", "8.8.8.8")
    
    // Preferences Keys
    const val PREFS_NAME = "shadowmesh_secure"
    const val KEY_PIN_HASH = "pin_hash"
    const val KEY_PIN_SALT = "pin_salt"
    const val KEY_PANIC_PIN_HASH = "panic_pin_hash"
    const val KEY_PANIC_PIN_SALT = "panic_pin_salt"
    const val KEY_VPN_CONSENT = "vpn_consent_accepted"
    const val KEY_DEVICE_ID = "device_id"
    const val KEY_ACTIVATION_CODE = "activation_code"
}
