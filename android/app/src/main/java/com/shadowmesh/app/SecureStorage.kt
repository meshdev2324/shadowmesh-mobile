
package com.shadowmesh.app

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class SecureStorage private constructor(
    private val context: Context,
    private val encryptedPrefs: SharedPreferences? = null
) {
    companion object {
        private const val PREFS_NAME = "shadowmesh_secure_prefs"
        private const val MASTER_KEY_ALIAS = "shadowmesh_master_key"
        private const val HASH_ITERATIONS = 10000
        private const val HASH_KEY_LENGTH = 256
        private const val SALT_LENGTH = 32
        
        @Volatile
        private var instance: SecureStorage? = null
        
        fun getInstance(context: Context): SecureStorage {
            return instance ?: synchronized(this) {
                instance ?: SecureStorage(context.applicationContext).also { instance = it }
            }
        }
    }
    
    private val _encryptedPrefs: SharedPreferences by lazy {
        encryptedPrefs ?: run {
            val masterKey = MasterKey.Builder(context, MASTER_KEY_ALIAS)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            ) as EncryptedSharedPreferences
        }
    }
    
    fun set(key: String, value: String) {
        _encryptedPrefs.edit().putString(key, value).apply()
    }
    
    fun get(key: String): String? {
        return _encryptedPrefs.getString(key, null)
    }
    
    fun remove(key: String) {
        _encryptedPrefs.edit().remove(key).apply()
    }

    fun generateSalt(): String {
        val random = SecureRandom()
        val saltBytes = ByteArray(SALT_LENGTH)
        random.nextBytes(saltBytes)
        return saltBytes.joinToString("") { "%02x".format(it) }
    }

    fun hashPin(pin: String, salt: String): String {
        val spec = PBEKeySpec(
            pin.toCharArray(),
            salt.toByteArray(),
            HASH_ITERATIONS,
            HASH_KEY_LENGTH
        )
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        return hash.joinToString("") { "%02x".format(it) }
    }
    
    fun getPersistentDeviceId(): String {
        var deviceId = get("device_id")
        if (deviceId == null) {
            deviceId = generateRandomDeviceId()
            set("device_id", deviceId)
        }
        return deviceId
    }
    
    private fun generateRandomDeviceId(): String {
        val random = SecureRandom()
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }
}
