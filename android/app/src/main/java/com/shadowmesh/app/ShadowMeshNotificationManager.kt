package com.shadowmesh.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

class ShadowMeshNotificationManager private constructor(private val context: Context) {
    companion object {
        private const val CHANNEL_ID = "shadowmesh_vpn_channel"
        
        @Volatile
        private var instance: ShadowMeshNotificationManager? = null

        fun getInstance(context: Context): ShadowMeshNotificationManager {
            return instance ?: synchronized(this) {
                instance ?: ShadowMeshNotificationManager(context.applicationContext).also { instance = it }
            }
        }
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "ShadowMesh VPN Status"
            val descriptionText = "Shows active VPN connection status"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun getVpnNotification(isConnected: Boolean, serverName: String, latency: Int? = null): Notification {
        val contentText = if (isConnected) {
            if (latency != null) "Connected to $serverName • ${latency}ms" else "Connected to $serverName"
        } else {
            "Disconnected"
        }
        
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("ShadowMesh")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_secure)
            .setOngoing(isConnected)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
