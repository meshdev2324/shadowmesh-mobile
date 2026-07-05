package com.shadowmesh.app

import android.content.Context
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import uniffi.shadowmesh.VpnManager
import uniffi.shadowmesh.createVpnManager
import uniffi.shadowmesh.getDefaultUserSettings
import uniffi.shadowmesh.VpnNode
import uniffi.shadowmesh.VpnConfig
import uniffi.shadowmesh.ConnectionStats
import java.io.ByteArrayInputStream
import com.wireguard.crypto.KeyPair

class WireGuardService private constructor() {
    companion object {
        val instance by lazy { WireGuardService() }
    }

    private var backend: Backend? = null
    var vpnManager: VpnManager? = null
        private set
    private var tunnel: WgTunnel? = null
    private var currentTunnelName: String? = null
    private var connectedSince: Long = 0L
    private var totalBytesReceived: ULong = 0uL
    private var totalBytesSent: ULong = 0uL
    private var lastHandshake: Long = 0L

    fun initialize(ctx: Context) {
        if (backend == null) {
            backend = GoBackend(ctx)
        }
        if (vpnManager == null) {
            try {
                vpnManager = createVpnManager(getDefaultUserSettings())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun generateKeyPair(): Pair<String, String> {
        val keyPair = KeyPair()
        return Pair(keyPair.privateKey.toBase64(), keyPair.publicKey.toBase64())
    }

    class WgTunnel(private val tunnelName: String) : Tunnel {
        override fun getName() = tunnelName
        override fun onStateChange(newState: Tunnel.State) {}
    }

    suspend fun connect(node: VpnNode, clientConfig: VpnConfig) {
        val backend = this.backend ?: return
        
        try {
            vpnManager?.initiateConnection(node, clientConfig.publicKey)
            
            tunnel = WgTunnel("ShadowMesh")
            currentTunnelName = "ShadowMesh-${node.region}"

            val splitTunnelConfig = vpnManager?.getSplitTunnelConfig()
            var appRouting = ""
            if (splitTunnelConfig != null && splitTunnelConfig.enabled && splitTunnelConfig.appList.isNotEmpty()) {
                val appListStr = splitTunnelConfig.appList.joinToString(",")
                if (splitTunnelConfig.mode.lowercase() == "include") {
                    appRouting = "IncludedApplications = $appListStr"
                } else {
                    appRouting = "ExcludedApplications = $appListStr"
                }
            }

            // Generate config string
            val configString = """
            [Interface]
            PrivateKey = ${clientConfig.privateKey}
            Address = ${clientConfig.address}
            DNS = ${clientConfig.dns.joinToString(",")}
            MTU = ${clientConfig.mtu}
            $appRouting

            [Peer]
            PublicKey = ${node.publicKey}
            AllowedIPs = 0.0.0.0/0
            Endpoint = ${node.endpoint}
            PersistentKeepalive = 25
            """.trimIndent()

            val config = Config.parse(ByteArrayInputStream(configString.toByteArray(Charsets.UTF_8)))
            
            backend.setState(tunnel!!, Tunnel.State.UP, config)
            
            connectedSince = System.currentTimeMillis() / 1000L
            vpnManager?.completeConnection()
        } catch (e: Exception) {
            e.printStackTrace()
            vpnManager?.disconnect()
            throw e
        }
    }

    suspend fun disconnect() {
        val backend = this.backend
        val tunnel = this.tunnel
        
        if (backend != null && tunnel != null) {
            try {
                backend.setState(tunnel, Tunnel.State.DOWN, null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        vpnManager?.disconnect()
        this.tunnel = null
        currentTunnelName = null
        connectedSince = 0L
        totalBytesReceived = 0uL
        totalBytesSent = 0uL
        lastHandshake = 0L
    }

    fun getStats(): ConnectionStats {
        val backend = this.backend
        val tunnel = this.tunnel
        if (backend != null && tunnel != null) {
            try {
                val runtimeConfig = backend.getRuntimeConfiguration(tunnel)
                var rxBytes: ULong = 0uL
                var txBytes: ULong = 0uL
                for (peer in runtimeConfig.peers) {
                    rxBytes += peer.rxBytes.toULong()
                    txBytes += peer.txBytes.toULong()
                    if (peer.lastHandshakeTime != null) {
                        lastHandshake = peer.lastHandshakeTime!!.epochSecond
                    }
                }
                totalBytesReceived = rxBytes
                totalBytesSent = txBytes
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        return ConnectionStats(
            totalBytesReceived,
            totalBytesSent,
            totalBytesReceived / 1500uL,
            totalBytesSent / 1500uL,
            lastHandshake,
            connectedSince
        )
    }

    fun isConnected(): Boolean {
        return tunnel != null
    }

    fun getCurrentTunnel(): String? {
        return currentTunnelName
    }
    
    suspend fun activateKillSwitch() {
        val backend = this.backend
        val tunnel = this.tunnel
        if (backend != null && tunnel != null) {
            try {
                val nullConfig = """
                    [Interface]
                    PrivateKey = ${KeyPair().privateKey.toBase64()}
                    Address = 192.168.0.2/32
                    
                    [Peer]
                    PublicKey = AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=
                    AllowedIPs = 0.0.0.0/0, ::/0
                """.trimIndent()
                
                val config = Config.parse(ByteArrayInputStream(nullConfig.toByteArray(Charsets.UTF_8)))
                backend.setState(tunnel, Tunnel.State.UP, config)
                println("Kill switch activated on Android")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    suspend fun deactivateKillSwitch() {
        // Just note that we'll reconnect with normal config when needed
        println("Kill switch deactivated on Android")
    }
}
