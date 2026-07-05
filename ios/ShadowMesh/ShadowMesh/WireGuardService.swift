//
//  WireGuardService.swift
//  ShadowMesh
//
//  Created on 6/29/24.
//

import Foundation
import NetworkExtension

class WireGuardService {
    static let shared = WireGuardService()
    
    private var isInitialized = false
    private var currentTunnel: String?
    private var tunnelManager: NETunnelProviderManager?
    private var connectedSince: Int64 = 0
    private var lastHandshake: Int64 = 0
    private var totalBytesReceived: UInt64 = 0
    private var totalBytesSent: UInt64 = 0
    
    // Rust Core Logic
    var vpnManager: VpnManager?
    
    private init() {
        do {
            let settings = try getDefaultUserSettings()
            self.vpnManager = try createVpnManager(settings: settings)
        } catch {
            print("Failed to initialize Rust VPN Manager: \(error)")
        }
    }
    
    func initialize() async -> Bool {
        isInitialized = true
        do {
            let managers = try await NETunnelProviderManager.loadAllFromPreferences()
            if let existingManager = managers.first {
                tunnelManager = existingManager
                currentTunnel = existingManager.localizedDescription
            }
        } catch {
            print("Error loading tunnel manager: \(error)")
        }
        return isInitialized
    }

    func generateKeyPair() -> (privateKey: String, publicKey: String) {
        do {
            let keys = try generateWireguardKeys()
            return (privateKey: keys[0], publicKey: keys[1])
        } catch {
            print("Failed to generate keys: \(error)")
            return (privateKey: "", publicKey: "")
        }
    }
    
    func connect(node: VpnNode, clientConfig: VpnConfig) async throws {
        if !isInitialized {
            _ = await initialize()
        }
        
        // Let Rust know we are initiating connection
        try? self.vpnManager?.initiateConnection(node: node, devicePublicKey: clientConfig.publicKey)
        
        // Generate wireguard config string manually for now (could be moved to Rust)
        let configString = """
        [Interface]
        PrivateKey = \(clientConfig.privateKey)
        Address = \(clientConfig.address)
        DNS = \(clientConfig.dns.joined(separator: ","))
        MTU = \(clientConfig.mtu)

        [Peer]
        PublicKey = \(node.publicKey)
        AllowedIPs = 0.0.0.0/0
        Endpoint = \(node.endpoint)
        PersistentKeepalive = 25
        """
        
        let providerConfiguration = NETunnelProviderProtocol()
        providerConfiguration.providerBundleIdentifier = "com.shadowmesh.app.network-extension"
        providerConfiguration.serverAddress = node.endpoint
        providerConfiguration.providerConfiguration = [
            "wg-quick-config": configString
        ]
        
        if tunnelManager == nil {
            tunnelManager = NETunnelProviderManager()
        }
        
        tunnelManager?.protocolConfiguration = providerConfiguration
        tunnelManager?.localizedDescription = "ShadowMesh - \(node.name)"
        tunnelManager?.isEnabled = true
        
        do {
            try await tunnelManager?.saveToPreferences()
            try await tunnelManager?.loadFromPreferences()
            try await (tunnelManager?.connection as! NETunnelProviderSession).startVPNTunnel(options: [:])
            
            currentTunnel = "ShadowMesh - \(node.name)"
            connectedSince = Int64(Date().timeIntervalSince1970)
            self.vpnManager?.completeConnection()
        } catch {
            print("Error connecting: \(error)")
            // self.vpnManager?.disconnect() 
            throw error
        }
    }
    
    func disconnect() async {
        do {
            self.vpnManager?.disconnect()
            try await tunnelManager?.loadFromPreferences()
            (tunnelManager?.connection as! NETunnelProviderSession).stopVPNTunnel()
            
            currentTunnel = nil
            connectedSince = 0
            totalBytesReceived = 0
            totalBytesSent = 0
        } catch {
            print("Error disconnecting: \(error)")
        }
    }
    
    func getStats() -> ConnectionStats {
        if currentTunnel == nil {
            return ConnectionStats(
                bytesReceived: 0,
                bytesSent: 0,
                packetsReceived: 0,
                packetsSent: 0,
                lastHandshake: 0,
                connectedSince: 0
            )
        }
        
        do {
            // Try to get real stats from PacketTunnelProvider
            let session = tunnelManager?.connection as? NETunnelProviderSession
            let data = try session?.sendProviderMessage(Data("get-stats".utf8)) { $0 }
            
            if let data = data {
                if let json = try JSONSerialization.jsonObject(with: data, options: []) as? [String: Any] {
                    totalBytesReceived = json["rxBytes"] as? UInt64 ?? totalBytesReceived
                    totalBytesSent = json["txBytes"] as? UInt64 ?? totalBytesSent
                    lastHandshake = json["lastHandshake"] as? Int64 ?? lastHandshake
                }
            }
        } catch {
            print("Failed to get real stats: \(error)")
        }
        
        return ConnectionStats(
            bytesReceived: totalBytesReceived,
            bytesSent: totalBytesSent,
            packetsReceived: totalBytesReceived / 1500,
            packetsSent: totalBytesSent / 1500,
            lastHandshake: lastHandshake,
            connectedSince: connectedSince
        )
    }
    
    func isConnected() -> Bool {
        return currentTunnel != nil
    }
    
    func getCurrentTunnel() -> String? {
        return currentTunnel
    }
    
    func activateKillSwitch() async throws {
        try await tunnelManager?.loadFromPreferences()
        tunnelManager?.isOnDemandEnabled = true
        tunnelManager?.isEnabled = true
        if let proto = tunnelManager?.protocolConfiguration as? NETunnelProviderProtocol {
            proto.includeAllNetworks = true
            proto.excludeLocalNetworks = true
            tunnelManager?.protocolConfiguration = proto
        }
        try await tunnelManager?.saveToPreferences()
        
        let session = tunnelManager?.connection as? NETunnelProviderSession
        _ = try await session?.sendProviderMessage(Data("ACTIVATE_KILL_SWITCH".utf8)) { $0 }
    }
    
    func deactivateKillSwitch() async throws {
        try await tunnelManager?.loadFromPreferences()
        tunnelManager?.isOnDemandEnabled = false
        if let proto = tunnelManager?.protocolConfiguration as? NETunnelProviderProtocol {
            proto.includeAllNetworks = false
            proto.excludeLocalNetworks = false
            tunnelManager?.protocolConfiguration = proto
        }
        try await tunnelManager?.saveToPreferences()
        
        let session = tunnelManager?.connection as? NETunnelProviderSession
        _ = try await session?.sendProviderMessage(Data("DEACTIVATE_KILL_SWITCH".utf8)) { $0 }
    }
}
