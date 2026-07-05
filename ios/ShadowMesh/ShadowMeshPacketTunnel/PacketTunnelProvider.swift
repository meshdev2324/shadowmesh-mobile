
//
//  PacketTunnelProvider.swift
//  ShadowMeshPacketTunnel
//
//  Created on 6/29/24.
//

import NetworkExtension
import os.log
import WireGuardKit

class PacketTunnelProvider: NEPacketTunnelProvider {
    let logger = OSLog(subsystem: "com.shadowmesh.app.network-extension", category: "PacketTunnelProvider")
    
    private var adapter: WireGuardAdapter?
    private var isKillSwitchActive = false
    
    override func startTunnel(options: [String : NSObject]? = nil) async throws {
        os_log("Starting tunnel", log: logger, type: .info)
        
        guard let protocolConfig = protocolConfiguration as? NETunnelProviderProtocol else {
            os_log("Invalid protocol configuration", log: logger, type: .error)
            throw NSError(domain: "ShadowMesh", code: -1, userInfo: [NSLocalizedDescriptionKey: "Invalid configuration"])
        }
        
        guard let wgQuickConfig = protocolConfig.providerConfiguration?["wg-quick-config"] as? String else {
            os_log("Missing wireguard config", log: logger, type: .error)
            throw NSError(domain: "ShadowMesh", code: -2, userInfo: [NSLocalizedDescriptionKey: "Missing WireGuard config"])
        }
        
        adapter = WireGuardAdapter(with: self)
        
        do {
            try adapter?.start(tunnelConfiguration: TunnelConfiguration(fromWgQuickConfig: wgQuickConfig)!)
            os_log("Tunnel started successfully", log: logger, type: .info)
        } catch {
            os_log("Failed to start tunnel: %{public}@", log: logger, type: .error, String(describing: error))
            throw error
        }
    }
    
    override func stopTunnel(with reason: NEProviderStopReason) async {
        os_log("Stopping tunnel with reason %d", log: logger, type: .info, reason.rawValue)
        adapter?.stop { _ in
            self.adapter = nil
        }
    }
    
    private func enforceKillSwitch() async throws {
        if isKillSwitchActive {
            // Block all traffic by setting up a null route or dropping packets
            os_log("Kill switch activated, blocking all network traffic", log: logger, type: .error)
            
            // Create a null tunnel configuration that blocks everything
            let nullConfig = """
            [Interface]
            PrivateKey = \(WireGuardKit.PrivateKey().base64Key)
            Address = 192.168.0.2/32
            
            [Peer]
            PublicKey = \(WireGuardKit.PublicKey(base64Key: "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")!.base64Key)
            AllowedIPs = 0.0.0.0/0, ::/0
            """
            
            try await adapter?.start(tunnelConfiguration: TunnelConfiguration(fromWgQuickConfig: nullConfig)!)
        }
    }
    
    override func handleAppMessage(_ messageData: Data) async -> Data? {
        os_log("Received message from app", log: logger, type: .debug)
        
        if let message = String(data: messageData, encoding: .utf8) {
            if message == "ACTIVATE_KILL_SWITCH" {
                isKillSwitchActive = true
                try? await enforceKillSwitch()
                return "KILL_SWITCH_ACTIVE".data(using: .utf8)
            } else if message == "DEACTIVATE_KILL_SWITCH" {
                isKillSwitchActive = false
                return "KILL_SWITCH_INACTIVE".data(using: .utf8)
            }
        }
        
        guard let adapter = adapter else {
            return nil
        }
        
        return await withCheckedContinuation { continuation in
            adapter.getRuntimeConfiguration { config in
                guard let config = config else {
                    continuation.resume(returning: nil)
                    return
                }
                
                // Extract stats from config
                var rxBytes: UInt64 = 0
                var txBytes: UInt64 = 0
                var lastHandshake: Int64 = 0
                
                for peer in config.peers {
                    rxBytes += peer.rxBytes
                    txBytes += peer.txBytes
                    lastHandshake = max(lastHandshake, peer.lastHandshakeTime?.timeIntervalSince1970 ?? 0)
                }
                
                // Create data to send back (simple serialization for now)
                var result = [String: Any]()
                result["rxBytes"] = rxBytes
                result["txBytes"] = txBytes
                result["lastHandshake"] = lastHandshake
                
                do {
                    let data = try JSONSerialization.data(withJSONObject: result, options: [])
                    continuation.resume(returning: data)
                } catch {
                    os_log("Failed to serialize stats: %{public}@", log: self.logger, type: .error, String(describing: error))
                    continuation.resume(returning: nil)
                }
            }
        }
    }
    
    override func sleep() async {
        os_log("Provider going to sleep", log: logger, type: .debug)
    }
    
    override func wake() {
        os_log("Provider waking up", log: logger, type: .debug)
    }
}

