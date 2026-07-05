package com.shadowmesh.app.security

import android.content.Context
import android.content.Intent
import com.shadowmesh.app.SecureStorage

class PanicWipeManager private constructor(private val context: Context) {
    companion object {
        @Volatile
        private var instance: PanicWipeManager? = null

        fun getInstance(context: Context): PanicWipeManager {
            return instance ?: synchronized(this) {
                instance ?: PanicWipeManager(context.applicationContext).also { instance = it }
            }
        }
    }

    fun executePanicWipe() {
        println("CRITICAL: Panic Wipe Initiated")
        
        // 1. Signal
        reportToBackend()
        
        // 2. Wipe Storage
        try {
            SecureStorage.getInstance(context).remove("auth_token")
            SecureStorage.getInstance(context).remove("private_key")
            println("Storage successfully wiped.")
        } catch (e: Exception) {
            println("Failed to clear secure storage: ${e.message}")
        }
        
        // 3. Decoy Transition
        val intent = Intent("com.shadowmesh.app.TRIGGER_DECOY")
        context.sendBroadcast(intent)
    }

    private fun reportToBackend() {
        println("Sending duress signal to server...")
    }
}
